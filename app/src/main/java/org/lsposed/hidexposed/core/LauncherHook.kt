package org.lsposed.hidexposed.core

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Binder
import android.os.Handler
import android.os.IInterface
import android.os.RemoteCallbackList
import android.os.UserHandle
import android.os.UserManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.lsposed.hidexposed.data.ConfigStore
import java.util.ArrayList

/**
 * System-level hook targeting `com.android.server.pm.LauncherAppsService$LauncherAppsImpl`.
 *
 * Compatible with Android 11 (SDK 30) through Android 17 (SDK 37+):
 * 1. `getUserProfiles()` — Ensures our companion UI receives all user profiles on the device
 *    (User 0, User 10 Clone Profile, Work Profile, Dual Apps, Private Space, Secondary Users).
 * 2. `canAccessProfile(int, String)` (Android 13–17), `ensureInUserProfiles(int, String)` (Android 11–12),
 *    and `canAccessHiddenProfile(int, int)` (Android 15–17 Private Space) — Grants our companion UI
 *    permission to inspect launcher activities and badged icons across all profiles.
 * 3. `getLauncherActivities(String, String, UserHandle)` — Filters hidden Xposed modules and
 *    per-user blacklisted apps from all launchers while keeping them visible to Xposed managers.
 */
object LauncherHook {
    private const val TAG = "XHide"
    private const val TARGET_CLASS = "com.android.server.pm.LauncherAppsService\$LauncherAppsImpl"
    private const val EXTRA_ALL_USERS_QUERY = "hidexposed_all_users_query"

    fun install(classLoader: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(TARGET_CLASS, classLoader)
            if (clazz == null) {
                XposedBridge.log("[$TAG] Target class not found: $TARGET_CLASS")
                return
            }

            hookGetUserProfiles(clazz)
            hookProfileAccessChecks(clazz)
            hookGetLauncherActivities(clazz)

            XposedBridge.log("[$TAG] Successfully installed system hooks on $TARGET_CLASS")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to install LauncherHook: ${t.message}")
        }
    }

    /**
     * Hooks `ILauncherApps#getUserProfiles()` so that when `XHide` calls
     * `launcherApps.getProfiles()`, it receives every active UserHandle on the device.
     */
    private fun hookGetUserProfiles(clazz: Class<*>) {
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "getUserProfiles",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            if (!isCallerSelfApp(param.thisObject)) return
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
     * Hooks profile access checks across Android 11–17:
     * - `canAccessProfile(int, String)` (Android 13–17)
     * - `ensureInUserProfiles(int, String)` (Android 11–12)
     * - `canAccessHiddenProfile(int, int)` (Android 15–17 Private Space / Clone profiles)
     */
    private fun hookProfileAccessChecks(clazz: Class<*>) {
        // Android 13–17: boolean canAccessProfile(int targetUserId, String message)
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "canAccessProfile",
                Int::class.javaPrimitiveType,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            if (isCallerSelfApp(param.thisObject)) {
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
                            if (isCallerSelfApp(param.thisObject)) {
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
                            if (isCallerSelfApp(param.thisObject)) {
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
                        if (callingPackage == ConfigStore.SELF_PACKAGE &&
                            targetPackage.startsWith(ConfigStore.IPC_PREFIX)
                        ) {
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
                        val callingPackage = param.args[0] as? String ?: return
                        val targetPackage = param.args[1] as? String
                        val userHandle = param.args[2] as? UserHandle
                        val userId = getUserId(userHandle)

                        // If our companion UI requested all users' launcher activities in one call
                        if (callingPackage == ConfigStore.SELF_PACKAGE &&
                            param.getObjectExtra(EXTRA_ALL_USERS_QUERY) == true
                        ) {
                            val slice = param.result ?: return
                            val baseList = extractListFromResult(slice) ?: emptyList<Any>()
                            val combined = ArrayList<Any>(baseList.filterNotNull())
                            val profiles = collectAllUserHandles(param.thisObject, userHandle)

                            for (profile in profiles) {
                                if (getUserId(profile) == userId) continue
                                val extraItems = queryLauncherActivitiesForProfile(param.thisObject, profile)
                                for (item in extraItems) {
                                    if (item != null) combined.add(item)
                                }
                            }

                            param.result = rebuildResultSlice(slice, combined)
                            return
                        }

                        // Never filter queries coming from core system, our own UI, or Xposed managers
                        if (callingPackage == "android" ||
                            callingPackage == ConfigStore.SELF_PACKAGE ||
                            ModuleCache.KNOWN_MANAGERS.contains(callingPackage)
                        ) {
                            return
                        }

                        val slice = param.result ?: return
                        val rawList = extractListFromResult(slice) ?: return
                        if (rawList.isEmpty()) return

                        val context = try {
                            XposedHelpers.getObjectField(param.thisObject, "mContext") as? Context
                        } catch (_: Throwable) {
                            null
                        }

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

    private fun isCallerSelfApp(launcherAppsImpl: Any?): Boolean {
        if (launcherAppsImpl == null) return false
        val callingUid = Binder.getCallingUid()
        val token = Binder.clearCallingIdentity()
        return try {
            val context = XposedHelpers.getObjectField(launcherAppsImpl, "mContext") as? Context ?: return false
            val pkgs = context.packageManager.getPackagesForUid(callingUid) ?: return false
            pkgs.contains(ConfigStore.SELF_PACKAGE)
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun queryLauncherActivitiesForProfile(launcherAppsImpl: Any, profile: UserHandle): List<*> {
        val token = Binder.clearCallingIdentity()
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
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
            }

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

            try {
                val slice = XposedHelpers.callMethod(
                    launcherAppsImpl,
                    "queryActivitiesForUser",
                    "android",
                    intent,
                    profile
                )
                if (slice != null) {
                    val list = extractListFromResult(slice)
                    if (!list.isNullOrEmpty()) return list
                }
            } catch (_: Throwable) {
            }

            emptyList<Any>()
        } catch (_: Throwable) {
            emptyList<Any>()
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun getUserId(userHandle: UserHandle?): Int {
        if (userHandle == null) return 0
        return try {
            XposedHelpers.callMethod(userHandle, "getIdentifier") as Int
        } catch (_: Throwable) {
            userHandle.hashCode()
        }
    }

    /**
     * Supports:
     * - `LauncherActivityInfoInternal` (Android 11–17: `getActivityInfo()` / `mActivityInfo`)
     * - `ResolveInfo` (Android 10 / legacy custom ROMs: `activityInfo`)
     * - Direct `ActivityInfo`
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
     * Notifies all registered launchers across all user profiles on Android 11–17:
     * 1. First attempts `MyPackageMonitor#onPackageChanged(String, UserHandle)` (Android 12–17).
     * 2. Falls back to broadcasting `IOnAppsChangedListener#onPackageChanged(UserHandle, String)`
     *    directly via `LauncherAppsImpl#mListeners` (Android 11–17 universal).
     */
    private fun notifyLaunchersOfChanges(
        launcherAppsImpl: Any?,
        changedPackages: List<String>,
        fallbackUserHandle: UserHandle?
    ) {
        if (launcherAppsImpl == null || changedPackages.isEmpty()) return
        try {
            val monitor = XposedHelpers.getObjectField(launcherAppsImpl, "mPackageMonitor")
            val handler = XposedHelpers.getObjectField(launcherAppsImpl, "mCallbackHandler") as? Handler
            val profiles = collectAllUserHandles(launcherAppsImpl, fallbackUserHandle)

            val task = Runnable {
                for (profile in profiles) {
                    for (pkg in changedPackages) {
                        var notified = false
                        if (monitor != null) {
                            try {
                                XposedHelpers.callMethod(monitor, "onPackageChanged", pkg, profile)
                                notified = true
                            } catch (_: Throwable) {
                            }
                        }
                        if (!notified) {
                            broadcastOnPackageChangedFallback(launcherAppsImpl, profile, pkg)
                        }
                    }
                }
            }
            if (handler != null) {
                handler.post(task)
            } else {
                task.run()
            }
        } catch (_: Throwable) {
        }
    }

    private fun broadcastOnPackageChangedFallback(
        launcherAppsImpl: Any,
        profile: UserHandle,
        pkg: String
    ) {
        try {
            val listeners = XposedHelpers.getObjectField(launcherAppsImpl, "mListeners") as? RemoteCallbackList<*> ?: return
            val count = listeners.beginBroadcast()
            try {
                for (i in 0 until count) {
                    val listener = listeners.getBroadcastItem(i) as? IInterface ?: continue
                    try {
                        XposedHelpers.callMethod(listener, "onPackageChanged", profile, pkg)
                    } catch (_: Throwable) {
                    }
                }
            } finally {
                listeners.finishBroadcast()
            }
        } catch (_: Throwable) {
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
