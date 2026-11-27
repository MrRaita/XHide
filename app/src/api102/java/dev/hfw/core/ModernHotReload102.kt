package dev.hfw.core

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** API 102-only dispatch and hook replacement. No reference is made from API 101 code. */
internal object ModernHotReload102 {
    fun initialize(module: ModernModule) {
        if (isSupported(module)) {
            module.log(android.util.Log.INFO, dev.hfw.Identity.TAG, "API 102 hook replacement is available")
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
        module.log(android.util.Log.INFO, dev.hfw.Identity.TAG, "[API 102] onHotReloading triggered; preparing hooks for hot reload")
        return LauncherHook.prepareForHotReload()
    }

    fun onHotReloaded(module: ModernModule, param: XposedModuleInterface.HotReloadedParam) {
        val handles = param.oldHookHandles.associateBy { it.executable }.toMutableMap()
        val newHandles = mutableListOf<HookHandle>()
        val classLoader = param.oldHookHandles.firstOrNull()?.executable?.declaringClass?.classLoader
            ?: module.requireSystemServerClassLoader()
        var reloadSucceeded = false
        try {
            module.log(android.util.Log.INFO, dev.hfw.Identity.TAG, "[API 102] onHotReloaded triggered in process: ${param.processName} (isSystemServer=${param.isSystemServer})")
            if (!param.isSystemServer || !isSupported(module)) {
                param.oldHookHandles.forEach { it.unhook() }
                return
            }

            // Phase 1: Planning & verification.
            // Collect required method hooks and verify against old handles before mutating any existing hook.
            val plannedHooks = mutableListOf<PlannedHook>()
            val stagingHandles = mutableMapOf<java.lang.reflect.Executable, HookHandle>()
            val stagingBackend = object : HookBackend by ModernHookBackend(module, stagingHandles) {
                override fun hookMethod(method: java.lang.reflect.Method, callback: HookCallback): HookHandle? {
                    val old = handles[method]
                    plannedHooks.add(PlannedHook(method, old, callback))
                    return object : HookHandle() {
                        override val executable = method
                        override fun unhook() = Unit
                    }
                }
            }

            val installedAll = LauncherHook.install(classLoader, stagingBackend)
            val hasUnmatchedOld = handles.keys.any { oldMethod ->
                plannedHooks.none { it.method == oldMethod }
            }
            val hasMissingOldForPlanned = plannedHooks.any { it.oldHandle == null }

            if (!installedAll || hasUnmatchedOld || hasMissingOldForPlanned || plannedHooks.size != param.oldHookHandles.size) {
                throw IllegalStateException(
                    "API 102 hot reload incomplete: installedAll=$installedAll, " +
                    "planned=${plannedHooks.size}, oldHandles=${param.oldHookHandles.size}, " +
                    "missingOld=$hasMissingOldForPlanned, unmatchedOld=$hasUnmatchedOld"
                )
            }

            // Phase 2: Atomic commit.
            // Replace hooks now that all targets are verified and matched.
            val activeHandles = mutableMapOf<java.lang.reflect.Executable, HookHandle>()
            for (plan in plannedHooks) {
                val oldHandle = plan.oldHandle!!
                val replacement = oldHandle.replaceHook { chain -> invokeHookCallback(chain, plan.callback) }
                val reloadedHandle = ReloadedHandle(replacement)
                newHandles.add(reloadedHandle)
                activeHandles[plan.method] = reloadedHandle
                handles.remove(plan.method)
            }
            LauncherHook.updateBackend(ModernHookBackend(module, activeHandles))
            reloadSucceeded = true
            module.log(android.util.Log.INFO, dev.hfw.Identity.TAG, "[API 102] Successfully replaced and restored hooks via replaceHook")
        } catch (t: Throwable) {
            module.log(android.util.Log.ERROR, dev.hfw.Identity.TAG, "Hot reload hook restoration failed", t)
            val fallbackHandles = LinkedHashMap<java.lang.reflect.Executable, HookHandle>()
            val recovered = runCatching {
                module.reinstallAfterHotReload(classLoader, fallbackHandles)
            }.onFailure {
                module.log(android.util.Log.ERROR, dev.hfw.Identity.TAG, "Fallback installation failed", it)
            }.getOrDefault(false)

            if (recovered) {
                // Recovery succeeded: unhook the superseded partial replacements and old handles.
                reloadSucceeded = true
                newHandles.forEach { runCatching { it.unhook() } }
                handles.values.forEach { runCatching { it.unhook() } }
            } else {
                // Recovery failed: unhook newly registered fallback handles to avoid duplicate
                // leaks, and preserve existing newHandles + handles so enforcement remains active.
                fallbackHandles.values.forEach { runCatching { it.unhook() } }
                module.log(android.util.Log.WARN, dev.hfw.Identity.TAG, "Retaining existing hooks to preserve privacy enforcement after recovery failure")
                // Resume old generation's lifecycle state so retained callbacks do not reject self-IPC
                System.getProperties().remove(LauncherHook.RETIRER_KEY)
                (System.getProperties().remove(LauncherHook.RESUMER_KEY) as? Runnable)?.run()
            }
        } finally {
            if (reloadSucceeded) {
                module.resumeAfterHotReload()
            }
        }
    }

    private class PlannedHook(
        val method: java.lang.reflect.Method,
        val oldHandle: XposedInterface.HookHandle?,
        val callback: HookCallback
    )

    private class ReloadedHandle(
        private val handle: XposedInterface.HookHandle
    ) : HookHandle() {
        override val executable = handle.executable
        override fun unhook() = handle.unhook()
    }
}
