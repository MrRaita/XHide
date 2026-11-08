package org.lsposed.hidexposed.core

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Binder
import android.os.Handler
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
 * Hooks:
 * 1. `getUserProfiles()` — Ensures our companion UI receives all user profiles on the device
 *    (User 0, User 10 Clone Profile, Work Profile, Dual Apps, Private Space, Secondary Users).
 * 2. `canAccessProfile(int, String)` — Grants our companion UI permission to inspect launcher
 *    activities and badged icons across all profiles without requiring system signature permissions.
 * 3. `getLauncherActivities(String, String, UserHandle)` — Filters hidden Xposed modules and
 *    per-user blacklisted apps from all launchers while keeping them visible to Xposed managers.
 */
object LauncherHook {
    private const val TAG = "HideXposedIcons"
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
            hookCanAccessProfile(clazz)
            hookGetLauncherActivities(clazz)

            XposedBridge.log("[$TAG] Successfully installed system hooks on $TARGET_CLASS")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to install LauncherHook: ${t.message}")
        }
    }

    /**
     * Hooks `ILauncherApps#getUserProfiles()` so that when `HideXposedIcons` calls
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
     * Hooks `LauncherAppsImpl#canAccessProfile(int, String)` so that `HideXposedIcons` can query
     * `getActivityList(null, userHandle)` and load badged icons for any user ID (User 10, Work,
     * Private Space, Dual Apps, etc.) without failing cross-profile checks.
     */
    private fun hookCanAccessProfile(clazz: Class<*>) {
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
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Could not hook canAccessProfile: ${t.message}")
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
                            val baseList = (XposedHelpers.callMethod(slice, "getList") as? List<*>) ?: emptyList<Any>()
                            val combined = ArrayList<Any>(baseList.filterNotNull())
                            val profiles = collectAllUserHandles(param.thisObject, userHandle)

                            for (profile in profiles) {
                                if (getUserId(profile) == userId) continue
                                val extraItems = queryLauncherActivitiesForProfile(param.thisObject, profile)
                                for (item in extraItems) {
                                    if (item != null) combined.add(item)
                                }
                            }

                            param.result = XposedHelpers.newInstance(slice.javaClass, combined)
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
                        val rawList = XposedHelpers.callMethod(slice, "getList") as? List<*> ?: return
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
                            param.result = XposedHelpers.newInstance(slice.javaClass, filteredList)
                        }
                    } catch (t: Throwable) {
                        XposedBridge.log("[$TAG] Fail-safe catch in afterHookedMethod: ${t.message}")
                    }
                }
            }
        )
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
                    val list = XposedHelpers.callMethod(slice, "getList") as? List<*>
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
                    val list = XposedHelpers.callMethod(slice, "getList") as? List<*>
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

    private fun extractActivityInfo(launcherActivityInfoInternal: Any): ActivityInfo? {
        return try {
            XposedHelpers.callMethod(launcherActivityInfoInternal, "getActivityInfo") as? ActivityInfo
        } catch (_: Throwable) {
            try {
                XposedHelpers.getObjectField(launcherActivityInfoInternal, "mActivityInfo") as? ActivityInfo
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun notifyLaunchersOfChanges(
        launcherAppsImpl: Any?,
        changedPackages: List<String>,
        fallbackUserHandle: UserHandle?
    ) {
        if (launcherAppsImpl == null || changedPackages.isEmpty()) return
        try {
            val monitor = XposedHelpers.getObjectField(launcherAppsImpl, "mPackageMonitor") ?: return
            val handler = XposedHelpers.getObjectField(launcherAppsImpl, "mCallbackHandler") as? Handler
            val profiles = collectAllUserHandles(launcherAppsImpl, fallbackUserHandle)

            val task = Runnable {
                for (profile in profiles) {
                    for (pkg in changedPackages) {
                        try {
                            XposedHelpers.callMethod(monitor, "onPackageChanged", pkg, profile)
                        } catch (_: Throwable) {
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

    private fun collectAllUserHandles(launcherAppsImpl: Any, fallbackUserHandle: UserHandle?): List<UserHandle> {
        val handles = LinkedHashSet<UserHandle>()
        if (fallbackUserHandle != null) handles.add(fallbackUserHandle)

        val token = Binder.clearCallingIdentity()
        try {
            // 1. UserManagerInternal#getUserIds() -> returns all existing user IDs on the device
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

            // 2. UserManager#getUsers() -> fallback for any custom/older ROMs
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
