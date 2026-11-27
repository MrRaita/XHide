package dev.hfw.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IInterface
import android.os.RemoteCallbackList
import android.os.UserHandle
import android.os.UserManager
import dev.hfw.state.HideScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One small daemon thread for overlay builds and delayed notifications. Stopped and restarted around hot reloads. */
internal object Work {
    private var exec: ScheduledThreadPoolExecutor? = null

    @Synchronized
    private fun get(): ScheduledThreadPoolExecutor {
        val e = exec
        if (e != null && !e.isShutdown) return e
        return ScheduledThreadPoolExecutor(1) { r -> Thread(r, "hfw-work").apply { isDaemon = true } }
            .also { it.executeExistingDelayedTasksAfterShutdownPolicy = false; exec = it }
    }

    fun execute(task: () -> Unit) {
        try {
            get().execute { guard(task) }
        } catch (t: Throwable) {
            HealthLog.event("work rejected", t)
        }
    }

    fun schedule(delayMs: Long, task: () -> Unit) {
        try {
            get().schedule({ guard(task) }, delayMs, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            HealthLog.event("scheduled work rejected", t)
        }
    }

    private fun guard(task: () -> Unit) {
        try {
            task()
        } catch (t: Throwable) {
            HealthLog.event("background work failed", t)
        }
    }

    @Synchronized
    fun stop() {
        runCatching { exec?.shutdownNow() }
        exec = null
    }
}

/**
 * Turns state changes into the signals launchers react to (all through LauncherAppsImpl.mListeners, system_server side):
 *  - hide:  onPackageRemoved + onPackagesUnavailable (+ empty shortcut list) so OEM launchers purge icons from their DBs
 *  - show:  onPackageAdded + onPackagesAvailable (no onPackageChanged)
 *  - name/icon of a visible package: onPackageChanged + ACTION_PACKAGE_CHANGED broadcast
 *  - name/icon of a hidden package: nothing
 * A hide/show transition always wins over a simultaneous name change (one op, one signal type).
 */
internal object Notifier {
    private const val RECONCILIATION_DELAY_MS = 2_500L
    private const val RECONCILIATION_COOLDOWN_NANOS = 15_000_000_000L
    private const val UNCONFIRMED_TTL_MS = 10 * 60 * 1000L

    private val lastPulseNanos = ConcurrentHashMap<Int, Long>()

    /** name/icon changes a launcher has not asked about yet: package -> uptime ms of the announcement. */
    private val unconfirmed = ConcurrentHashMap<String, Long>()

    fun reset() {
        lastPulseNanos.clear()
        unconfirmed.clear()
    }

    // ---- dispatch helpers --------------------------------------------------------------------------------------

    /** Runs [task] on LauncherAppsImpl's own callback handler (serialized with the framework's broadcasts). */
    private fun post(impl: Any, delayMs: Long, task: () -> Unit) {
        val handler = runCatching { Runtime.be().getObjectField(impl, "mCallbackHandler") as? Handler }.getOrNull()
        val run = Runnable {
            if (Runtime.acceptingReload) return@Runnable
            try {
                task()
            } catch (t: Throwable) {
                HealthLog.event("notification task failed", t)
            }
        }
        when {
            handler != null && delayMs <= 0 -> handler.post(run)
            handler != null -> handler.postDelayed(run, delayMs)
            delayMs <= 0 -> Work.execute { run.run() }
            else -> Work.schedule(delayMs) { run.run() }
        }
    }

    fun userHandles(impl: Any, fallback: UserHandle?): List<UserHandle> {
        val be = Runtime.be()
        val handles = LinkedHashSet<UserHandle>()
        if (fallback != null) handles.add(fallback)
        val token = Binder.clearCallingIdentity()
        try {
            try {
                val umInternal = be.getObjectField(impl, "mUserManagerInternal")
                val ids = if (umInternal != null) be.callMethod(umInternal, "getUserIds") as? IntArray else null
                if (ids != null) for (id in ids) (be.callStaticMethod(UserHandle::class.java, "of", id) as? UserHandle)?.let { handles.add(it) }
            } catch (_: Throwable) {
            }
            try {
                val um = be.getObjectField(impl, "mUm") as? UserManager
                if (um != null) {
                    (be.callMethod(um, "getUsers") as? List<*>)?.forEach { u ->
                        if (u == null) return@forEach
                        val id = be.getIntField(u, "id")
                        (be.callStaticMethod(UserHandle::class.java, "of", id) as? UserHandle)?.let { handles.add(it) }
                    }
                    um.userProfiles?.let { handles.addAll(it) }
                }
            } catch (_: Throwable) {
            }
        } finally {
            Binder.restoreCallingIdentity(token)
        }
        return handles.toList()
    }

    /** Best effort: is [pkg] installed for [userId]? Unknown counts as "no", the requester's own user is handled by the caller. */
    fun installedIn(ctx: Context?, pkg: String, userId: Int): Boolean {
        if (ctx == null) return false
        val token = Binder.clearCallingIdentity()
        return try {
            Runtime.be().callMethod(ctx.packageManager, "getApplicationInfoAsUser", pkg, 0, userId) != null
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    // ---- public signals ----------------------------------------------------------------------------------------

    /** Hide/show signals for [pkgs] in [profile]; direction is decided at send time from the current state. Sent twice for reliability. */
    fun hideShow(impl: Any, profile: UserHandle, pkgs: List<String>) {
        if (pkgs.isEmpty()) return
        post(impl, 0) { deliverHideShow(impl, profile, pkgs) }
        post(impl, RECONCILIATION_DELAY_MS) { deliverHideShow(impl, profile, pkgs) }
    }

    /** onPackageChanged (+ ACTION_PACKAGE_CHANGED) for visible packages whose name or icon changed. */
    fun changed(impl: Any, profile: UserHandle, pkgs: List<String>, intentBroadcast: Boolean = true) {
        if (pkgs.isEmpty()) return
        val now = android.os.SystemClock.uptimeMillis()
        pkgs.forEach { unconfirmed[it] = now }
        post(impl, 0) { deliverChanged(impl, profile, pkgs) }
        if (intentBroadcast) Work.execute { packageChangedIntent(pkgs, profile) }
    }

    /** A launcher asked about [pkg] itself: it has seen the change. */
    fun confirm(pkg: String) {
        unconfirmed.remove(pkg)
    }

    /**
     * A non-exempt caller asked for the full launcher list. After a short delay (so the launcher has finished loading)
     * re-announce hidden packages (reconciliation pulse) and name/icon changes nobody asked about yet.
     */
    fun pulse(impl: Any?, profile: UserHandle?, userId: Int) {
        if (impl == null || profile == null || Runtime.acceptingReload) return
        val now = System.nanoTime()
        val last = lastPulseNanos[userId] ?: 0L
        if (now - last < RECONCILIATION_COOLDOWN_NANOS) return
        lastPulseNanos[userId] = now
        post(impl, RECONCILIATION_DELAY_MS) {
            val snap = StateStore.snapshot
            val ctx = Runtime.contextOf(impl)
            val be = Runtime.be()
            val hideSupported = android.os.Build.VERSION.SDK_INT >= 30
            if (hideSupported) {
                val hidden = snap.hiddenFor(userId, ModuleCache.getDetectedModulePackages()).filter { pkg ->
                    snap.shouldHideScope(pkg, userId, ModuleCache.isXposedModule(pkg, null, ctx, profile, be), HideScope.LAUNCHER)
                }
                if (hidden.isNotEmpty()) {
                    HealthLog.event("reconciliation pulse for user $userId (${hidden.size} hidden)")
                    deliverHideShow(impl, profile, hidden)
                }
            }
            val cutoff = android.os.SystemClock.uptimeMillis() - UNCONFIRMED_TTL_MS
            unconfirmed.entries.removeIf { it.value < cutoff }
            val pending = unconfirmed.keys.filter { pkg ->
                (snap.apps[pkg] != null || snap.labelOf(pkg, userId) != null || snap.icons[userId]?.get(pkg) != null) &&
                    (!hideSupported || !snap.shouldHideScope(pkg, userId, ModuleCache.isXposedModule(pkg, null, ctx, profile, be), HideScope.LAUNCHER))
            }
            if (pending.isNotEmpty()) {
                HealthLog.event("re-announcing ${pending.size} unconfirmed name/icon change(s) for user $userId")
                deliverChanged(impl, profile, pending)
            }
        }
    }

    // ---- delivery ----------------------------------------------------------------------------------------------

    private fun listenersOf(impl: Any): RemoteCallbackList<*>? =
        runCatching { Runtime.be().getObjectField(impl, "mListeners") as? RemoteCallbackList<*> }.getOrNull()

    private inline fun forEachListener(impl: Any, profile: UserHandle, callbackName: String, action: (IInterface) -> Unit) {
        val listeners = listenersOf(impl) ?: run {
            HealthLog.event("LauncherAppsImpl.mListeners not found")
            return
        }
        // The framework iterates mListeners under synchronized(mListeners); beginBroadcast() twice at once throws.
        synchronized(listeners) {
            val count = listeners.beginBroadcast()
            try {
                for (i in 0 until count) {
                    val listener = listeners.getBroadcastItem(i) as? IInterface ?: continue
                    val cookie = runCatching { listeners.getBroadcastCookie(i) }.getOrNull()
                    if (cookie != null) {
                        val ok = runCatching { Runtime.be().callMethod(impl, "isEnabledProfileOf", profile, cookie, callbackName) as? Boolean }.getOrNull()
                        if (ok == false) continue
                    }
                    action(listener)
                }
            } finally {
                listeners.finishBroadcast()
            }
        }
    }

    private fun deliverHideShow(impl: Any, profile: UserHandle, pkgs: List<String>) {
        val be = Runtime.be()
        val ctx = Runtime.contextOf(impl)
        val userId = userIdOf(profile, be)
        val snap = StateStore.snapshot
        val (hidden, shown) = pkgs.distinct().partition { pkg ->
            snap.shouldHideScope(pkg, userId, ModuleCache.isXposedModule(pkg, null, ctx, profile, be), HideScope.LAUNCHER)
        }
        val emptySlice = if (hidden.isNotEmpty()) runCatching {
            be.callStaticMethod(Class.forName("android.content.pm.ParceledListSlice"), "emptyList")
        }.getOrNull() else null
        forEachListener(impl, profile, "onPackageChanged") { listener ->
            if (hidden.isNotEmpty()) {
                for (pkg in hidden) {
                    runCatching { be.callMethod(listener, "onPackageRemoved", profile, pkg) }
                        .onFailure { HealthLog.event("onPackageRemoved($pkg) failed: ${it.message}") }
                    if (emptySlice != null) runCatching { be.callMethod(listener, "onShortcutChanged", profile, pkg, emptySlice) }
                }
                runCatching { be.callMethod(listener, "onPackagesUnavailable", profile, hidden.toTypedArray(), false) }
                    .onFailure { HealthLog.event("onPackagesUnavailable failed: ${it.message}") }
            }
            if (shown.isNotEmpty()) {
                for (pkg in shown) {
                    runCatching { be.callMethod(listener, "onPackageAdded", profile, pkg) }
                        .onFailure { HealthLog.event("onPackageAdded($pkg) failed: ${it.message}") }
                }
                runCatching { be.callMethod(listener, "onPackagesAvailable", profile, shown.toTypedArray(), false) }
                    .onFailure { HealthLog.event("onPackagesAvailable failed: ${it.message}") }
            }
        }
    }

    private fun deliverChanged(impl: Any, profile: UserHandle, pkgs: List<String>) {
        val be = Runtime.be()
        var delivered = 0
        forEachListener(impl, profile, "onPackageChanged") { listener ->
            for (pkg in pkgs) {
                runCatching { be.callMethod(listener, "onPackageChanged", profile, pkg) }
                    .onSuccess { delivered++ }
                    .onFailure { HealthLog.event("onPackageChanged($pkg) failed: ${it.message}") }
            }
        }
        HealthLog.event("onPackageChanged: ${pkgs.size} package(s), $delivered call(s)")
    }

    /** What PackageManagerService sends after a component/app change; Settings and many apps reload their lists on it. */
    private fun packageChangedIntent(pkgs: List<String>, profile: UserHandle) {
        val ctx = Runtime.contextOf(null) ?: return
        val be = Runtime.be()
        val userId = userIdOf(profile, be)
        val pm = ctx.packageManager
        var sent = 0
        for (pkg in pkgs) {
            val ai = runCatching { be.callMethod(pm, "getApplicationInfoAsUser", pkg, 0, userId) as? ApplicationInfo }.getOrNull()
                ?: if (userId == 0) runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() else null
            val uid = ai?.uid ?: continue
            val intent = Intent(Intent.ACTION_PACKAGE_CHANGED, Uri.fromParts("package", pkg, null))
                .putExtra(Intent.EXTRA_UID, uid)
                .putExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST, arrayOf(pkg))
                .putExtra(Intent.EXTRA_DONT_KILL_APP, true)
                .addFlags(0x01000000) // FLAG_RECEIVER_INCLUDE_BACKGROUND (hidden constant)
            runCatching { be.callMethod(ctx, "sendBroadcastAsUser", intent, profile) }
                .onSuccess { sent++ }
                .onFailure { HealthLog.event("ACTION_PACKAGE_CHANGED for $pkg failed: ${it.message}") }
        }
        HealthLog.event("ACTION_PACKAGE_CHANGED broadcast for $sent package(s)")
    }
}
