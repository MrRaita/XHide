package org.lsposed.hidexposed

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lsposed.hidexposed.data.ConfigStore

class ConfigStoreCompatibilityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun redirectSystemConfig() {
        // Host machines have no /data/system; persist into a temp dir so updates can succeed.
        ConfigStore.systemConfigPath = tmp.newFolder().resolve("hidexposed_config.json").path
    }

    @Test
    fun testMultiUserKeyGeneration() {
        assertEquals("0:com.example.app", ConfigStore.userKey(0, "com.example.app"))
        assertEquals("10:com.discord", ConfigStore.userKey(10, "com.discord"))
        assertEquals("11:com.work.app", ConfigStore.userKey(11, "com.work.app"))
    }

    @Test
    fun testPerUserIsolationLogic() {
        val payload = JSONObject().apply {
            put("autoHide", true)
            put("autoHideClone", true)
            put("whitelist", JSONArray(listOf("0:org.lsposed.hidexposed", "10:com.discord")))
            put("blacklist", JSONArray(listOf("0:com.discord", "10:com.google.android.youtube")))
            put("changed", JSONArray(listOf("com.discord", "com.google.android.youtube")))
        }.toString()

        val changed = ConfigStore.applySystemIpcUpdate(payload)
        assertEquals(listOf("com.discord", "com.google.android.youtube"), changed)

        // User 0 discord should be hidden (blacklisted for user 0)
        assertTrue(ConfigStore.shouldHidePackage("com.discord", userId = 0, isDetectedXposedModule = false))

        // User 10 discord should NOT be hidden (whitelisted for user 10)
        assertFalse(ConfigStore.shouldHidePackage("com.discord", userId = 10, isDetectedXposedModule = false))

        // User 10 youtube should be hidden (blacklisted for user 10)
        assertTrue(ConfigStore.shouldHidePackage("com.google.android.youtube", userId = 10, isDetectedXposedModule = false))

        // User 0 youtube should NOT be hidden (not blacklisted for user 0)
        assertFalse(ConfigStore.shouldHidePackage("com.google.android.youtube", userId = 0, isDetectedXposedModule = false))
    }

    @Test
    fun testAutoHideCloneProfilesToggle() {
        val payload = JSONObject().apply {
            put("autoHide", true)
            put("autoHideClone", false) // Do not auto hide modules in clone profile
            put("whitelist", JSONArray())
            put("blacklist", JSONArray())
            put("changed", JSONArray())
        }.toString()

        ConfigStore.applySystemIpcUpdate(payload)

        // Xposed module in User 0 should be auto-hidden
        assertTrue(ConfigStore.shouldHidePackage("ps.reso.instaeclipse", userId = 0, isDetectedXposedModule = true))

        // Xposed module in User 10 should NOT be auto-hidden because autoHideClone is false
        assertFalse(ConfigStore.shouldHidePackage("ps.reso.instaeclipse", userId = 10, isDetectedXposedModule = true))
    }

    @Test
    fun testSafeBinderLimitRejection() {
        val hugePayload = "A".repeat(100 * 1024) // Exceeds safe 96 KB threshold
        assertNull(ConfigStore.applySystemIpcUpdate(hugePayload))
    }

    @Test
    fun malformedPayloadIsRejected() {
        assertNull(ConfigStore.applySystemIpcUpdate("{not json"))
    }

    @Test
    fun successfulUpdateIsPersisted() {
        val payload = JSONObject().apply {
            put("autoHide", false)
            put("blacklist", JSONArray(listOf("0:com.persisted")))
        }.toString()
        assertNotNull(ConfigStore.applySystemIpcUpdate(payload))
        val onDisk = JSONObject(java.io.File(ConfigStore.systemConfigPath).readText())
        assertFalse(onDisk.getBoolean("autoHide"))
        assertEquals("0:com.persisted", onDisk.getJSONArray("blacklist").getString(0))
    }

    @Test
    fun persistenceFailureKeepsPreviousRules() {
        val before = JSONObject().apply {
            put("blacklist", JSONArray(listOf("0:com.before")))
        }.toString()
        assertNotNull(ConfigStore.applySystemIpcUpdate(before))

        // Point persistence at a directory that does not exist so the atomic write fails.
        ConfigStore.systemConfigPath = tmp.root.resolve("missing/dir/config.json").path
        val after = JSONObject().apply {
            put("blacklist", JSONArray(listOf("0:com.after")))
        }.toString()
        assertNull(ConfigStore.applySystemIpcUpdate(after))

        assertTrue(ConfigStore.shouldHidePackage("com.before", 0, false))
        assertFalse(ConfigStore.shouldHidePackage("com.after", 0, false))
    }

    @Test
    fun snapshotLargerThanReloadLimitIsRejected() {
        val before = JSONObject().apply {
            put("blacklist", JSONArray(listOf("0:com.before")))
        }.toString()
        assertNotNull(ConfigStore.applySystemIpcUpdate(before))

        // 484 distinct 200-char keys fit under the IPC limit, but the normalized snapshot (flags plus
        // the self whitelist entry) exceeds it, so the file could not be reloaded after a restart.
        val keys = (0 until 484).map { i -> "p$i".padEnd(200, 'a') }
        val payload = JSONObject().apply { put("blacklist", JSONArray(keys)) }.toString()
        assertTrue(payload.length <= 96 * 1024)
        assertNull(ConfigStore.applySystemIpcUpdate(payload))

        assertTrue(ConfigStore.shouldHidePackage("com.before", 0, false))
        assertFalse(ConfigStore.shouldHidePackage(keys[0], 0, false))
    }

    @Test
    fun testValidKeyRegex() {
        assertTrue(ConfigStore.isValidConfigKey("0:org.lsposed.hidexposed"))
        assertTrue(ConfigStore.isValidConfigKey("10:com.discord"))
        assertTrue(ConfigStore.isValidConfigKey("com.example.app"))
        assertFalse(ConfigStore.isValidConfigKey("../../../etc/shadow"))
        assertFalse(ConfigStore.isValidConfigKey("invalid key with spaces"))
    }
}
