package org.lsposed.hidexposed.core

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Entry point registered in `assets/xposed_init`.
 * Only activates inside `system_server` (`packageName == "android"`).
 */
class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "android") {
            LauncherHook.install(lpparam.classLoader)
        }
    }
}
