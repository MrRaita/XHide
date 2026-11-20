package org.lsposed.hidexposed.core

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface


/** The lifecycle and methods shared by the real API 101 and 102 module entries. */
abstract class ModernModuleCore : XposedModule() {
    @Volatile
    private var systemServerClassLoader: ClassLoader? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        log(android.util.Log.INFO, TAG, "Modern module loaded in ${param.processName}; API ${apiVersion}")
        initializeOptionalLayer(this)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        systemServerClassLoader = param.classLoader
        LauncherHook.install(param.classLoader, ModernHookBackend(this, LinkedHashMap()))
    }

    protected open fun initializeOptionalLayer(module: ModernModuleCore) = Unit

    internal fun reinstallAfterHotReload(): Boolean {
        val classLoader = systemServerClassLoader
            ?: Thread.currentThread().contextClassLoader
            ?: ClassLoader.getSystemClassLoader()
        LauncherHook.install(classLoader, ModernHookBackend(this, LinkedHashMap()))
        return true
    }

    internal fun requireSystemServerClassLoader(): ClassLoader =
        systemServerClassLoader
            ?: Thread.currentThread().contextClassLoader
            ?: ClassLoader.getSystemClassLoader()

    internal fun resumeAfterHotReload() {
        LauncherHook.resumeAfterHotReload()
    }

    companion object {
        private const val TAG = "XHide"

        internal fun invokeHookCallback(
            chain: XposedInterface.Chain,
            callback: HookCallback
        ): Any? {
            val arguments = ChainArguments(chain.args, chain.executable)
            val shared = HookParam(chain.thisObject, arguments) {
                chain.proceed(arguments.toTypedArray())
            }
            callback.before(shared)
            if (!shared.hasResult) {
                val originalResult = chain.proceed(arguments.toTypedArray())
                shared.result = originalResult
            }
            callback.after(shared)
            return shared.result
        }
    }
}
