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
        module.log(android.util.Log.INFO, "XHide", "[API 102] onHotReloading triggered; preparing hooks for hot reload")
        return LauncherHook.prepareForHotReload()
    }

    fun onHotReloaded(module: ModernModule, param: XposedModuleInterface.HotReloadedParam) {
        val handles = param.oldHookHandles.associateBy { it.executable }.toMutableMap()
        val newHandles = mutableListOf<HookHandle>()
        try {
            module.log(android.util.Log.INFO, "XHide", "[API 102] onHotReloaded triggered in process: ${param.processName} (isSystemServer=${param.isSystemServer})")
            if (!param.isSystemServer || !isSupported(module)) {
                param.oldHookHandles.forEach { it.unhook() }
                return
            }

            val replacementBackend = object : HookBackend by ModernHookBackend(module, mutableMapOf()) {
                override fun findAndHookMethod(
                    clazz: Class<*>,
                    methodName: String,
                    vararg parameterTypes: Class<*>,
                    callback: HookCallback
                ): HookHandle {
                    val method = clazz.getDeclaredMethod(methodName, *parameterTypes)
                    val old = handles[method]
                    val replacement = if (old != null) {
                        val replaced = old.replaceHook { chain -> invokeHookCallback(chain, callback) }
                        handles.remove(method)
                        replaced
                    } else {
                        module.hook(method)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept { chain -> invokeHookCallback(chain, callback) }
                    }
                    return ReloadedHandle(replacement).also {
                        registerHook(it)
                        newHandles.add(it)
                    }
                }
            }
            val classLoader = param.oldHookHandles.firstOrNull()?.executable?.declaringClass?.classLoader
                ?: module.requireSystemServerClassLoader()
            val installedAll = LauncherHook.install(classLoader, replacementBackend)
            if (!installedAll || handles.isNotEmpty()) {
                throw IllegalStateException("API 102 hot reload incomplete: installedAll=$installedAll, unmatched=${handles.size}")
            }
            module.log(android.util.Log.INFO, "XHide", "[API 102] Successfully replaced and restored hooks via replaceHook")
        } catch (t: Throwable) {
            module.log(android.util.Log.ERROR, "XHide", "Hot reload hook restoration failed", t)
            val fallbackHandles = LinkedHashMap<java.lang.reflect.Executable, HookHandle>()
            val recovered = runCatching {
                module.reinstallAfterHotReload(fallbackHandles)
            }.onFailure {
                module.log(android.util.Log.ERROR, "XHide", "Fallback installation failed", it)
            }.getOrDefault(false)

            if (recovered) {
                // Recovery succeeded: unhook the superseded partial replacements and old handles.
                newHandles.forEach { runCatching { it.unhook() } }
                handles.values.forEach { runCatching { it.unhook() } }
            } else {
                // Recovery failed: unhook newly registered fallback handles to avoid duplicate
                // leaks, and preserve existing newHandles + handles so enforcement remains active.
                fallbackHandles.values.forEach { runCatching { it.unhook() } }
                module.log(android.util.Log.WARN, "XHide", "Retaining existing hooks to preserve privacy enforcement after recovery failure")
            }
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
