package dev.hfw.core

import io.github.libxposed.api.XposedModuleInterface

/** Production entry. On API 102 it supports hot reload; on API 101 it runs with the standard core. */
class ModernModule : ModernModuleCore() {
    override fun initializeOptionalLayer(module: ModernModuleCore) {
        if (ModernHotReload102.isSupported(this)) {
            ModernHotReload102.initialize(this)
        } else {
            log(android.util.Log.INFO, dev.hfw.Identity.TAG, "Running on LibXposed API 101 (hot reload disabled)")
        }
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean =
        ModernHotReload102.onHotReloading(this)

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        ModernHotReload102.onHotReloaded(this, param)
    }
}
