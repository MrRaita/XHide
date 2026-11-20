package org.lsposed.hidexposed.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IInterface
import android.os.Process
import android.os.RemoteCallbackList
import android.os.UserHandle
import android.os.UserManager
import org.lsposed.hidexposed.BuildConfig
import org.lsposed.hidexposed.data.ConfigStore
import java.security.MessageDigest
import java.util.ArrayList

/**
 * System-level hook targeting `LauncherAppsService$LauncherAppsImpl` and `UserManagerService`.
 *
 * 100% verified against AOSP Android 11 (SDK 30) through Android 17 (SDK 37+) and HMA architecture:
 * 1. `UserManagerService#getProfileIds(int, boolean)` (Android 11–17) &
 *    `LauncherAppsImpl#getUserProfiles()` (Android 15–17) — Ensures our companion UI receives
 *    all active user profiles on the device (`User 0`, `User 10` Clone/Work Profile, MIUI `999`
 *    Dual Apps, Samsung `150` Secure Folder, Private Space, Secondary Users).
 * 2. `canAccessProfile(int, String)` (Android 11–17), `canAccessProfile(int, int, int, int, String)`
 *    (Android 15–17), `ensureInUserProfiles(int, String)` (Android 11–12),
 *    `canAccessHiddenProfile(int, int)` (Android 15–17 Private Space), and
 *    `injectHasInteractAcrossUsersFullPermission(int, int)` (Android 11–17) — Grants our companion
 *    UI permission to query launcher activities across all profiles without SecurityExceptions.
 * 3. `getLauncherActivities(String, String, UserHandle)` (Android 11–17),
 *    `resolveLauncherActivityInternal(String, ComponentName, UserHandle)` (Android 12–17),
 *    `resolveActivity(String, ComponentName, UserHandle)` (Android 11), and
 *    `isActivityEnabled(String, ComponentName, UserHandle)` (Android 11–17) — Filters hidden
 *    Xposed modules and per-user blacklisted apps from all launchers while keeping them 100%
 *    accessible to Xposed/Root managers and system internals.
 */
object LauncherHook {
    private const val TAG = "XHide"
    private const val TARGET_CLASS = "com.android.server.pm.LauncherAppsService\$LauncherAppsImpl"
    private const val USER_MANAGER_SERVICE_CLASS = "com.android.server.pm.UserManagerService"
    private const val EXTRA_ALL_USERS_QUERY = "hidexposed_all_users_query"
    private const val EXTRA_CONFIG_QUERY = "hidexposed_config_query"
    private const val PER_USER_RANGE = 100000
    // Not a valid package name, so a launcher-activity query for it always returns an empty list.
    private const val IPC_REJECTED_PACKAGE = "__hidexposed_ipc_rejected__"

    @Volatile
    private var cachedSelfAppId: Int = -1

    private lateinit var backend: HookBackend
    private var watcherContext: Context? = null
    private var watcherReceiver: android.content.BroadcastReceiver? = null
    private var watcherThread: Thread? = null

    internal fun install(classLoader: ClassLoader, hookBackend: HookBackend) {
        synchronized(lifecycleLock) {
            backend = hookBackend
            acceptingReload = false
            stopWatcherLocked()
        }
        selfWatcherRegistered = false
        selfWatcherAllUsers = false
        selfWatcherScheduled.set(false)
        cachedSelfAppId = -1
        try {
            val clazz = backend.findClassIfExists(TARGET_CLASS, classLoader)
            if (clazz == null) {
                backend.log("[$TAG] Target class not found: $TARGET_CLASS")
                return
            }

            runCatching { hookGetUserProfiles(clazz) }
                .onFailure { backend.log("[$TAG] hookGetUserProfiles failed: ${it.message}") }
            runCatching { hookUserManagerGetProfileIds(classLoader) }
                .onFailure { backend.log("[$TAG] hookUserManagerGetProfileIds failed: ${it.message}") }
            runCatching { hookProfileAccessChecks(clazz) }
                .onFailure { backend.log("[$TAG] hookProfileAccessChecks failed: ${it.message}") }
            runCatching { hookGetLauncherActivities(clazz) }
                .onFailure { backend.log("[$TAG] hookGetLauncherActivities failed: ${it.message}") }
            runCatching { hookGetShortcuts(clazz) }
                .onFailure { backend.log("[$TAG] hookGetShortcuts failed: ${it.message}") }
            runCatching { hookResolveAndEnabledChecks(clazz) }
                .onFailure { backend.log("[$TAG] hookResolveAndEnabledChecks failed: ${it.message}") }

            backend.log("[$TAG] Successfully installed system hooks on $TARGET_CLASS (Android 11–17)")
        } catch (t: Throwable) {
            backend.log("[$TAG] Failed to install LauncherHook: ${t.message}")
        }
    }

    @Volatile
    private var acceptingReload = false
    private val lifecycleLock = Any()

    fun prepareForHotReload(): Boolean {
        synchronized(lifecycleLock) {
            acceptingReload = true
            cachedSelfAppId = -1
            stopWatcherLocked()
            val prepared = ModuleCache.prepareForHotReload()
            if (!prepared) resumeLocked()
            return prepared
        }
    }

    fun resumeAfterHotReload() {
        synchronized(lifecycleLock) { resumeLocked() }
    }

    private fun resumeLocked() {
        acceptingReload = false
        ModuleCache.resumeAfterHotReload()
        if (watcherContext != null && watcherReceiver != null) {
            selfWatcherRegistered = true
            scheduleSelfPackageWatcher(watcherContext!!)
        }
    }

    private fun stopWatcherLocked() {
        val context = watcherContext
        val receiver = watcherReceiver
        val thread = watcherThread
        if (context != null && receiver != null) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
                // Already unregistered: this is the desired shutdown state.
            }
        }
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(2_000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        if (thread?.isAlive == true) {
            backend.log("[$TAG] Watcher thread did not stop before hot reload")
        }
        watcherThread = null
        watcherContext = null
        watcherReceiver = null
        selfWatcherRegistered = false
        selfWatcherAllUsers = false
        selfWatcherScheduled.set(false)
    }
    // Incremented (under selfCacheLock) on every companion package lifecycle event. An in-flight
    // isCallerSelf lookup only publishes its appId if no event happened since it started, so a
    // removal racing with verification cannot re-publish a stale appId.
    private val selfCacheLock = Any()
    private var selfCacheGeneration = 0L

    /**
     * Hooks `ILauncherApps#getUserProfiles()` (used by `LauncherApps.getProfiles()` on Android 15–17)
     * so `XHide` receives every active UserHandle on the device.
     */
    private fun hookGetUserProfiles(clazz: Class<*>) {
        try {
            backend.findAndHookMethod(
                clazz,
                "getUserProfiles",
                callback = object : HookCallback() {
                    override fun after(param: HookParam) {
                        try {
                            if (!isCallerSelf(Binder.getCallingUid(), param.thisObject)) return
                            val existing = (param.result as? List<*>)
                                ?.filterIsInstance<UserHandle>()
                                ?: emptyList()
                            val allHandles = collectAllUserHandles(param.thisObject ?: return, null)
                            val merged = LinkedHashSet<UserHandle>()
                            merged.addAll(existing)
                            merged.addAll(allHandles)
                            param.setResult(ArrayList(merged))
                        } catch (t: Throwable) {
                            backend.log("[$TAG] Error in getUserProfiles hook: ${t.message}")
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            backend.log("[$TAG] Could not hook getUserProfiles: ${t.message}")
        }
    }

    /**
     * Hooks `UserManagerService#getProfileIds(int, boolean)` (used by `LauncherApps.getProfiles()`
     * and `UserManager.getUserProfiles()` on Android 11–17) so `XHide` receives all user IDs
     * including Clone (10), Work, Dual Apps (999), Secure Folder (150), and Secondary Users.
     */
    private fun hookUserManagerGetProfileIds(classLoader: ClassLoader) {
        try {
            val umsClass = backend.findClassIfExists(USER_MANAGER_SERVICE_CLASS, classLoader) ?: return
            backend.findAndHookMethod(
                umsClass,
                "getProfileIds",
                Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                callback = object : HookCallback() {
                    override fun after(param: HookParam) {
                        try {
                            if (!isCallerSelf(Binder.getCallingUid(), param.thisObject)) return
                            val baseIds = param.result as? IntArray ?: IntArray(0)
                            val allUserIds = try {
                                backend.callMethod(param.thisObject!!, "getUserIds") as? IntArray
                            } catch (_: Throwable) {
                                null
                            } ?: return

                            val merged = LinkedHashSet<Int>()
                            for (id in baseIds) merged.add(id)
                            for (id in allUserIds) merged.add(id)
                            param.setResult(merged.toIntArray())
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            backend.log("[$TAG] Could not hook UserManagerService.getProfileIds: ${t.message}")
        }
    }

    /**
     * Hooks profile access checks across Android 11–17:
     * - `canAccessProfile(int, String)` (Android 11–17)
     * - `canAccessProfile(int, int, int, int, String)` (Android 15–17)
     * - `ensureInUserProfiles(int, String)` (Android 11–12)
     * - `canAccessHiddenProfile(int, int)` (Android 15–17 Private Space / Clone profiles)
     * - `injectHasInteractAcrossUsersFullPermission(int, int)` (Android 11–17 universal)
     */
    private fun hookProfileAccessChecks(clazz: Class<*>) {
        // Android 11–17: boolean canAccessProfile(int targetUserId, String message)
        try {
            backend.findAndHookMethod(
                clazz,
                "canAccessProfile",
                Int::class.javaPrimitiveType!!,
                String::class.java,
                callback = object : HookCallback() {
                    override fun before(param: HookParam) {
                        try {
                            if (isCallerSelf(Binder.getCallingUid(), param.thisObject)) {
                                param.setResult(true)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }

        // Android 15–17: boolean canAccessProfile(int callingUid, int callingUserId, int callingPid, int targetUserId, String message)
        try {
            backend.findAndHookMethod(
                clazz,
                "canAccessProfile",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                String::class.java,
                callback = object : HookCallback() {
                    override fun before(param: HookParam) {
                        try {
                            val binderUid = Binder.getCallingUid()
                            val argUid = param.args[0] as? Int ?: binderUid
                            if (argUid == binderUid && isCallerSelf(binderUid, param.thisObject)) {
                                param.setResult(true)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }

        // Android 11–12: void ensureInUserProfiles(int targetUserId, String message)
        try {
            backend.findAndHookMethod(
                clazz,
                "ensureInUserProfiles",
                Int::class.javaPrimitiveType!!,
                String::class.java,
                callback = object : HookCallback() {
                    override fun before(param: HookParam) {
                        try {
                            if (isCallerSelf(Binder.getCallingUid(), param.thisObject)) {
                                param.setResult(null)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }

        // Android 15–17: boolean canAccessHiddenProfile(int callingUid, int callingPid)
        try {
            backend.findAndHookMethod(
                clazz,
                "canAccessHiddenProfile",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                callback = object : HookCallback() {
                    override fun before(param: HookParam) {
                        try {
                            val binderUid = Binder.getCallingUid()
                            val argUid = param.args[0] as? Int ?: binderUid
                            if (argUid == binderUid && isCallerSelf(binderUid, param.thisObject)) {
                                param.setResult(true)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }

        // Android 11–17: boolean injectHasInteractAcrossUsersFullPermission(int pid, int uid)
        try {
            backend.findAndHookMethod(
                clazz,
                "injectHasInteractAcrossUsersFullPermission",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                callback = object : HookCallback() {
                    override fun before(param: HookParam) {
                        try {
                            val binderUid = Binder.getCallingUid()
                            val argUid = param.args[1] as? Int ?: binderUid
                            if (argUid == binderUid && isCallerSelf(binderUid, param.thisObject)) {
                                param.setResult(true)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }
    }

    private fun hookGetLauncherActivities(clazz: Class<*>) {
        backend.findAndHookMethod(
            clazz,
            "getLauncherActivities",
            String::class.java,
            String::class.java,
            UserHandle::class.java,
            callback = object : HookCallback() {
                override fun before(param: HookParam) {
                    try {
                        val callingPackage = param.args[0] as? String ?: return
                        val targetPackage = param.args[1] as? String ?: return
                        val userHandle = param.args[2] as? UserHandle

                        // Handle zero-lock IPC from our companion UI application
                        if (targetPackage.startsWith(ConfigStore.IPC_PREFIX)) {
                            val callingUid = Binder.getCallingUid()
                            // Strictly reject IPC commands from unverified callers
                            if (callingPackage != ConfigStore.SELF_PACKAGE ||
                                !isCallerSelf(callingUid, param.thisObject)
                            ) {
                                param.setResult(emptyList<Any>())
                                return
                            }

                            when {
                                targetPackage == ConfigStore.IPC_PING -> {
                                    param.args[1] = ConfigStore.SELF_PACKAGE
                                }
                                targetPackage == ConfigStore.IPC_GET_CONFIG -> {
                                    // Answer with XHide's own activity; the config is attached afterwards.
                                    param.setObjectExtra(EXTRA_CONFIG_QUERY, true)
                                    param.args[1] = ConfigStore.SELF_PACKAGE
                                }
                                targetPackage == ConfigStore.IPC_GET_ALL_USERS -> {
                                    param.setObjectExtra(EXTRA_ALL_USERS_QUERY, true)
                                    param.args[1] = null
                                }
                                targetPackage.startsWith(ConfigStore.IPC_UPDATE_PREFIX) -> {
                                    val payload = targetPackage.removePrefix(ConfigStore.IPC_UPDATE_PREFIX)
                                    val changedPackages = ConfigStore.applySystemIpcUpdate(payload)
                                    if (changedPackages == null) {
                                        // Rejected or not persisted: query a package that cannot exist so
                                        // the UI receives an empty list, reports failure and reverts.
                                        param.args[1] = IPC_REJECTED_PACKAGE
                                        backend.log("[$TAG] IPC config update rejected (invalid payload or persistence failure)")
                                    } else {
                                        notifyLaunchersOfChanges(param.thisObject, changedPackages, userHandle)
                                        param.args[1] = ConfigStore.SELF_PACKAGE
                                    }
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        backend.log("[$TAG] Error in beforeHookedMethod: ${t.javaClass.simpleName}: ${t.message}", t)
                    }
                }

                override fun after(param: HookParam) {
                    try {
                        val targetPackage = param.args[1] as? String
                        val userHandle = param.args[2] as? UserHandle
                        val userId = getUserId(userHandle)
                        val callingUid = Binder.getCallingUid()

                        // Companion asked for the persisted config: attach it to copies of the reply's
                        // ActivityInfo/ApplicationInfo so no cached PackageManager object is mutated.
                        if (param.getObjectExtra(EXTRA_CONFIG_QUERY) == true) {
                            if (!isCallerSelf(callingUid, param.thisObject)) return
                            val slice = param.result ?: return
                            val item = extractListFromResult(slice)?.firstOrNull() ?: return
                            val ai = extractActivityInfo(item) ?: return
                            val appCopy = ApplicationInfo(ai.applicationInfo ?: return)
                            appCopy.metaData = android.os.Bundle().apply {
                                putString(ConfigStore.IPC_CONFIG_META_KEY, ConfigStore.systemConfigJson())
                            }
                            val aiCopy = ActivityInfo(ai)
                            aiCopy.applicationInfo = appCopy
                            if (item is ResolveInfo) {
                                item.activityInfo = aiCopy
                            } else {
                                backend.setObjectField(item, "mActivityInfo", aiCopy)
                            }
                            return
                        }

                        // If our companion UI requested all users' launcher activities in one call
                        if (param.getObjectExtra(EXTRA_ALL_USERS_QUERY) == true &&
                            isCallerSelf(callingUid, param.thisObject)
                        ) {
                            val slice = param.result ?: return
                            val baseList = extractListFromResult(slice) ?: emptyList<Any>()
                            val combined = ArrayList<Any>(baseList.filterNotNull())
                            val profiles = collectAllUserHandles(param.thisObject ?: return, userHandle)

                            val expectedClass = baseList.firstOrNull()?.javaClass
                            for (profile in profiles) {
                                if (getUserId(profile) == userId) continue
                                val extraItems = queryLauncherActivitiesForProfile(param.thisObject ?: return, profile)
                                for (item in extraItems) {
                                    if (item != null && (expectedClass == null || expectedClass.isInstance(item))) {
                                        combined.add(item)
                                    }
                                }
                            }

                            param.setResult(rebuildResultSlice(slice, combined))
                            return
                        }

                        // Never filter queries coming from core system or our own UI
                        if (isExemptCaller(callingUid, param.thisObject)) {
                            return
                        }

                        val slice = param.result ?: return
                        val rawList = extractListFromResult(slice) ?: return
                        if (rawList.isEmpty()) return

                        val context = getContextFromImpl(param.thisObject)

                        val filteredList = ArrayList<Any>(rawList.size)
                        var modified = false

                        for (item in rawList) {
                            if (item == null) continue
                            val activityInfo = extractActivityInfo(item)
                            val pkgName = activityInfo?.packageName ?: targetPackage

                            if (!pkgName.isNullOrEmpty()) {
                                val isModule = ModuleCache.isXposedModule(
                                    packageName = pkgName,
                                    appInfo = activityInfo?.applicationInfo,
                                    context = context,
                                    userHandle = userHandle,
                                    backend = backend
                                )
                                if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                    modified = true
                                    continue
                                }
                            }
                            filteredList.add(item)
                        }

                        if (modified) {
                            param.setResult(rebuildResultSlice(slice, filteredList))
                        }
                    } catch (t: Throwable) {
                        backend.log("[$TAG] Fail-safe catch in afterHookedMethod: ${t.message}")
                    }
                }
            }
        )
    }

    /**
     * Hooks direct component resolution and activity-enabled checks used by launchers when
     * refreshing workspace shortcuts or handling `onPackageChanged`:
     * - `resolveLauncherActivityInternal(String, ComponentName, UserHandle)` (Android 12–17)
     * - `resolveActivity(String, ComponentName, UserHandle)` (Android 11)
     * - `isActivityEnabled(String, ComponentName, UserHandle)` (Android 11–17)
     */
    private fun hookResolveAndEnabledChecks(clazz: Class<*>) {
        val resolveCallback = object : HookCallback() {
            override fun after(param: HookParam) {
                try {
                    if (param.result == null) return
                    val callingUid = Binder.getCallingUid()
                    if (isExemptCaller(callingUid, param.thisObject)) return

                    val component = param.args[1] as? ComponentName
                    val userHandle = param.args[2] as? UserHandle
                    val pkgName = component?.packageName ?: return
                    val userId = getUserId(userHandle)
                    val context = getContextFromImpl(param.thisObject)
                    val result = param.result ?: return
                    val activityInfo = extractActivityInfo(result)

                    val isModule = ModuleCache.isXposedModule(
                        packageName = pkgName,
                        appInfo = activityInfo?.applicationInfo,
                        context = context,
                        userHandle = userHandle,
                        backend = backend
                    )
                    if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                        param.setResult(null)
                    }
                } catch (_: Throwable) {
                }
            }
        }

        // Android 12–17: resolveLauncherActivityInternal(String, ComponentName, UserHandle)
        try {
            backend.findAndHookMethod(
                clazz,
                "resolveLauncherActivityInternal",
                String::class.java,
                ComponentName::class.java,
                UserHandle::class.java,
                callback = resolveCallback
            )
        } catch (_: Throwable) {
        }

        // Android 11: resolveActivity(String, ComponentName, UserHandle)
        try {
            backend.findAndHookMethod(
                clazz,
                "resolveActivity",
                String::class.java,
                ComponentName::class.java,
                UserHandle::class.java,
                callback = resolveCallback
            )
        } catch (_: Throwable) {
        }

        // Android 11–17: isPackageEnabled(String, String, UserHandle)
        try {
            backend.findAndHookMethod(
                clazz,
                "isPackageEnabled",
                String::class.java,
                String::class.java,
                UserHandle::class.java,
                callback = object : HookCallback() {
                    override fun after(param: HookParam) {
                        try {
                            if (param.result != true) return
                            val callingUid = Binder.getCallingUid()
                            if (isExemptCaller(callingUid, param.thisObject)) return

                            val pkgName = param.args[1] as? String ?: return
                            val userHandle = param.args[2] as? UserHandle
                            val userId = getUserId(userHandle)
                            val context = getContextFromImpl(param.thisObject)

                            val isModule = ModuleCache.isXposedModule(
                                packageName = pkgName,
                                appInfo = null,
                                context = context,
                                userHandle = userHandle,
                        backend = backend
                            )
                            if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                param.setResult(false)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }

        // Android 11–17: isActivityEnabled(String, ComponentName, UserHandle)
        try {
            backend.findAndHookMethod(
                clazz,
                "isActivityEnabled",
                String::class.java,
                ComponentName::class.java,
                UserHandle::class.java,
                callback = object : HookCallback() {
                    override fun after(param: HookParam) {
                        try {
                            if (param.result != true) return
                            val callingUid = Binder.getCallingUid()
                            if (isExemptCaller(callingUid, param.thisObject)) return

                            val component = param.args[1] as? ComponentName ?: return
                            val userHandle = param.args[2] as? UserHandle
                            val pkgName = component.packageName
                            val userId = getUserId(userHandle)
                            val context = getContextFromImpl(param.thisObject)

                            val isModule = ModuleCache.isXposedModule(
                                packageName = pkgName,
                                appInfo = null,
                                context = context,
                                userHandle = userHandle,
                        backend = backend
                            )
                            if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                param.setResult(false)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }

        // Android 11–17: getApplicationInfo(String, String, int/long, UserHandle)
        val appInfoCallback = object : HookCallback() {
            override fun after(param: HookParam) {
                try {
                    if (param.result == null) return
                    val callingUid = Binder.getCallingUid()
                    if (isExemptCaller(callingUid, param.thisObject)) return

                    val pkgName = param.args[1] as? String ?: return
                    val userHandle = param.args[3] as? UserHandle
                    val userId = getUserId(userHandle)
                    val context = getContextFromImpl(param.thisObject)
                    val appInfo = param.result as? ApplicationInfo

                    val isModule = ModuleCache.isXposedModule(
                        packageName = pkgName,
                        appInfo = appInfo,
                        context = context,
                        userHandle = userHandle,
                        backend = backend
                    )
                    if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                        param.setResult(null)
                    }
                } catch (_: Throwable) {
                }
            }
        }
        try {
            backend.findAndHookMethod(
                clazz,
                "getApplicationInfo",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
                UserHandle::class.java,
                callback = appInfoCallback
            )
        } catch (_: Throwable) {
        }
        try {
            backend.findAndHookMethod(
                clazz,
                "getApplicationInfo",
                String::class.java,
                String::class.java,
                Long::class.javaPrimitiveType!!,
                UserHandle::class.java,
                callback = appInfoCallback
            )
        } catch (_: Throwable) {
        }

        // Android 12–17: shouldHideFromSuggestions(String, UserHandle)
        try {
            backend.findAndHookMethod(
                clazz,
                "shouldHideFromSuggestions",
                String::class.java,
                UserHandle::class.java,
                callback = object : HookCallback() {
                    override fun after(param: HookParam) {
                        try {
                            if (param.result == true) return
                            val pkgName = param.args[0] as? String ?: return
                            if (pkgName == ConfigStore.SELF_PACKAGE) return
                            val userHandle = param.args[1] as? UserHandle
                            val userId = getUserId(userHandle)
                            val context = getContextFromImpl(param.thisObject)

                            val isModule = ModuleCache.isXposedModule(
                                packageName = pkgName,
                                appInfo = null,
                                context = context,
                                userHandle = userHandle,
                        backend = backend
                            )
                            if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                param.setResult(true)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }

        // Android 11–17: getActivityLaunchIntent(String, ComponentName, UserHandle)
        try {
            backend.findAndHookMethod(
                clazz,
                "getActivityLaunchIntent",
                String::class.java,
                ComponentName::class.java,
                UserHandle::class.java,
                callback = object : HookCallback() {
                    override fun after(param: HookParam) {
                        try {
                            if (param.result == null) return
                            val callingUid = Binder.getCallingUid()
                            if (isExemptCaller(callingUid, param.thisObject)) return

                            val component = param.args[1] as? ComponentName ?: return
                            val pkgName = component.packageName
                            val userHandle = param.args[2] as? UserHandle
                            val userId = getUserId(userHandle)
                            val context = getContextFromImpl(param.thisObject)

                            val isModule = ModuleCache.isXposedModule(
                                packageName = pkgName,
                                appInfo = null,
                                context = context,
                                userHandle = userHandle,
                        backend = backend
                            )
                            if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                param.setResult(null)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }
    }

    private fun hookGetShortcuts(clazz: Class<*>) {
        val callback = object : HookCallback() {
            override fun after(param: HookParam) {
                try {
                    val slice = param.result ?: return
                    val callingUid = Binder.getCallingUid()
                    if (isExemptCaller(callingUid, param.thisObject)) return

                    val rawList = extractListFromResult(slice) ?: return
                    if (rawList.isEmpty()) return

                    val userHandle = param.args.lastOrNull() as? UserHandle
                    val userId = getUserId(userHandle)
                    val context = getContextFromImpl(param.thisObject)

                    val filteredList = ArrayList<Any>(rawList.size)
                    var modified = false

                    for (item in rawList) {
                        if (item == null) continue
                        val pkgName = try {
                            backend.callMethod(item, "getPackage") as? String
                        } catch (_: Throwable) {
                            null
                        }

                        if (!pkgName.isNullOrEmpty()) {
                            val isModule = ModuleCache.isXposedModule(
                                packageName = pkgName,
                                appInfo = null,
                                context = context,
                                userHandle = userHandle,
                        backend = backend
                            )
                            if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                modified = true
                                continue
                            }
                        }
                        filteredList.add(item)
                    }

                    if (modified) {
                        param.setResult(rebuildResultSlice(slice, filteredList))
                    }
                } catch (t: Throwable) {
                    backend.log("[$TAG] Error in hookGetShortcuts: ${t.message}")
                }
            }
        }

        // Android 12–17: getShortcuts(String callingPackage, ShortcutQueryWrapper query, UserHandle user)
        val queryWrapperClass = backend.findClassIfExists("android.content.pm.ShortcutQueryWrapper", clazz.classLoader ?: return)
        if (queryWrapperClass != null) {
            try {
                backend.findAndHookMethod(
                    clazz,
                    "getShortcuts",
                    String::class.java,
                    queryWrapperClass,
                    UserHandle::class.java,
                    callback = callback
                )
            } catch (_: Throwable) {
            }
        }

        // Android 11: getShortcuts(String, long, String, List, ComponentName, int, UserHandle)
        try {
            backend.findAndHookMethod(
                clazz,
                "getShortcuts",
                String::class.java,
                Long::class.javaPrimitiveType!!,
                String::class.java,
                java.util.List::class.java,
                ComponentName::class.java,
                Int::class.javaPrimitiveType!!,
                UserHandle::class.java,
                callback = callback
            )
        } catch (_: Throwable) {
        }
    }

    private fun getContextFromImpl(serviceObject: Any?): Context? {
        if (serviceObject == null) return null
        return try {
            backend.getObjectField(serviceObject, "mContext") as? Context
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractListFromResult(resultObj: Any): List<*>? {
        if (resultObj is List<*>) return resultObj
        return try {
            backend.callMethod(resultObj, "getList") as? List<*>
        } catch (_: Throwable) {
            try {
                backend.getObjectField(resultObj, "mList") as? List<*>
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun rebuildResultSlice(originalResult: Any, newList: ArrayList<Any>): Any {
        if (originalResult is List<*>) return newList
        return try {
            backend.newInstance(originalResult.javaClass, newList) ?: originalResult
        } catch (_: Throwable) {
            try {
                backend.setObjectField(originalResult, "mList", newList)
                originalResult
            } catch (_: Throwable) {
                originalResult
            }
        }
    }

    private fun isExemptCaller(callingUid: Int, serviceObject: Any?): Boolean {
        if (callingUid == Process.SYSTEM_UID || callingUid == 0) return true
        if (isCallerSelf(callingUid, serviceObject)) return true
        return false
    }

    private fun isCallerSelf(callingUid: Int, serviceObject: Any?): Boolean {
        if (acceptingReload) return false
        val appId = callingUid % PER_USER_RANGE
        if (appId < Process.FIRST_APPLICATION_UID) return false
        val cached = cachedSelfAppId
        if (cached != -1) {
            return appId == cached
        }
        val context = getContextFromImpl(serviceObject) ?: return false
        val token = Binder.clearCallingIdentity()
        return try {
            // Lock-free snapshot before any lookup. Launcher queries hit this path on every call
            // until the appId is cached, so it must never block on watcher registration.
            val watcherReadyAtStart = selfWatcherAllUsers
            val generationAtStart = synchronized(selfCacheLock) { selfCacheGeneration }

            val pm = context.packageManager
            val pkgs = pm.getPackagesForUid(callingUid)
            val isSelf = pkgs?.contains(ConfigStore.SELF_PACKAGE) == true
            if (!isSelf) return false

            if (!verifyCallerSignature(pm)) {
                return false
            }

            // Registration runs off the Binder thread; until it completes, caching stays disabled
            // and every call goes through full verification.
            if (!watcherReadyAtStart) scheduleSelfPackageWatcher(context)

            // Only cache once we can guarantee invalidation on uninstall/replace, and only if no
            // lifecycle event happened mid-verification; otherwise a reassigned appId would
            // inherit IPC and cross-profile privileges.
            if (watcherReadyAtStart) {
                synchronized(selfCacheLock) {
                    if (selfCacheGeneration == generationAtStart) {
                        cachedSelfAppId = appId
                    }
                }
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    @Volatile
    private var selfWatcherRegistered = false

    private val selfWatcherScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Registers the package watcher on a background thread, at most once at a time. */
    private fun scheduleSelfPackageWatcher(context: Context) {
        if (acceptingReload) return
        if (selfWatcherRegistered) return
        if (!selfWatcherScheduled.compareAndSet(false, true)) return
        try {
            val thread = Thread({
                val token = Binder.clearCallingIdentity()
                try {
                    if (!acceptingReload) ensureSelfPackageWatcher(context)
                } catch (t: Throwable) {
                    backend.log("[$TAG] Self package watcher registration failed: ${t.message}")
                } finally {
                    Binder.restoreCallingIdentity(token)
                    // Allow a retry later if registration did not happen.
                    if (!selfWatcherRegistered) selfWatcherScheduled.set(false)
                }
            }, "XHide-SelfWatcher").apply { isDaemon = true }
            watcherThread = thread
            thread.start()
        } catch (t: Throwable) {
            selfWatcherScheduled.set(false)
            backend.log("[$TAG] Could not start self watcher thread: ${t.message}")
        }
    }

    // True only when the watcher covers every user. A user-scoped fallback receiver cannot
    // guarantee invalidation when the companion is removed from another user.
    @Volatile
    private var selfWatcherAllUsers = false

    /**
     * Registers a one-time receiver in `system_server` that drops [cachedSelfAppId] whenever the
     * companion package is removed, replaced, or re-added in any user, forcing re-verification.
     * Must be called with cleared calling identity. Returns true only when the watcher covers all
     * users, i.e. when it is safe to cache the self-caller appId.
     */
    @Synchronized
    private fun ensureSelfPackageWatcher(context: Context): Boolean {
        if (selfWatcherRegistered) return selfWatcherAllUsers
        if (acceptingReload) return false
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
            addDataSchemeSpecificPart(ConfigStore.SELF_PACKAGE, android.os.PatternMatcher.PATTERN_LITERAL)
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.data?.schemeSpecificPart != ConfigStore.SELF_PACKAGE) return
                synchronized(selfCacheLock) {
                    selfCacheGeneration++
                    cachedSelfAppId = -1
                }
                backend.log("[$TAG] Companion package changed (${intent.action}); self-caller cache invalidated")
            }
        }
        selfWatcherRegistered = run {
            synchronized(lifecycleLock) {
                if (acceptingReload) return false
                watcherContext = context
                watcherReceiver = receiver
            }
            try {
                val allUsers = backend.getStaticObjectField(UserHandle::class.java, "ALL") as UserHandle
                backend.callMethod(context, "registerReceiverAsUser", receiver, allUsers, filter, null, null)
                selfWatcherAllUsers = true
                true
            } catch (t: Throwable) {
                backend.log("[$TAG] registerReceiverAsUser failed, falling back: ${t.message}")
                try {
                    // User-scoped only: still useful for invalidation, but not sufficient to cache.
                    context.registerReceiver(receiver, filter)
                    backend.log("[$TAG] Self package watcher is user-scoped; self-caller appId will not be cached")
                    true
                } catch (t2: Throwable) {
                    backend.log("[$TAG] Could not register self package watcher: ${t2.message}")
                    synchronized(lifecycleLock) {
                        if (watcherReceiver === receiver) {
                            watcherReceiver = null
                            watcherContext = null
                        }
                    }
                    false
                }
            }
        }
        return selfWatcherAllUsers
    }

    private fun verifyCallerSignature(pm: PackageManager): Boolean {
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(ConfigStore.SELF_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ConfigStore.SELF_PACKAGE, PackageManager.GET_SIGNATURES)
            } ?: return false

            val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.signatures
            }
            if (signers.isNullOrEmpty()) return false

            val pinnedCert = BuildConfig.RELEASE_CERT_SHA256
            if (pinnedCert.isNotEmpty()) {
                val md = MessageDigest.getInstance("SHA-256")
                val digestHex = md.digest(signers[0].toByteArray())
                    .joinToString("") { "%02x".format(it) }
                return digestHex.equals(pinnedCert, ignoreCase = true)
            }
            true
        } catch (t: Throwable) {
            backend.log("[$TAG] Caller signature verification error: ${t.message}")
            false
        }
    }

    private fun queryLauncherActivitiesForProfile(launcherAppsImpl: Any, profile: UserHandle): List<*> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        // 1. Android 12–17: queryIntentLauncherActivities(Intent, int filterCallingUid, UserHandle)
        val token = Binder.clearCallingIdentity()
        try {
            val res = backend.callMethod(
                launcherAppsImpl,
                "queryIntentLauncherActivities",
                intent,
                1000,
                profile
            ) as? List<*>
            if (!res.isNullOrEmpty()) return res
        } catch (_: Throwable) {
        } finally {
            Binder.restoreCallingIdentity(token)
        }

        // 2. Android 11–17: queryActivitiesForUser(String, Intent, UserHandle)
        // Keep caller identity so our canAccessProfile hook recognizes isCallerSelfApp!
        try {
            val slice = backend.callMethod(
                launcherAppsImpl,
                "queryActivitiesForUser",
                ConfigStore.SELF_PACKAGE,
                intent,
                profile
            )
            if (slice != null) {
                val list = extractListFromResult(slice)
                if (!list.isNullOrEmpty()) return list
            }
        } catch (_: Throwable) {
        }

        // 3. Legacy 2-arg fallback: queryActivitiesForUser(Intent, UserHandle)
        try {
            val slice = backend.callMethod(
                launcherAppsImpl,
                "queryActivitiesForUser",
                intent,
                profile
            )
            if (slice != null) {
                val list = extractListFromResult(slice)
                if (!list.isNullOrEmpty()) return list
            }
        } catch (_: Throwable) {
        }

        return emptyList<Any>()
    }

    private fun getUserId(userHandle: UserHandle?): Int = userIdOf(userHandle, backend)

    /**
     * Supports:
     * - `LauncherActivityInfoInternal` (Android 12–17: `getActivityInfo()` / `mActivityInfo`)
     * - `ResolveInfo` (Android 11: `activityInfo`)
     * - Direct `ActivityInfo` (Android 11 `resolveActivity`)
     */
    private fun extractActivityInfo(item: Any): ActivityInfo? {
        if (item is ActivityInfo) return item
        if (item is ResolveInfo) return item.activityInfo
        return try {
            backend.callMethod(item, "getActivityInfo") as? ActivityInfo
        } catch (_: Throwable) {
            try {
                backend.getObjectField(item, "mActivityInfo") as? ActivityInfo
            } catch (_: Throwable) {
                try {
                    backend.getObjectField(item, "activityInfo") as? ActivityInfo
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }

    /**
     * Notifies all registered launchers across affected user profiles on Android 11–17:
     * Broadcasts `onPackageRemoved` / `onPackagesUnavailable` when hiding so OEM launchers (One UI Home,
     * HyperOS, ColorOS) and AOSP Launcher3 immediately purge icons from persistent SQLite caches and UI.
     * Broadcasts `onPackageAdded` / `onPackagesAvailable` when unhiding so launchers re-query activities.
     *
     * Correctly filters by target profile (`userId`) so secondary/work profiles (e.g. User 10) never receive
     * events for User 0 apps (preventing OEM launcher `removeIf` race conditions).
     * Validates listener accessibility via `isEnabledProfileOf` using the listener's `BroadcastCookie`.
     */
    private fun notifyLaunchersOfChanges(
        launcherAppsImpl: Any?,
        changedPackages: List<String>,
        fallbackUserHandle: UserHandle?
    ) {
        if (launcherAppsImpl == null || changedPackages.isEmpty()) return
        try {
            val handler = backend.getObjectField(launcherAppsImpl, "mCallbackHandler") as? Handler
            val profiles = collectAllUserHandles(launcherAppsImpl, fallbackUserHandle)

            val parsedTargets = changedPackages.map { raw ->
                if (raw.contains(':')) {
                    val uidStr = raw.substringBefore(':')
                    val pkg = raw.substringAfter(':')
                    uidStr.toIntOrNull() to pkg
                } else {
                    null to raw
                }
            }

            val task = Runnable {
                for (profile in profiles) {
                    val profileUserId = getUserId(profile)
                    val pkgsForThisProfile = parsedTargets.filter { (targetUserId, _) ->
                        targetUserId == null || targetUserId == profileUserId
                    }.map { it.second }.distinct()

                    if (pkgsForThisProfile.isNotEmpty()) {
                        broadcastOnPackageChangedBatch(launcherAppsImpl, profile, pkgsForThisProfile)
                    }
                }
            }
            if (handler != null) {
                handler.post(task)
            } else {
                task.run()
            }
        } catch (t: Throwable) {
            backend.log("[$TAG] Error in notifyLaunchersOfChanges: ${t.message}")
        }
    }

    private fun broadcastOnPackageChangedBatch(
        launcherAppsImpl: Any,
        profile: UserHandle,
        changedPackages: List<String>
    ) {
        try {
            val listeners = backend.getObjectField(launcherAppsImpl, "mListeners") as? RemoteCallbackList<*> ?: return
            val count = listeners.beginBroadcast()
            val userId = getUserId(profile)
            val context = getContextFromImpl(launcherAppsImpl)
            try {
                val (hiddenPkgs, unhiddenPkgs) = changedPackages.partition { pkg ->
                    val isModule = ModuleCache.isXposedModule(
                        packageName = pkg,
                        appInfo = null,
                        context = context,
                        userHandle = profile,
                        backend = backend
                    )
                    ConfigStore.shouldHidePackage(
                        packageName = pkg,
                        userId = userId,
                        isDetectedXposedModule = isModule
                    )
                }

                val emptySlice = if (hiddenPkgs.isNotEmpty()) {
                    runCatching {
                        val sliceClass = Class.forName("android.content.pm.ParceledListSlice")
                        backend.callStaticMethod(sliceClass, "emptyList")
                    }.getOrNull()
                } else null

                for (i in 0 until count) {
                    val listener = listeners.getBroadcastItem(i) as? IInterface ?: continue
                    val cookie = try { listeners.getBroadcastCookie(i) } catch (_: Throwable) { null }

                    if (cookie != null) {
                        val isAccessible = runCatching {
                            backend.callMethod(launcherAppsImpl, "isEnabledProfileOf", profile, cookie, "onPackageChanged") as? Boolean
                        }.getOrNull()
                        if (isAccessible == false) {
                            continue
                        }
                    }

                    // 1. Process Hidden Packages
                    if (hiddenPkgs.isNotEmpty()) {
                        for (pkg in hiddenPkgs) {
                            try {
                                backend.callMethod(listener, "onPackageRemoved", profile, pkg)
                            } catch (t: Throwable) {
                                backend.log("[$TAG] Error calling onPackageRemoved($profile, $pkg): ${t.message}")
                            }
                            if (emptySlice != null) {
                                try {
                                    backend.callMethod(listener, "onShortcutChanged", profile, pkg, emptySlice)
                                } catch (_: Throwable) {
                                }
                            }
                        }
                        try {
                            backend.callMethod(listener, "onPackagesUnavailable", profile, hiddenPkgs.toTypedArray(), false)
                        } catch (t: Throwable) {
                            backend.log("[$TAG] Error calling onPackagesUnavailable: ${t.message}")
                        }
                    }

                    // 2. Process Unhidden Packages
                    if (unhiddenPkgs.isNotEmpty()) {
                        for (pkg in unhiddenPkgs) {
                            try {
                                backend.callMethod(listener, "onPackageAdded", profile, pkg)
                            } catch (t: Throwable) {
                                backend.log("[$TAG] Error calling onPackageAdded($profile, $pkg): ${t.message}")
                            }
                        }
                        try {
                            backend.callMethod(listener, "onPackagesAvailable", profile, unhiddenPkgs.toTypedArray(), false)
                        } catch (t: Throwable) {
                            backend.log("[$TAG] Error calling onPackagesAvailable: ${t.message}")
                        }
                    }
                }
            } finally {
                listeners.finishBroadcast()
            }
        } catch (t: Throwable) {
            backend.log("[$TAG] Error in broadcastOnPackageChangedBatch: ${t.message}")
        }
    }

    private fun collectAllUserHandles(launcherAppsImpl: Any, fallbackUserHandle: UserHandle?): List<UserHandle> {
        val handles = LinkedHashSet<UserHandle>()
        if (fallbackUserHandle != null) handles.add(fallbackUserHandle)

        val token = Binder.clearCallingIdentity()
        try {
            // 1. UserManagerInternal#getUserIds() -> returns all existing user IDs on Android 11–17
            try {
                val umInternal = backend.getObjectField(launcherAppsImpl, "mUserManagerInternal")
                if (umInternal != null) {
                    val userIds = backend.callMethod(umInternal, "getUserIds") as? IntArray
                    if (userIds != null) {
                        for (id in userIds) {
                            val uh = backend.callStaticMethod(UserHandle::class.java, "of", id) as? UserHandle
                            if (uh != null) handles.add(uh)
                        }
                    }
                }
            } catch (_: Throwable) {
            }

            // 2. UserManager#getUsers() -> fallback across AOSP & OEM ROMs
            try {
                val um = backend.getObjectField(launcherAppsImpl, "mUm") as? UserManager
                if (um != null) {
                    val users = backend.callMethod(um, "getUsers") as? List<*>
                    if (users != null) {
                        for (u in users) {
                            if (u == null) continue
                            val id = backend.getIntField(u, "id")
                            val uh = backend.callStaticMethod(UserHandle::class.java, "of", id) as? UserHandle
                            if (uh != null) handles.add(uh)
                        }
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
}
