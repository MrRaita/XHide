package org.lsposed.hidexposed.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingOpsTest {

    private val base = ConfigStore.HideRules(
        autoHide = true,
        autoHideClone = true,
        whitelist = setOf(ConfigStore.SELF_USER0_KEY),
        blacklist = emptySet()
    )

    private fun toggle(pkg: String, user: Int = 0, module: Boolean = false, hide: Boolean) =
        PendingOp.Toggle(ConfigStore.userKey(user, pkg), pkg, user, module, hide)

    @Test
    fun hidingRegularAppAddsBlacklistEntry() {
        val r = toggle("com.x", hide = true).applyTo(base)
        assertTrue(r.blacklist.contains("0:com.x"))
        assertFalse(r.whitelist.contains("0:com.x"))
    }

    @Test
    fun unhidingAutoHiddenModuleAddsWhitelistEntry() {
        val r = toggle("com.mod", module = true, hide = false).applyTo(base)
        assertTrue(r.whitelist.contains("0:com.mod"))
        assertFalse(r.blacklist.contains("0:com.mod"))
    }

    @Test
    fun hidingAutoHiddenModuleOnlyRemovesWhitelistEntry() {
        val start = base.copy(whitelist = base.whitelist + "0:com.mod")
        val r = toggle("com.mod", module = true, hide = true).applyTo(start)
        assertFalse(r.whitelist.contains("0:com.mod"))
        assertFalse(r.blacklist.contains("0:com.mod"))
    }

    @Test
    fun hidingSelfWritesBlacklistEvenAsModule() {
        val r = toggle(ConfigStore.SELF_PACKAGE, module = true, hide = true).applyTo(base)
        assertTrue(r.blacklist.contains(ConfigStore.SELF_USER0_KEY))
    }

    @Test
    fun cloneAutoHideAffectsSecondaryUserToggle() {
        val noClone = base.copy(autoHideClone = false)
        val r = toggle("com.mod", user = 10, module = true, hide = true).applyTo(noClone)
        // Not auto-hidden in user 10 when clone auto-hide is off, so hiding needs a blacklist entry.
        assertTrue(r.blacklist.contains("10:com.mod"))
    }

    @Test
    fun toggleRemovesLegacyGlobalEntry() {
        val start = base.copy(blacklist = setOf("com.x"))
        val r = toggle("com.x", hide = false).applyTo(start)
        assertFalse(r.blacklist.contains("com.x"))
    }

    @Test
    fun replayIsOrderSensitiveForAutoHide() {
        // Unhiding a module while auto-hide is on whitelists it; turning auto-hide off afterwards keeps it.
        val ops = listOf(toggle("com.mod", module = true, hide = false), PendingOp.SetAutoHide(false))
        val r = PendingOps.applyAll(base, ops)
        assertFalse(r.autoHide)
        assertTrue(r.whitelist.contains("0:com.mod"))
    }

    @Test
    fun enqueueDropsEarlierToggleForSameKey() {
        var q: List<PendingOp> = emptyList()
        q = PendingOps.enqueue(q, toggle("com.x", hide = true))!!
        q = PendingOps.enqueue(q, PendingOp.SetAutoHide(false))!!
        q = PendingOps.enqueue(q, toggle("com.x", hide = false))!!
        assertEquals(listOf(PendingOp.SetAutoHide(false), toggle("com.x", hide = false)), q)
    }

    @Test
    fun enqueueRejectsWhenFull() {
        val full = (0 until PendingOps.MAX_PENDING_OPS).map { toggle("com.p$it", hide = true) }
        assertNull(PendingOps.enqueue(full, toggle("com.new", hide = true)))
    }

    @Test
    fun changedKeysListsToggleKeysOnce() {
        val ops = listOf(toggle("com.a", hide = true), PendingOp.SetAutoHide(true), toggle("com.b", user = 10, hide = false))
        assertEquals(listOf("0:com.a", "10:com.b"), PendingOps.changedKeys(ops))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val ops = listOf(
            toggle("com.a", module = true, hide = true),
            PendingOp.SetAutoHide(false),
            PendingOp.SetAutoHideClone(true),
            toggle("com.b", user = 10, hide = false)
        )
        assertEquals(ops, PendingOps.decode(PendingOps.encode(ops)))
    }

    @Test
    fun malformedInputDecodesToEmpty() {
        assertTrue(PendingOps.decode(null).isEmpty())
        assertTrue(PendingOps.decode("not json").isEmpty())
        assertTrue(PendingOps.decode("{\"v\":2,\"ops\":[]}").isEmpty())
        assertTrue(PendingOps.decode("{\"v\":1,\"ops\":[{\"t\":\"unknown\"}]}").isEmpty())
        // Key must match user and package, and be a valid config key.
        assertTrue(
            PendingOps.decode(
                "{\"v\":1,\"ops\":[{\"t\":\"toggle\",\"key\":\"0:com.other\",\"pkg\":\"com.x\",\"user\":0,\"module\":false,\"hide\":true}]}"
            ).isEmpty()
        )
        assertTrue(
            PendingOps.decode(
                "{\"v\":1,\"ops\":[{\"t\":\"toggle\",\"key\":\"0:../etc\",\"pkg\":\"../etc\",\"user\":0,\"module\":false,\"hide\":true}]}"
            ).isEmpty()
        )
    }
}