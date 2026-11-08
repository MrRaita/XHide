package org.lsposed.hidexposed.core

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.UserHandle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.lsposed.hidexposed.data.ConfigStore
import java.util.ArrayList

/**
 * System-level hook targeting `com.android.server.pm.LauncherAppsService$LauncherAppsImpl`.
 *
 * Intercepts `getLauncherActivities(String callingPackage, String packageName, UserHandle user)`
 * at the IPC boundary used by all Android launchers (AOSP, One UI, Pixel Launcher, Niagara, Nova,
 * HyperOS, Lawnchair, etc.).
 *
 * Guarantees:
 * - Zero PackageManagerService lock contention (runs after PMS snapshot completes).
 * - Zero impact on boot phases (`android.ext.services`, `AppSearch`, etc. never call `LauncherApps`).
 * - Complete `try-catch (Throwable)` fail-safe so any unexpected state returns the unmodified list.
 */
object LauncherHook {
    private const val TAG = "HideXposedIcons"
    private const val TARGET_CLASS = "com.android.server.pm.LauncherAppsService\$LauncherAppsImpl"
    private const val TARGET_METHOD = "getLauncherActivities"

    fun install(classLoader: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(TARGET_CLASS, classLoader)
            if (clazz == null) {
                XposedBridge.log("[$TAG] Target class not found: $TARGET_CLASS")
                return
            }

            XposedHelpers.findAndHookMethod(
                clazz,
                TARGET_METHOD,
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
                                    if (ConfigStore.shouldHidePackage(pkgName, isModule)) {
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

            XposedBridge.log("[$TAG] Successfully installed system hook on $TARGET_CLASS#$TARGET_METHOD")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to install LauncherHook: ${t.message}")
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

    /**
     * Triggers `MyPackageMonitor.onPackageChanged` inside `LauncherAppsImpl` so connected launchers
     * (Niagara, One UI Home, Pixel Launcher, Nova, etc.) immediately refresh the affected packages
     * in real-time without needing a launcher restart.
     */
    private fun notifyLaunchersOfChanges(
        launcherAppsImpl: Any?,
        changedPackages: List<String>,
        userHandle: UserHandle?
    ) {
        if (launcherAppsImpl == null || changedPackages.isEmpty() || userHandle == null) return
        try {
            val monitor = XposedHelpers.getObjectField(launcherAppsImpl, "mPackageMonitor") ?: return
            val handler = XposedHelpers.getObjectField(launcherAppsImpl, "mCallbackHandler") as? Handler
            val task = Runnable {
                for (pkg in changedPackages) {
                    try {
                        XposedHelpers.callMethod(monitor, "onPackageChanged", pkg, userHandle)
                    } catch (_: Throwable) {
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
}
