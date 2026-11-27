package dev.fadalias.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HookCallbackContractTest {
    private fun sampleExecutable() = HookCallbackContractTest::class.java.declaredMethods.first()

    @Test fun hookParamTracksResultAndExtras() {
        var proceeded = false
        val args = ChainArguments(listOf("a", 1), sampleExecutable())
        val param = HookParam("target", args) {
            proceeded = true
            "original"
        }

        assertFalse(param.hasResult)
        assertNull(param.result)
        assertEquals("original", param.proceed())
        assertTrue(proceeded)

        param.setResult("override")
        assertTrue(param.hasResult)
        assertEquals("override", param.result)

        param.setObjectExtra("k", 42)
        assertEquals(42, param.getObjectExtra("k"))
    }

    @Test fun chainArgumentsAllowSetAndRejectStructuralEdits() {
        val args = ChainArguments(listOf("x", 2), sampleExecutable())
        assertEquals("x", args[0])
        args[0] = "y"
        assertEquals("y", args[0])

        var addRejected = false
        try { args.add("z") } catch (_: UnsupportedOperationException) { addRejected = true }
        assertTrue(addRejected)

        var removeRejected = false
        try { args.removeAt(0) } catch (_: UnsupportedOperationException) { removeRejected = true }
        assertTrue(removeRejected)
    }
}
