package dev.fadalias.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Process
import android.os.SystemClock
import dev.fadalias.BuildConfig
import dev.fadalias.Identity
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal enum class Role { SELF, SYSTEM, LAUNCHER, OTHER }

/**
 * Who is calling? Resolved once per uid and cached; the cache is dropped by [PackageEvents] whenever a package changes,
 * which is what makes caching safe (a reassigned app id can never inherit another app's role).
 *
 * SELF is verified by package ownership + signature (pinned when a cert is configured).
 */
internal object Callers {
    private const val PER_USER_RANGE = 100000

    private val roles = ConcurrentHashMap<Int, Role>()

    @Volatile
    private var cachedSelfAppId = -1

    private val selfLock = Any()
    private var selfGeneration = 0L

    /**
     * LauncherAppsService clears the calling identity while it generates ActivityInfos, so the name hooks would see
     * system_server (uid 1000) instead of the manager and rewrite the manager's names too. getLauncherActivities marks
     * the thread while the verified manager is the real caller. Expires on its own in case `after` never runs.
     */
    private val selfScopeUntil = ThreadLocal<Long>()

    /** True while [isSelf] verifies the manager: the name hooks must not ask "is this the manager?" again (endless recursion). */
    private val verifying = ThreadLocal<Boolean>()

    fun isVerifying(): Boolean = verifying.get() == true

    fun enterSelfScope() = selfScopeUntil.set(SystemClock.elapsedRealtime() + 3_000L)

    fun leaveSelfScope() = selfScopeUntil.remove()

    fun inSelfScope(): Boolean {
        val until = selfScopeUntil.get() ?: return false
        return SystemClock.elapsedRealtime() <= until
    }

    fun roleOf(uid: Int, impl: Any?): Role {
        if (uid == Process.SYSTEM_UID || uid == 0) return Role.SYSTEM
        roles[uid]?.let { return it }
        val role = when {
            isSelf(uid, impl) -> Role.SELF
            isLauncher(uid) -> Role.LAUNCHER
            else -> Role.OTHER
        }
        // Only cache while package events are watched, otherwise nothing would ever invalidate the entry.
        if (PackageEvents.allUsers && !Runtime.isReloadPending()) roles[uid] = role
        return role
    }

    fun isExempt(uid: Int, impl: Any?): Boolean {
        val r = roleOf(uid, impl)
        return r == Role.SYSTEM || r == Role.SELF
    }

    fun isSelf(uid: Int, impl: Any?): Boolean {
        if (Runtime.isReloadPending()) return false
        val appId = uid % PER_USER_RANGE
        if (appId < Process.FIRST_APPLICATION_UID) return false
        val cached = cachedSelfAppId
        if (cached != -1) return appId == cached
        val context = Runtime.contextOf(impl) ?: return false
        if (verifying.get() == true) return false
        verifying.set(true)
        val token = Binder.clearCallingIdentity()
        return try {
            val watcherReady = PackageEvents.allUsers
            val generation = synchronized(selfLock) { selfGeneration }
            val pm = context.packageManager
            val owns = pm.getPackagesForUid(uid)?.contains(Identity.PACKAGE) == true
            if (!owns) return false
            if (!signatureOk(pm)) return false
            // Registration runs off the Binder thread; until it completes every call goes through full verification.
            if (!watcherReady) PackageEvents.ensureRegisteredAsync(context)
            if (watcherReady && !Runtime.isReloadPending()) {
                synchronized(selfLock) {
                    if (selfGeneration == generation && !Runtime.acceptingReload && PackageEvents.allUsers) cachedSelfAppId = appId
                }
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restoreCallingIdentity(token)
            verifying.remove()
        }
    }

    /** The companion package changed (installed, replaced, removed): forget every cached verdict. */
    fun invalidate() {
        synchronized(selfLock) {
            selfGeneration++
            cachedSelfAppId = -1
        }
        roles.clear()
        homeStale = true
    }

    fun invalidateHome() {
        roles.entries.removeIf { it.value == Role.LAUNCHER || it.value == Role.OTHER }
        homeStale = true
    }

    private fun signatureOk(pm: PackageManager): Boolean {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(Identity.PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(Identity.PACKAGE, PackageManager.GET_SIGNATURES)
            } ?: return false
            val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            if (signers.isNullOrEmpty()) return false
            val pinned = BuildConfig.PINNED_CERT_SHA256
            if (pinned.isNotEmpty()) {
                val md = MessageDigest.getInstance("SHA-256")
                return signers.any { sig ->
                    val digest = md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
                    digest.equals(pinned, ignoreCase = true)
                }
            }
            true
        } catch (t: Throwable) {
            HealthLog.event("caller signature check failed", t)
            false
        }
    }

    // ---- launcher detection (apps that declare the HOME category) ---------------------------------------------

    @Volatile
    private var homeAppIds: Set<Int> = emptySet()

    @Volatile
    private var homeAt = 0L

    @Volatile
    private var homeStale = true

    private val inHomeLookup = ThreadLocal<Boolean>()

    fun isLauncher(uid: Int): Boolean {
        val appId = uid % PER_USER_RANGE
        if (appId < Process.FIRST_APPLICATION_UID) return false
        val age = SystemClock.uptimeMillis() - homeAt
        // Stale after a package event; but never more often than once a second. Without events: every 10 s.
        if (homeAt == 0L || (homeStale && age > 1_000) || age > 10_000 || (appId !in homeAppIds && age > 1_000 && !PackageEvents.allUsers)) refreshHome()
        return appId in homeAppIds
    }

    private fun refreshHome() {
        if (inHomeLookup.get() == true) return // our own query must not recurse into the name hooks
        inHomeLookup.set(true)
        val token = Binder.clearCallingIdentity() // ask as system_server, not as the (visibility-limited) caller
        try {
            val ctx = Runtime.contextOf(null) ?: return
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val flags = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
            homeAppIds = ctx.packageManager.queryIntentActivities(home, flags)
                .mapNotNull { it.activityInfo?.applicationInfo?.uid?.let { u -> u % PER_USER_RANGE } }.toSet()
            homeAt = SystemClock.uptimeMillis()
            homeStale = false
        } catch (t: Throwable) {
            homeAt = SystemClock.uptimeMillis() // do not retry in a tight loop
            HealthLog.event("resolving launcher uids failed", t)
        } finally {
            Binder.restoreCallingIdentity(token)
            inHomeLookup.remove()
        }
    }
}
