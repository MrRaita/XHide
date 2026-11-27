package dev.hfw.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.UserHandle
import dev.hfw.Identity
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The single package-event source in system_server. One receiver (all users) feeds:
 *  - the caller cache (companion changed / any launcher candidate changed),
 *  - uninstall cleanup (state record, icon file, overlay).
 * Events are handled on their own thread, never on system_server's main thread (cleanup writes and fsyncs).
 */
internal object PackageEvents {
    @Volatile
    var allUsers = false
        private set

    private val scheduled = AtomicBoolean(false)
    private var thread: HandlerThread? = null
    private var receiver: BroadcastReceiver? = null
    private var registeredOn: Context? = null

    fun ensureRegisteredAsync(context: Context) {
        if (Runtime.isReloadPending() || allUsers) return
        if (!scheduled.compareAndSet(false, true)) return
        try {
            Thread({
                val token = Binder.clearCallingIdentity()
                try {
                    register(context)
                } catch (t: Throwable) {
                    HealthLog.event("package event registration failed", t)
                } finally {
                    Binder.restoreCallingIdentity(token)
                    if (!allUsers) scheduled.set(false)
                }
            }, "hfw-events-init").apply { isDaemon = true }.start()
        } catch (t: Throwable) {
            scheduled.set(false)
            HealthLog.event("could not start package event registration", t)
        }
    }

    @Synchronized
    private fun register(context: Context) {
        if (allUsers || Runtime.isReloadPending()) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val ht = HandlerThread("hfw-events").also { it.isDaemon = true; it.start() }
        val handler = Handler(ht.looper)
        val rcv = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                try {
                    handle(intent ?: return)
                } catch (t: Throwable) {
                    HealthLog.event("package event failed", t)
                }
            }
        }
        val be = Runtime.be()
        try {
            val all = be.getStaticObjectField(UserHandle::class.java, "ALL") as UserHandle
            be.callMethod(context, "registerReceiverAsUser", rcv, all, filter, null, handler)
            allUsers = true
        } catch (t: Throwable) {
            // A user-scoped receiver still invalidates, but is not enough to cache caller roles.
            HealthLog.event("registerReceiverAsUser failed; roles will not be cached", t)
            try {
                context.registerReceiver(rcv, filter, null, handler)
            } catch (t2: Throwable) {
                ht.quitSafely()
                HealthLog.event("package events unavailable", t2)
                return
            }
        }
        thread = ht
        receiver = rcv
        registeredOn = context
        HealthLog.event("package events registered (allUsers=$allUsers)")
    }

    private fun handle(intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == Identity.PACKAGE) {
            Callers.invalidate()
            HealthLog.event("companion package changed (${intent.action})")
        } else if (intent.action != Intent.ACTION_PACKAGE_CHANGED ||
            intent.getStringArrayExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST)?.contains(pkg) == true
        ) {
            Callers.invalidateHome()
        }
        if (intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            cleanup(pkg, intent)
        } else if (pkg != Identity.PACKAGE && (intent.action == Intent.ACTION_PACKAGE_ADDED || intent.action == Intent.ACTION_PACKAGE_REPLACED)) {
            val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
            val userId = if (uid >= 0) uid / 100_000 else 0
            val snap = StateStore.snapshot
            if ((snap.icons[userId]?.get(pkg)?.iconRev ?: 0L) > 0L || (snap.apps[pkg]?.iconRev ?: 0L) > 0L) {
                Coordinator.reapplyOverlay(pkg, userId)
            }
        }
    }

    /** Data was removed with the package: forget its rule, its icon file and its overlay. */
    private fun cleanup(pkg: String, intent: Intent) {
        if (pkg == Identity.PACKAGE) return
        val forAll = intent.getBooleanExtra("android.intent.extra.REMOVED_FOR_ALL_USERS", false)
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        val userId = if (uid >= 0) uid / 100000 else 0
        val upd = StateStore.forget(pkg, if (forAll) null else userId) ?: return
        if (forAll) {
            runCatching { File(File(StateStore.dir, "icons"), "$pkg.png").delete() }
            if (upd.overlayRemove.isNotEmpty()) Coordinator.removeOverlays(upd.overlayRemove)
            for ((uid, p) in upd.overlayRemoveUsers) Coordinator.removeOverlays(setOf(p), uid)
        } else if (upd.overlayRemoveUsers.isNotEmpty()) {
            Coordinator.removeOverlays(setOf(pkg), userId)
        }
        HealthLog.event("cleaned up $pkg (allUsers=$forAll, user=$userId)")
    }

    @Synchronized
    fun stop() {
        val ctx = registeredOn
        val rcv = receiver
        if (ctx != null && rcv != null) runCatching { ctx.unregisterReceiver(rcv) }
        thread?.quitSafely()
        thread = null
        receiver = null
        registeredOn = null
        allUsers = false
        scheduled.set(false)
    }
}
