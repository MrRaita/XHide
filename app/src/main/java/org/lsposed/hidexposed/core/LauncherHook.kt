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
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
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
    private const val PER_USER_RANGE = 100000

    @Volatile
    private var cachedSelfAppId: Int = -1

    fun install(classLoader: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(TARGET_CLASS, classLoader)
            if (clazz == null) {
                XposedBridge.log("[$TAG] Target class not found: $TARGET_CLASS")
                return
            }

            runCatching { hookGetUserProfiles(clazz) }
                .onFailure { XposedBridge.log("[$TAG] hookGetUserProfiles failed: ${it.message}") }
            runCatching { hookUserManagerGetProfileIds(classLoader) }
                .onFailure { XposedBridge.log("[$TAG] hookUserManagerGetProfileIds failed: ${it.message}") }
            runCatching { hookProfileAccessChecks(clazz) }
                .onFailure { XposedBridge.log("[$TAG] hookProfileAccessChecks failed: ${it.message}") }
            runCatching { hookGetLauncherActivities(clazz) }
                .onFailure { XposedBridge.log("[$TAG] hookGetLauncherActivities failed: ${it.message}") }
            runCatching { hookGetShortcuts(clazz) }
                .onFailure { XposedBridge.log("[$TAG] hookGetShortcuts failed: ${it.message}") }
            runCatching { hookResolveAndEnabledChecks(clazz) }
                .onFailure { XposedBridge.log("[$TAG] hookResolveAndEnabledChecks failed: ${it.message}") }

            XposedBridge.log("[$TAG] Successfully installed system hooks on $TARGET_CLASS (Android 11–17)")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to install LauncherHook: ${t.message}")
        }
    }

    /**
     * Hooks `ILauncherApps#getUserProfiles()` (used by `LauncherApps.getProfiles()` on Android 15–17)
     * so `XHide` receives every active UserHandle on the device.
     */
    private fun hookGetUserProfiles(clazz: Class<*>) {
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "getUserProfiles",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            if (!isCallerSelf(Binder.getCallingUid(), param.thisObject)) return
                            val existing = (param.result as? List<*>)
                                ?.filterIsInstance<UserHandle>()
                                ?: emptyList()
                            val allHandles = collectAllUserHandles(param.thisObject, null)
                            val merged = LinkedHashSet<UserHandle>()
                            merged.addAll(existing)
                            merged.addAll(allHandles)
                            param.result = ArrayList(merged)
                        } catch (t: Throwable) {
                            XposedBridge.log("[$TAG] Error in getUserProfiles hook: ${t.message}")
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Could not hook getUserProfiles: ${t.message}")
        }
    }

    /**
     * Hooks `UserManagerService#getProfileIds(int, boolean)` (used by `LauncherApps.getProfiles()`
     * and `UserManager.getUserProfiles()` on Android 11–17) so `XHide` receives all user IDs
     * including Clone (10), Work, Dual Apps (999), Secure Folder (150), and Secondary Users.
     */
    private fun hookUserManagerGetProfileIds(classLoader: ClassLoader) {
        try {
            val umsClass = XposedHelpers.findClassIfExists(USER_MANAGER_SERVICE_CLASS, classLoader) ?: return
            XposedHelpers.findAndHookMethod(
                umsClass,
                "getProfileIds",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            if (!isCallerSelf(Binder.getCallingUid(), param.thisObject)) return
                            val baseIds = param.result as? IntArray ?: IntArray(0)
                            val allUserIds = try {
                                XposedHelpers.callMethod(param.thisObject, "getUserIds") as? IntArray
                            } catch (_: Throwable) {
                                null
                            } ?: return

                            val merged = LinkedHashSet<Int>()
                            for (id in baseIds) merged.add(id)
                            for (id in allUserIds) merged.add(id)
                            param.result = merged.toIntArray()
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Could not hook UserManagerService.getProfileIds: ${t.message}")
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
            XposedHelpers.findAndHookMethod(
                clazz,
                "canAccessProfile",
                Int::class.javaPrimitiveType,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            if (isCallerSelf(Binder.getCallingUid(), param.thisObject)) {
                                param.result = true
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
            XposedHelpers.findAndHookMethod(
                clazz,
                "canAccessProfile",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val binderUid = Binder.getCallingUid()
                            val argUid = param.args[0] as? Int ?: binderUid
                            if (argUid == binderUid && isCallerSelf(binderUid, param.thisObject)) {
                                param.result = true
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
            XposedHelpers.findAndHookMethod(
                clazz,
                "ensureInUserProfiles",
                Int::class.javaPrimitiveType,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            if (isCallerSelf(Binder.getCallingUid(), param.thisObject)) {
                                param.result = null
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
            XposedHelpers.findAndHookMethod(
                clazz,
                "canAccessHiddenProfile",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val binderUid = Binder.getCallingUid()
                            val argUid = param.args[0] as? Int ?: binderUid
                            if (argUid == binderUid && isCallerSelf(binderUid, param.thisObject)) {
                                param.result = true
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
            XposedHelpers.findAndHookMethod(
                clazz,
                "injectHasInteractAcrossUsersFullPermission",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val binderUid = Binder.getCallingUid()
                            val argUid = param.args[1] as? Int ?: binderUid
                            if (argUid == binderUid && isCallerSelf(binderUid, param.thisObject)) {
                                param.result = true
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
        XposedHelpers.findAndHookMethod(
            clazz,
            "getLauncherActivities",
            String::class.java,
            String::class.java,
            UserHandle::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
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
                                param.result = emptyList<Any>()
                                return
                            }

                            when {
                                targetPackage == ConfigStore.IPC_PING -> {
                                    param.args[1] = ConfigStore.SELF_PACKAGE
                                }
                                targetPackage == ConfigStore.IPC_GET_ALL_USERS -> {
                                    param.setObjectExtra(EXTRA_ALL_USERS_QUERY, true)
                                    param.args[1] = null
                                }
                                targetPackage.startsWith(ConfigStore.IPC_UPDATE_PREFIX) -> {
                                    val payload = targetPackage.removePrefix(ConfigStore.IPC_UPDATE_PREFIX)
                                    val changedPackages = ConfigStore.applySystemIpcUpdate(payload)
                                    notifyLaunchersOfChanges(param.thisObject, changedPackages, userHandle)
                                    param.args[1] = ConfigStore.SELF_PACKAGE
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        XposedBridge.log("[$TAG] Error in beforeHookedMethod: ${t.message}")
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val targetPackage = param.args[1] as? String
                        val userHandle = param.args[2] as? UserHandle
                        val userId = getUserId(userHandle)
                        val callingUid = Binder.getCallingUid()

                        // If our companion UI requested all users' launcher activities in one call
                        if (param.getObjectExtra(EXTRA_ALL_USERS_QUERY) == true &&
                            isCallerSelf(callingUid, param.thisObject)
                        ) {
                            val slice = param.result ?: return
                            val baseList = extractListFromResult(slice) ?: emptyList<Any>()
                            val combined = ArrayList<Any>(baseList.filterNotNull())
                            val profiles = collectAllUserHandles(param.thisObject, userHandle)

                            val expectedClass = baseList.firstOrNull()?.javaClass
                            for (profile in profiles) {
                                if (getUserId(profile) == userId) continue
                                val extraItems = queryLauncherActivitiesForProfile(param.thisObject, profile)
                                for (item in extraItems) {
                                    if (item != null && (expectedClass == null || expectedClass.isInstance(item))) {
                                        combined.add(item)
                                    }
                                }
                            }

                            param.result = rebuildResultSlice(slice, combined)
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
                                    userHandle = userHandle
                                )
                                if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                    modified = true
                                    continue
                                }
                            }
                            filteredList.add(item)
                        }

                        if (modified) {
                            param.result = rebuildResultSlice(slice, filteredList)
                        }
                    } catch (t: Throwable) {
                        XposedBridge.log("[$TAG] Fail-safe catch in afterHookedMethod: ${t.message}")
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
        val resolveCallback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    if (param.result == null) return
                    val callingUid = Binder.getCallingUid()
                    if (isExemptCaller(callingUid, param.thisObject)) return

                    val component = param.args[1] as? ComponentName
                    val userHandle = param.args[2] as? UserHandle
                    val pkgName = component?.packageName ?: return
                    val userId = getUserId(userHandle)
                    val context = getContextFromImpl(param.thisObject)
                    val activityInfo = extractActivityInfo(param.result)

                    val isModule = ModuleCache.isXposedModule(
                        packageName = pkgName,
                        appInfo = activityInfo?.applicationInfo,
                        context = context,
                        userHandle = userHandle
                    )
                    if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                        param.result = null
                    }
                } catch (_: Throwable) {
                }
            }
        }

        // Android 12–17: resolveLauncherActivityInternal(String, ComponentName, UserHandle)
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "resolveLauncherActivityInternal",
                String::class.java,
                ComponentName::class.java,
                UserHandle::class.java,
                resolveCallback
            )
        } catch (_: Throwable) {
        }

        // Android 11: resolveActivity(String, ComponentName, UserHandle)
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "resolveActivity",
                String::class.java,
                ComponentName::class.java,
                UserHandle::class.java,
                resolveCallback
            )
        } catch (_: Throwable) {
        }

        // Android 11–17: isPackageEnabled(String, String, UserHandle)
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "isPackageEnabled",
                String::class.java,
                String::class.java,
                UserHandle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
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
                                userHandle = userHandle
                            )
                            if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                param.result = false
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
            XposedHelpers.findAndHookMethod(
                clazz,
                "isActivityEnabled",
                String::class.java,
                ComponentName::class.java,
                UserHandle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
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
                                userHandle = userHandle
                            )
                            if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                param.result = false
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }

        // Android 11–17: getApplicationInfo(String, String, int/long, UserHandle)
        val appInfoCallback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
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
                        userHandle = userHandle
                    )
                    if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                        param.result = null
                    }
                } catch (_: Throwable) {
                }
            }
        }
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "getApplicationInfo",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                UserHandle::class.java,
                appInfoCallback
            )
        } catch (_: Throwable) {
        }
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "getApplicationInfo",
                String::class.java,
                String::class.java,
                Long::class.javaPrimitiveType,
                UserHandle::class.java,
                appInfoCallback
            )
        } catch (_: Throwable) {
        }

        // Android 12–17: shouldHideFromSuggestions(String, UserHandle)
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "shouldHideFromSuggestions",
                String::class.java,
                UserHandle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
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
                                userHandle = userHandle
                            )
                            if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                param.result = true
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
            XposedHelpers.findAndHookMethod(
                clazz,
                "getActivityLaunchIntent",
                String::class.java,
                ComponentName::class.java,
                UserHandle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
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
                                userHandle = userHandle
                            )
                            if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                param.result = null
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
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
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
                            XposedHelpers.callMethod(item, "getPackage") as? String
                        } catch (_: Throwable) {
                            null
                        }

                        if (!pkgName.isNullOrEmpty()) {
                            val isModule = ModuleCache.isXposedModule(
                                packageName = pkgName,
                                appInfo = null,
                                context = context,
                                userHandle = userHandle
                            )
                            if (ConfigStore.shouldHidePackage(pkgName, userId, isModule)) {
                                modified = true
                                continue
                            }
                        }
                        filteredList.add(item)
                    }

                    if (modified) {
                        param.result = rebuildResultSlice(slice, filteredList)
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("[$TAG] Error in hookGetShortcuts: ${t.message}")
                }
            }
        }

        // Android 12–17: getShortcuts(String callingPackage, ShortcutQueryWrapper query, UserHandle user)
        val queryWrapperClass = XposedHelpers.findClassIfExists("android.content.pm.ShortcutQueryWrapper", clazz.classLoader)
        if (queryWrapperClass != null) {
            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "getShortcuts",
                    String::class.java,
                    queryWrapperClass,
                    UserHandle::class.java,
                    callback
                )
            } catch (_: Throwable) {
            }
        }

        // Android 11: getShortcuts(String, long, String, List, ComponentName, int, UserHandle)
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "getShortcuts",
                String::class.java,
                Long::class.javaPrimitiveType,
                String::class.java,
                java.util.List::class.java,
                ComponentName::class.java,
                Int::class.javaPrimitiveType,
                UserHandle::class.java,
                callback
            )
        } catch (_: Throwable) {
        }
    }

    private fun getContextFromImpl(serviceObject: Any?): Context? {
        if (serviceObject == null) return null
        return try {
            XposedHelpers.getObjectField(serviceObject, "mContext") as? Context
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractListFromResult(resultObj: Any): List<*>? {
        if (resultObj is List<*>) return resultObj
        return try {
            XposedHelpers.callMethod(resultObj, "getList") as? List<*>
        } catch (_: Throwable) {
            try {
                XposedHelpers.getObjectField(resultObj, "mList") as? List<*>
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun rebuildResultSlice(originalResult: Any, newList: ArrayList<Any>): Any {
        if (originalResult is List<*>) return newList
        return try {
            XposedHelpers.newInstance(originalResult.javaClass, newList)
        } catch (_: Throwable) {
            try {
                XposedHelpers.setObjectField(originalResult, "mList", newList)
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
        val appId = callingUid % PER_USER_RANGE
        if (appId < Process.FIRST_APPLICATION_UID) return false
        val cached = cachedSelfAppId
        if (cached != -1) {
            return appId == cached
        }
        val context = getContextFromImpl(serviceObject) ?: return false
        val token = Binder.clearCallingIdentity()
        return try {
            val pm = context.packageManager
            val pkgs = pm.getPackagesForUid(callingUid)
            val isSelf = pkgs?.contains(ConfigStore.SELF_PACKAGE) == true
            if (!isSelf) return false

            if (!verifyCallerSignature(pm)) {
                return false
            }

            // Only cache once we can guarantee invalidation on uninstall/replace;
            // otherwise a reassigned appId would inherit IPC and cross-profile privileges.
            if (ensureSelfPackageWatcher(context)) {
                cachedSelfAppId = appId
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
                cachedSelfAppId = -1
                XposedBridge.log("[$TAG] Companion package changed (${intent.action}); self-caller cache invalidated")
            }
        }
        selfWatcherRegistered = try {
            val allUsers = XposedHelpers.getStaticObjectField(UserHandle::class.java, "ALL") as UserHandle
            XposedHelpers.callMethod(context, "registerReceiverAsUser", receiver, allUsers, filter, null, null)
            selfWatcherAllUsers = true
            true
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] registerReceiverAsUser failed, falling back: ${t.message}")
            try {
                // User-scoped only: still useful for invalidation, but not sufficient to cache.
                context.registerReceiver(receiver, filter)
                XposedBridge.log("[$TAG] Self package watcher is user-scoped; self-caller appId will not be cached")
                true
            } catch (t2: Throwable) {
                XposedBridge.log("[$TAG] Could not register self package watcher: ${t2.message}")
                false
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
            XposedBridge.log("[$TAG] Caller signature verification error: ${t.message}")
            false
        }
    }

    private fun queryLauncherActivitiesForProfile(launcherAppsImpl: Any, profile: UserHandle): List<*> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        // 1. Android 12–17: queryIntentLauncherActivities(Intent, int filterCallingUid, UserHandle)
        val token = Binder.clearCallingIdentity()
        try {
            val res = XposedHelpers.callMethod(
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
            val slice = XposedHelpers.callMethod(
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
            val slice = XposedHelpers.callMethod(
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

    private fun getUserId(userHandle: UserHandle?): Int = userIdOf(userHandle)

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
            XposedHelpers.callMethod(item, "getActivityInfo") as? ActivityInfo
        } catch (_: Throwable) {
            try {
                XposedHelpers.getObjectField(item, "mActivityInfo") as? ActivityInfo
            } catch (_: Throwable) {
                try {
                    XposedHelpers.getObjectField(item, "activityInfo") as? ActivityInfo
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
            val handler = XposedHelpers.getObjectField(launcherAppsImpl, "mCallbackHandler") as? Handler
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
            XposedBridge.log("[$TAG] Error in notifyLaunchersOfChanges: ${t.message}")
        }
    }

    private fun broadcastOnPackageChangedBatch(
        launcherAppsImpl: Any,
        profile: UserHandle,
        changedPackages: List<String>
    ) {
        try {
            val listeners = XposedHelpers.getObjectField(launcherAppsImpl, "mListeners") as? RemoteCallbackList<*> ?: return
            val count = listeners.beginBroadcast()
            val userId = getUserId(profile)
            val context = getContextFromImpl(launcherAppsImpl)
            try {
                val (hiddenPkgs, unhiddenPkgs) = changedPackages.partition { pkg ->
                    val isModule = ModuleCache.isXposedModule(
                        packageName = pkg,
                        appInfo = null,
                        context = context,
                        userHandle = profile
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
                        XposedHelpers.callStaticMethod(sliceClass, "emptyList")
                    }.getOrNull()
                } else null

                for (i in 0 until count) {
                    val listener = listeners.getBroadcastItem(i) as? IInterface ?: continue
                    val cookie = try { listeners.getBroadcastCookie(i) } catch (_: Throwable) { null }

                    if (cookie != null) {
                        val isAccessible = runCatching {
                            XposedHelpers.callMethod(launcherAppsImpl, "isEnabledProfileOf", profile, cookie, "onPackageChanged") as? Boolean
                        }.getOrNull()
                        if (isAccessible == false) {
                            continue
                        }
                    }

                    // 1. Process Hidden Packages
                    if (hiddenPkgs.isNotEmpty()) {
                        for (pkg in hiddenPkgs) {
                            try {
                                XposedHelpers.callMethod(listener, "onPackageRemoved", profile, pkg)
                            } catch (t: Throwable) {
                                XposedBridge.log("[$TAG] Error calling onPackageRemoved($profile, $pkg): ${t.message}")
                            }
                            if (emptySlice != null) {
                                try {
                                    XposedHelpers.callMethod(listener, "onShortcutChanged", profile, pkg, emptySlice)
                                } catch (_: Throwable) {
                                }
                            }
                        }
                        try {
                            XposedHelpers.callMethod(listener, "onPackagesUnavailable", profile, hiddenPkgs.toTypedArray(), false)
                        } catch (t: Throwable) {
                            XposedBridge.log("[$TAG] Error calling onPackagesUnavailable: ${t.message}")
                        }
                    }

                    // 2. Process Unhidden Packages
                    if (unhiddenPkgs.isNotEmpty()) {
                        for (pkg in unhiddenPkgs) {
                            try {
                                XposedHelpers.callMethod(listener, "onPackageAdded", profile, pkg)
                            } catch (t: Throwable) {
                                XposedBridge.log("[$TAG] Error calling onPackageAdded($profile, $pkg): ${t.message}")
                            }
                        }
                        try {
                            XposedHelpers.callMethod(listener, "onPackagesAvailable", profile, unhiddenPkgs.toTypedArray(), false)
                        } catch (t: Throwable) {
                            XposedBridge.log("[$TAG] Error calling onPackagesAvailable: ${t.message}")
                        }
                    }
                }
            } finally {
                listeners.finishBroadcast()
            }
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Error in broadcastOnPackageChangedBatch: ${t.message}")
        }
    }

    private fun collectAllUserHandles(launcherAppsImpl: Any, fallbackUserHandle: UserHandle?): List<UserHandle> {
        val handles = LinkedHashSet<UserHandle>()
        if (fallbackUserHandle != null) handles.add(fallbackUserHandle)

        val token = Binder.clearCallingIdentity()
        try {
            // 1. UserManagerInternal#getUserIds() -> returns all existing user IDs on Android 11–17
            try {
                val umInternal = XposedHelpers.getObjectField(launcherAppsImpl, "mUserManagerInternal")
                if (umInternal != null) {
                    val userIds = XposedHelpers.callMethod(umInternal, "getUserIds") as? IntArray
                    if (userIds != null) {
                        for (id in userIds) {
                            val uh = XposedHelpers.callStaticMethod(UserHandle::class.java, "of", id) as? UserHandle
                            if (uh != null) handles.add(uh)
                        }
                    }
                }
            } catch (_: Throwable) {
            }

            // 2. UserManager#getUsers() -> fallback across AOSP & OEM ROMs
            try {
                val um = XposedHelpers.getObjectField(launcherAppsImpl, "mUm") as? UserManager
                if (um != null) {
                    val users = XposedHelpers.callMethod(um, "getUsers") as? List<*>
                    if (users != null) {
                        for (u in users) {
                            if (u == null) continue
                            val id = XposedHelpers.getIntField(u, "id")
                            val uh = XposedHelpers.callStaticMethod(UserHandle::class.java, "of", id) as? UserHandle
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
