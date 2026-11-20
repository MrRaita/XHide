package org.lsposed.hidexposed.core

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method

internal fun invokeHookCallback(
    chain: XposedInterface.Chain,
    callback: HookCallback
): Any? = ModernModuleCore.invokeHookCallback(chain, callback)

/** API 101 backend. All types used here are present in API 101 and 102. */
internal class ModernHookBackend(
    private val module: XposedModule,
    private val handles: MutableMap<Executable, HookHandle>
) : HookBackend {
    override fun log(message: String, error: Throwable?) =
        module.log(android.util.Log.INFO, "XHide", message, error)

    override fun findClassIfExists(name: String, classLoader: ClassLoader): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    override fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
        callback: HookCallback
    ): HookHandle? {
        val method: Method = clazz.getDeclaredMethod(methodName, *parameterTypes)
        val handle = module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain -> invokeHookCallback(chain, callback) }
        return ModernHandle(method, handle).also { registerHook(it) }
    }

    override fun registerHook(handle: HookHandle) {
        handles[handle.executable] = handle
    }
    private class ModernHandle(
        method: Method,
        private val handle: XposedInterface.HookHandle
    ) : HookHandle() {
        override val executable: Executable = method

        override fun unhook() = handle.unhook()
    }

    override fun callMethod(receiver: Any, methodName: String, vararg args: Any?): Any? {
        val method = findMethod(receiver.javaClass, methodName, args)
        return module.getInvoker(method).setType(XposedInterface.Invoker.Type.ORIGIN)
            .invoke(receiver, *args)
    }

    override fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        val method = findMethod(clazz, methodName, args)
        return module.getInvoker(method).setType(XposedInterface.Invoker.Type.ORIGIN)
            .invoke(null, *args)
    }

    private fun findMethod(clazz: Class<*>, name: String, args: Array<out Any?>): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            val method = current.declaredMethods.firstOrNull { candidate ->
                candidate.name == name && candidate.parameterTypes.size == args.size &&
                    candidate.parameterTypes.indices.all { index ->
                        matches(candidate.parameterTypes[index], args[index])
                    }
            }
            if (method != null) return method
            current = current.superclass
        }
        throw NoSuchMethodException("${clazz.name}#$name(${args.size} arguments)")
    }

    private fun matches(type: Class<*>, value: Any?): Boolean {
        if (value == null) return !type.isPrimitive
        val boxed = when (type) {
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            else -> type
        }
        return boxed.isInstance(value)
    }

    override fun getObjectField(receiver: Any, fieldName: String): Any? {
        var current: Class<*>? = receiver.javaClass
        while (current != null) {
            runCatching {
                return current.getDeclaredField(fieldName).apply { isAccessible = true }.get(receiver)
            }
            current = current.superclass
        }
        throw NoSuchFieldException("${receiver.javaClass.name}.$fieldName")
    }

    override fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredField(fieldName).apply { isAccessible = true }.get(null)
            }
            current = current.superclass
        }
        throw NoSuchFieldException("${clazz.name}.$fieldName")
    }

    override fun getIntField(receiver: Any, fieldName: String): Int =
        getObjectField(receiver, fieldName) as Int

    override fun setObjectField(receiver: Any, fieldName: String, value: Any?) {
        var current: Class<*>? = receiver.javaClass
        while (current != null) {
            runCatching {
                current.getDeclaredField(fieldName).apply { isAccessible = true }.set(receiver, value)
                return
            }
            current = current.superclass
        }
        throw NoSuchFieldException("${receiver.javaClass.name}.$fieldName")
    }

    override fun newInstance(clazz: Class<*>, vararg args: Any?): Any? {
        val constructor = clazz.constructors.firstOrNull { candidate ->
            candidate.parameterTypes.size == args.size && candidate.parameterTypes.indices.all {
                matches(candidate.parameterTypes[it], args[it])
            }
        } ?: clazz.declaredConstructors.firstOrNull { candidate ->
            candidate.parameterTypes.size == args.size && candidate.parameterTypes.indices.all {
                matches(candidate.parameterTypes[it], args[it])
            }
        } ?: throw NoSuchMethodException("${clazz.name}(${args.size} arguments)")
        return module.getInvoker(constructor).setType(XposedInterface.Invoker.Type.ORIGIN)
            .newInstance(*args)
    }
}
