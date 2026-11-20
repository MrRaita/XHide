package org.lsposed.hidexposed.core

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** API 102-only dispatch and hook replacement. No reference is made from API 101 code. */
internal object ModernHotReload102 {
    fun initialize(module: ModernModule) {
        if (isSupported(module)) {
            module.log(android.util.Log.INFO, "XHide", "API 102 hook replacement is available")
        }
    }

    fun isSupported(module: XposedModule): Boolean = runCatching {
        module.apiVersion >= XposedInterface.API_102 &&
            XposedInterface.LIB_API >= XposedInterface.API_102 &&
            XposedInterface.HookHandle::class.java.methods.any {
                it.name == "replaceHook" &&
                    it.parameterTypes.contentEquals(arrayOf(XposedInterface.Hooker::class.java))
            }
    }.getOrDefault(false)

    fun onHotReloading(module: XposedModule): Boolean {
        if (!isSupported(module)) return false
        return LauncherHook.prepareForHotReload()
    }

    fun onHotReloaded(module: ModernModule, param: XposedModuleInterface.HotReloadedParam) {
        try {
            if (!param.isSystemServer || !isSupported(module)) {
                param.oldHookHandles.forEach { it.unhook() }
                return
            }

            val handles = param.oldHookHandles.associateBy { it.executable }.toMutableMap()
            val replacementBackend = object : HookBackend by ModernHookBackend(module, mutableMapOf()) {
                override fun findAndHookMethod(
                    clazz: Class<*>,
                    methodName: String,
                    vararg parameterTypes: Class<*>,
                    callback: HookCallback
                ): HookHandle {
                    val method = clazz.getDeclaredMethod(methodName, *parameterTypes)
                    val old = handles.remove(method) ?: error("No existing hook for $method")
                    val replacement = old.replaceHook { chain -> invokeHookCallback(chain, callback) }
                    return ReloadedHandle(replacement).also { registerHook(it) }
                }
            }
            LauncherHook.install(module.requireSystemServerClassLoader(), replacementBackend)
            handles.values.forEach { it.unhook() }
        } catch (t: Throwable) {
            module.log(android.util.Log.ERROR, "XHide", "Hot reload hook restoration failed", t)
            // Reinstalling with the API 101 backend is safer than leaving partial hooks behind.
            runCatching { module.reinstallAfterHotReload() }
                .onFailure { module.log(android.util.Log.ERROR, "XHide", "Fallback installation failed", it) }
        } finally {
            module.resumeAfterHotReload()
        }
    }

    private class ReloadedHandle(
        private val handle: XposedInterface.HookHandle
    ) : HookHandle() {
        override val executable = handle.executable
        override fun unhook() = handle.unhook()
    }
}
