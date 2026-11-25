package dev.fadalias.core

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal fun invokeHookCallback(
    chain: XposedInterface.Chain,
    callback: HookCallback
): Any? = ModernModuleCore.invokeHookCallback(chain, callback)

/** API 101 backend. All types used here are present in API 101 and 102. */
internal class ModernHookBackend(
    private val module: XposedModule,
    private val handles: MutableMap<Executable, HookHandle>
) : HookBackend {
    private data class MethodKey(val clazz: Class<*>, val name: String, val argTypes: List<Class<*>?>)
    private data class FieldKey(val clazz: Class<*>, val name: String)

    private val invokerCache = ConcurrentHashMap<MethodKey, XposedInterface.Invoker<*, Method>>()
    private val fieldCache = ConcurrentHashMap<FieldKey, Field>()

    private companion object {
        const val MAX_REFLECT_CACHE = 512
    }
    override fun log(message: String, error: Throwable?) =
        module.log(android.util.Log.INFO, dev.fadalias.Identity.TAG, message, error)

    override fun findClassIfExists(name: String, classLoader: ClassLoader): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    override fun hookMethod(method: Method, callback: HookCallback): HookHandle? {
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

    override fun callMethod(receiver: Any, methodName: String, vararg args: Any?): Any? =
        originInvoker(receiver.javaClass, methodName, args).invoke(receiver, *args)

    override fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? =
        originInvoker(clazz, methodName, args).invoke(null, *args)

    private fun originInvoker(clazz: Class<*>, name: String, args: Array<out Any?>): XposedInterface.Invoker<*, Method> {
        val key = MethodKey(clazz, name, args.map { it?.javaClass })
        invokerCache[key]?.let { return it }
        val invoker = module.getInvoker(findMethod(clazz, name, args)).setType(XposedInterface.Invoker.Type.ORIGIN)
        if (invokerCache.size >= MAX_REFLECT_CACHE) invokerCache.clear()
        invokerCache.putIfAbsent(key, invoker)
        return invoker
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

    private fun findField(clazz: Class<*>, fieldName: String): Field {
        val key = FieldKey(clazz, fieldName)
        fieldCache[key]?.let { return it }
        var current: Class<*>? = clazz
        while (current != null) {
            val field = runCatching { current.getDeclaredField(fieldName).apply { isAccessible = true } }.getOrNull()
            if (field != null) {
                if (fieldCache.size >= MAX_REFLECT_CACHE) fieldCache.clear()
                fieldCache.putIfAbsent(key, field)
                return field
            }
            current = current.superclass
        }
        throw NoSuchFieldException("${clazz.name}.$fieldName")
    }

    override fun getObjectField(receiver: Any, fieldName: String): Any? =
        findField(receiver.javaClass, fieldName).get(receiver)

    override fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any? =
        findField(clazz, fieldName).get(null)

    override fun getIntField(receiver: Any, fieldName: String): Int =
        getObjectField(receiver, fieldName) as Int

    override fun setObjectField(receiver: Any, fieldName: String, value: Any?) {
        findField(receiver.javaClass, fieldName).set(receiver, value)
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
