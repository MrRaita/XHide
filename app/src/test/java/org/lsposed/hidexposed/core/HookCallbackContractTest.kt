package org.lsposed.hidexposed.core

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Executable
import java.lang.reflect.Method

class HookCallbackContractTest {
    @Test
    fun modernCallbackMutatesChainArgumentsAndSharesOriginalResult() {
        val receiver = Any()
        val chain = TestChain(receiver, mutableListOf("old"))
        val callback = object : HookCallback() {
            override fun before(param: HookParam) {
                param.args[0] = "new"
            }

            override fun after(param: HookParam) {
                assertEquals("original:new", param.result)
                param.setResult("filtered")
            }
        }

        val result = ModernModuleCore.invokeHookCallback(chain, callback)

        assertEquals("filtered", result)
        assertEquals(listOf("new"), chain.proceedArguments)
    }

    @Test
    fun beforeCanShortCircuitAndAfterCanObserveExplicitNull() {
        val chain = TestChain(Any(), mutableListOf())
        val callback = object : HookCallback() {
            override fun before(param: HookParam) {
                param.setResult(null)
            }

            override fun after(param: HookParam) {
                assert(param.hasResult)
                assertNull(param.result)
                assertSame(chain.thisObject, param.thisObject)
            }
        }

        assertNull(ModernModuleCore.invokeHookCallback(chain, callback))
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun resumeAfterHotReloadRemovesResumerWithoutExecuting() {
        var executed = false
        System.getProperties()[LauncherHook.RESUMER_KEY] = Runnable {
            executed = true
        }
        LauncherHook.resumeAfterHotReload()
        assertNull(System.getProperties()[LauncherHook.RESUMER_KEY])
        assertFalse(executed)
    }

    @Test
    fun resumeAfterHotReloadInvokesRetirer() {
        var retired = false
        System.getProperties()[LauncherHook.RETIRER_KEY] = Runnable {
            retired = true
        }
        LauncherHook.resumeAfterHotReload()
        assertNull(System.getProperties()[LauncherHook.RETIRER_KEY])
        assertTrue(retired)
    }
}

private class TestChain(
    private val receiver: Any?,
    val values: MutableList<Any?>
) : XposedInterface.Chain {
    var proceedArguments: List<Any?>? = null
    var proceedCount = 0

    override fun getExecutable(): Executable = method
    override fun getThisObject(): Any? = receiver
    override fun getArgs(): MutableList<Any?> = values
    override fun getArg(index: Int): Any? = values[index]

    override fun proceed(): Any? {
        proceedCount++
        return "original:${values[0]}"
    }

    override fun proceed(args: Array<Any?>): Any? {
        proceedCount++
        proceedArguments = args.toList()
        return "original:${args[0]}"
    }

    override fun proceedWith(thisObject: Any): Any? = proceed()
    override fun proceedWith(thisObject: Any, args: Array<Any?>): Any? = proceed(args)

    private companion object {
        val method: Method = String::class.java.getMethod("length")
    }
}
