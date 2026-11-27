package dev.hfw.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateTests {
    private fun reduce(base: StateData, vararg ops: Op, now: Long = 1_000L) = Reducer.reduce(base, ops.toList(), now).data

    @Test fun nameAndIconAreStoredAndReset() {
        var s = reduce(StateData(), Op.Name("a.b", "  Hello  "), Op.Icon("a.b", 5))
        assertEquals("Hello", s.apps["a.b"]?.label)
        assertEquals(5L, s.apps["a.b"]?.iconRev)
        s = reduce(s, Op.Name("a.b", null), Op.Icon("a.b", 0), now = 2_000L)
        assertFalse(s.apps["a.b"]?.isActive ?: false)
    }

    @Test fun protectedPackagesCannotBeRenamed() {
        val s = reduce(StateData(), Op.Name("android", "x"), Op.Icon("system", 3))
        assertTrue(s.apps.isEmpty())
    }

    @Test fun systemAppsAndTheManagerItselfCanBeChanged() {
        val s = reduce(StateData(), Op.Name("com.android.settings", "S"), Op.Name(Identityless.SELF, "Mine"), Op.Icon(Identityless.SELF, 4))
        assertEquals("S", s.apps["com.android.settings"]?.label)
        assertEquals("Mine", s.apps[Identityless.SELF]?.label)
        assertEquals(4L, s.apps[Identityless.SELF]?.iconRev)
    }

    @Test fun forgetRemovesEverythingOfAPackage() {
        var s = reduce(StateData(), Op.Hide(0, "p.q", true), Op.Hide(10, "p.q", true), Op.Name("p.q", "N"))
        s = reduce(s, Op.Forget("p.q"))
        assertTrue(s.hide.isEmpty())
        assertNull(s.apps["p.q"])
    }

    @Test fun hideDecision() {
        val s = StateData(autoHide = true, autoHideClone = false)
        assertTrue(s.shouldHide("m.mod", 0, true))
        assertFalse(s.shouldHide("m.mod", 10, true))
        assertFalse(s.shouldHide("plain", 0, false))
        assertFalse(s.shouldHide(Identityless.SELF, 0, true)) // the manager is never auto-hidden
        val explicit = s.copy(hide = mapOf(0 to mapOf("m.mod" to false, "plain" to true)))
        assertFalse(explicit.shouldHide("m.mod", 0, true))
        assertTrue(explicit.shouldHide("plain", 0, false))
    }

    @Test fun hashIgnoresRuntimeFieldsButNotChoices() {
        val a = reduce(StateData(), Op.Name("a.b", "X"), now = 1L)
        val b = a.copy(rev = 99, apps = a.apps.mapValues { it.value.copy(changedAt = 777, overlayRev = 4, overlayStatus = Overlay.OK) })
        assertEquals(Canonical.hash(a), Canonical.hash(b))
        assertNotEquals(Canonical.hash(a), Canonical.hash(reduce(a, Op.Hide(0, "z.z", true))))
        assertNotEquals(Canonical.hash(a), Canonical.hash(reduce(a, Op.Name("a.b", "Y"))))
    }

    @Test fun codecRoundTrip() {
        val s = reduce(StateData(autoHide = false), Op.Hide(10, "x.y", true), Op.Name("a.b", "Ünï"), Op.Icon("a.b", 12))
        val back = StateCodec.decodeText(StateCodec.encodeText(s), strict = true)
        assertNotNull(back)
        assertEquals(Canonical.hash(s), Canonical.hash(back!!))
    }

    @Test fun opsRoundTripAndStrictDecodeRejectsGarbage() {
        val ops = listOf(Op.Hide(0, "a.b", true), Op.Hide(0, "c.d", null), Op.Name("a.b", "N"), Op.Icon("a.b", 7), Op.Auto(true, null), Op.Forget("e.f"), Op.Refresh(listOf("a.b"), true))
        val req = OpCodec.decode(OpCodec.encode(Request("id1", ops)))
        assertNotNull(req)
        assertEquals(ops, req!!.ops)
        assertNull(OpCodec.decode("""{"id":"x","ops":[{"k":"h","u":0,"p":"bad pkg!"}]}"""))
        assertNull(OpCodec.decode("""{"id":"x","ops":[{"k":"h","u":0,"p":"a..b"}]}"""))
        assertNull(OpCodec.decode("""{"id":"x","ops":[{"k":"zzz"}]}"""))
        assertNull(OpCodec.decode("not json"))
    }

    @Test fun replaceMakesHookEqualToManager() {
        val hook = reduce(StateData(), Op.Name("old.pkg", "Old"), Op.Hide(0, "gone", true))
        val manager = reduce(StateData(), Op.Name("new.pkg", "New"))
        val after = reduce(hook, Op.Replace(manager))
        assertEquals(Canonical.hash(manager), Canonical.hash(after))
    }

    @Test fun queueCoalescesPerKey() {
        val q = PendingQueue.coalesce(listOf(
            Op.Hide(0, "a.b", true), Op.Name("a.b", "1"), Op.Hide(0, "a.b", false), Op.Name("a.b", "2"),
            Op.Auto(true, null), Op.Auto(null, false),
        ))
        assertEquals(listOf(Op.Hide(0, "a.b", false), Op.Name("a.b", "2"), Op.Auto(true, false)), q)
    }

    @Test fun forgetDropsEarlierEditsButKeepsLaterOnes() {
        val q = PendingQueue.coalesce(listOf(Op.Name("a.b", "1"), Op.Hide(10, "a.b", true), Op.Forget("a.b"), Op.Name("a.b", "2"), Op.Name("x.y", "k")))
        assertEquals(listOf(Op.Forget("a.b"), Op.Name("a.b", "2"), Op.Name("x.y", "k")), q)
        // a package whose name merely ends the same must not be touched
        val q2 = PendingQueue.coalesce(listOf(Op.Hide(0, "x.a.b", true), Op.Forget("a.b")))
        assertEquals(listOf(Op.Hide(0, "x.a.b", true), Op.Forget("a.b")), q2)
    }

    @Test fun replaceSupersedesEarlierOps() {
        val r = Op.Replace(StateData())
        val q = PendingQueue.coalesce(listOf(Op.Name("a.b", "1"), r, Op.Hide(0, "c.d", true)))
        assertEquals(listOf(r, Op.Hide(0, "c.d", true)), q)
    }

    @Test fun tooManyHideEntriesAreRefused() {
        val ops = (0..Limits.MAX_PACKAGES).map { Op.Hide(0, "p$it", true) }
        var refused = false
        try { Reducer.reduce(StateData(), ops, 1L) } catch (_: IllegalArgumentException) { refused = true }
        assertTrue(refused)
    }

    @Test fun lenientDecodeSkipsCorruptEntriesWhileStrictRejects() {
        val json = """{"schema":1,"hide":{"0":{"ok.pkg":true,"bad.val":"notbool"},"1":"notobj"},"apps":{"ok.pkg":{"l":"Good"},"bad.pkg":"notobj"}}"""
        assertNull(StateCodec.decodeText(json, strict = true))
        val lenient = StateCodec.decodeText(json, strict = false)
        assertNotNull(lenient)
        assertEquals(true, lenient!!.hide[0]?.get("ok.pkg"))
        assertNull(lenient.hide[0]?.get("bad.val"))
        assertEquals("Good", lenient.apps["ok.pkg"]?.label)
        assertNull(lenient.apps["bad.pkg"])
    }

    @Test fun diffDetectsHideAutoNameAndIconChanges() {
        val a = reduce(StateData(autoHide = true), Op.Hide(0, "p1", true), Op.Name("p2", "Old"), Op.Icon("p3", 1))
        val b = reduce(a, Op.Hide(0, "p1", false), Op.Auto(false, null), Op.Name("p2", "New"), Op.Icon("p3", 2))
        val d = Diff.between(a, b)
        assertFalse(d.isEmpty)
        assertEquals(setOf(0 to "p1"), d.hide)
        assertTrue(d.autoChanged)
        assertEquals(setOf("p2"), d.names)
        assertEquals(setOf("p3"), d.icons)
        assertTrue(Diff.between(b, b).isEmpty)
    }

    @Test fun opCodecEnforcesPayloadAndOpLimits() {
        val tooMany = (0..Limits.MAX_OPS).map { Op.Forget("p.$it") }
        assertNull(OpCodec.decode(OpCodec.encode(Request("r1", tooMany))))

        val oversized = "x".repeat(Limits.MAX_PAYLOAD_CHARS + 1)
        assertNull(OpCodec.decode(oversized))
    }

    @Test fun queueCoalescesRefreshOps() {
        val q = PendingQueue.coalesce(listOf(
            Op.Refresh(listOf("a.b", "c.d"), force = false),
            Op.Name("x.y", "X"),
            Op.Refresh(listOf("c.d", "e.f"), force = true),
        ))
        assertEquals(listOf(Op.Name("x.y", "X"), Op.Refresh(listOf("a.b", "c.d", "e.f"), force = true)), q)
    }

    @Test fun perProfileLabelsAreIsolatedAndRoundTrip() {
        var s = reduce(StateData(), Op.Name("com.app", "WorkApp", user = 10))
        assertNull(s.labelOf("com.app", 0))
        assertEquals("WorkApp", s.labelOf("com.app", 10))

        s = reduce(s, Op.Name("com.app", "MainApp", user = 0))
        assertEquals("MainApp", s.labelOf("com.app", 0))
        assertEquals("WorkApp", s.labelOf("com.app", 10))

        val decoded = StateCodec.decodeText(StateCodec.encodeText(s), strict = true)
        assertNotNull(decoded)
        assertEquals(Canonical.hash(s), Canonical.hash(decoded!!))
        assertEquals("WorkApp", decoded.labelOf("com.app", 10))

        val d = Diff.between(s, reduce(s, Op.Name("com.app", null, user = 10)))
        assertEquals(setOf(10 to "com.app"), d.nameUsers)
        assertTrue(d.names.isEmpty())
    }

    @Test fun perProfileIconsAreIsolatedAndRoundTrip() {
        var s = reduce(StateData(), Op.Icon("com.app", 101L, user = 10))
        assertNull(s.icons[0]?.get("com.app"))
        assertEquals(101L, s.iconRuleOf("com.app", 10)?.iconRev)

        s = reduce(s, Op.Icon("com.app", 202L, user = 0))
        assertEquals(202L, s.iconRuleOf("com.app", 0)?.iconRev)
        assertEquals(101L, s.iconRuleOf("com.app", 10)?.iconRev)

        val decoded = StateCodec.decodeText(StateCodec.encodeText(s), strict = true)
        assertNotNull(decoded)
        assertEquals(Canonical.hash(s), Canonical.hash(decoded!!))
        assertEquals(101L, decoded.iconRuleOf("com.app", 10)?.iconRev)

        val d = Diff.between(s, reduce(s, Op.Icon("com.app", 0L, user = 10)))
        assertEquals(setOf(10 to "com.app"), d.iconUsers)
        assertTrue(d.icons.isEmpty())

        // Resetting look for user 10 leaves user 0 intact
        val afterResetU10 = reduce(
            reduce(s, Op.Name("com.app", "U0", user = 0), Op.Name("com.app", "U10", user = 10)),
            Op.Name("com.app", null, user = 10),
            Op.Icon("com.app", 0L, user = 10),
        )
        assertEquals("U0", afterResetU10.labelOf("com.app", 0))
        assertEquals(202L, afterResetU10.iconRuleOf("com.app", 0)?.iconRev)
        assertNull(afterResetU10.labelOf("com.app", 10))
        assertFalse(afterResetU10.icons[10]?.get("com.app")?.isActive == true)
    }

    @Test fun hideScopeTargetsIndependentlyAndRoundTrips() {
        // Visible in launcher, hidden only in Open-with and Share
        val mask = HideScope.INTENT_CHOOSER or HideScope.SHARE_TARGETS
        var s = reduce(StateData(hideIntentChooser = false), Op.HideScope(0, "com.beemdevelopment.aegis", mask))
        assertFalse(s.shouldHideScope("com.beemdevelopment.aegis", 0, false, HideScope.LAUNCHER))
        assertFalse(s.shouldHideScope("com.beemdevelopment.aegis", 0, false, HideScope.SHORTCUTS))
        assertTrue(s.shouldHideScope("com.beemdevelopment.aegis", 0, false, HideScope.INTENT_CHOOSER))
        assertTrue(s.shouldHideScope("com.beemdevelopment.aegis", 0, false, HideScope.SHARE_TARGETS))
        assertFalse("com.beemdevelopment.aegis" in s.hiddenFor(0, emptySet()))
        assertEquals(mask, s.effectiveHideMask("com.beemdevelopment.aegis", 0, false))

        // Per-profile isolation: user 10 stays untouched
        assertEquals(0, s.effectiveHideMask("com.beemdevelopment.aegis", 10, false))

        // OpCodec + StateCodec + Canonical.hash round-trip
        val req = Request("r1", listOf(Op.HideScope(0, "com.beemdevelopment.aegis", mask)))
        val decodedReq = OpCodec.decode(OpCodec.encode(req))
        assertNotNull(decodedReq)
        assertEquals(Op.HideScope(0, "com.beemdevelopment.aegis", mask), decodedReq!!.ops.single())

        val decodedState = StateCodec.decodeText(StateCodec.encodeText(s), strict = true)
        assertNotNull(decodedState)
        assertEquals(Canonical.hash(s), Canonical.hash(decodedState!!))
        assertEquals(mask, decodedState.effectiveHideMask("com.beemdevelopment.aegis", 0, false))

        // PendingQueue coalescing + Op.Forget cleanup
        val coalesced = PendingQueue.coalesce(listOf(
            Op.HideScope(0, "com.beemdevelopment.aegis", HideScope.LAUNCHER),
            Op.HideScope(0, "com.beemdevelopment.aegis", mask),
            Op.HideScope(0, "com.other", HideScope.ALL),
            Op.Forget("com.other"),
        ))
        assertEquals(listOf(Op.HideScope(0, "com.beemdevelopment.aegis", mask), Op.Forget("com.other")), coalesced)

        // Clearing HideScope falls back to normal Hide state
        val cleared = reduce(s, Op.HideScope(0, "com.beemdevelopment.aegis", null))
        assertEquals(0, cleared.effectiveHideMask("com.beemdevelopment.aegis", 0, false))
        val diff = Diff.between(s, cleared)
        assertEquals(setOf(0 to "com.beemdevelopment.aegis"), diff.hide)
    }

    private object Identityless { const val SELF = "dev.hfw" }
}
