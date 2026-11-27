package dev.hfw.core

import java.lang.reflect.Executable
import java.lang.reflect.Method

/** Runtime-neutral operations needed by the shared launcher hook. */
internal interface HookBackend {
    fun log(message: String, error: Throwable? = null)

    fun findClassIfExists(name: String, classLoader: ClassLoader): Class<*>?

    /** The one primitive every hook goes through; hot reload plans against it. */
    fun hookMethod(method: Method, callback: HookCallback): HookHandle?

    fun callMethod(receiver: Any, methodName: String, vararg args: Any?): Any?

    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any?

    fun getObjectField(receiver: Any, fieldName: String): Any?

    fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any?

    fun getIntField(receiver: Any, fieldName: String): Int

    fun setObjectField(receiver: Any, fieldName: String, value: Any?)

    fun newInstance(clazz: Class<*>, vararg args: Any?): Any?

    fun registerHook(handle: HookHandle)
}

internal abstract class HookCallback {
    open fun before(param: HookParam) {}
    open fun after(param: HookParam) {}
}

internal class HookParam(
    val thisObject: Any?,
    val args: MutableList<Any?>,
    private val proceedOriginal: () -> Any?
) {
    var result: Any? = null
        @JvmName("assignResult")
        set(value) {
            field = value
            hasResult = true
        }
    var hasResult: Boolean = false
        private set
    private val extras = HashMap<String, Any?>()

    fun setResult(value: Any?) {
        result = value
    }

    fun setObjectExtra(key: String, value: Any?) {
        extras[key] = value
    }

    fun getObjectExtra(key: String): Any? = extras[key]

    fun proceed(): Any? = proceedOriginal()
}

internal abstract class HookHandle {
    abstract val executable: Executable
    abstract fun unhook()
}

/** Hooks the declared method; throws NoSuchMethodException when this Android version does not have it. */
internal fun HookBackend.findAndHookMethod(
    clazz: Class<*>,
    methodName: String,
    vararg parameterTypes: Class<*>,
    callback: HookCallback
): HookHandle? = hookMethod(clazz.getDeclaredMethod(methodName, *parameterTypes), callback)
