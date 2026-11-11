package org.lsposed.hidexposed

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lsposed.hidexposed.data.ConfigStore

/**
 * Locks in the precedence contract of [ConfigStore.shouldHidePackage]:
 * user-scoped blacklist > user-scoped whitelist > global blacklist > global whitelist > auto-hide.
 *
 * ConfigStore is a singleton, so every test rebuilds its full state via [setup].
 */
class ShouldHidePackageTest {

    private fun setup(
        autoHide: Boolean = true,
        autoHideClone: Boolean = true,
        whitelist: List<String> = emptyList(),
        blacklist: List<String> = emptyList()
    ) {
        val json = JSONObject().apply {
            put("autoHide", autoHide)
            put("autoHideClone", autoHideClone)
            put("whitelist", JSONArray(whitelist))
            put("blacklist", JSONArray(blacklist))
        }
        ConfigStore.applySystemIpcUpdate(json.toString())
    }

    @Test
    fun userScopedBlacklistOverridesGlobalWhitelist() {
        setup(whitelist = listOf("com.x"), blacklist = listOf("10:com.x"))
        assertTrue(ConfigStore.shouldHidePackage("com.x", 10, false))
        assertFalse(ConfigStore.shouldHidePackage("com.x", 0, false))
    }

    @Test
    fun userScopedWhitelistOverridesGlobalBlacklist() {
        setup(whitelist = listOf("10:com.x"), blacklist = listOf("com.x"))
        assertFalse(ConfigStore.shouldHidePackage("com.x", 10, false))
        assertTrue(ConfigStore.shouldHidePackage("com.x", 0, false))
    }

    @Test
    fun globalBlacklistOverridesGlobalWhitelist() {
        setup(whitelist = listOf("com.x"), blacklist = listOf("com.x"))
        assertTrue(ConfigStore.shouldHidePackage("com.x", 0, false))
    }

    @Test
    fun whitelistedModuleStaysVisibleWithAutoHide() {
        setup(autoHide = true, whitelist = listOf("0:com.module"))
        assertFalse(ConfigStore.shouldHidePackage("com.module", 0, true))
    }

    @Test
    fun globallyWhitelistedModuleStaysVisibleInAllUsers() {
        setup(autoHide = true, whitelist = listOf("com.module"))
        assertFalse(ConfigStore.shouldHidePackage("com.module", 0, true))
        assertFalse(ConfigStore.shouldHidePackage("com.module", 10, true))
    }

    @Test
    fun unlistedNonModuleIsNeverHidden() {
        setup(autoHide = true, autoHideClone = true)
        assertFalse(ConfigStore.shouldHidePackage("com.regular", 0, false))
        assertFalse(ConfigStore.shouldHidePackage("com.regular", 10, false))
    }

    @Test
    fun autoHideDisabledLeavesModulesVisible() {
        setup(autoHide = false, autoHideClone = true)
        assertFalse(ConfigStore.shouldHidePackage("com.module", 0, true))
        assertFalse(ConfigStore.shouldHidePackage("com.module", 10, true))
    }

    @Test
    fun cloneAutoHideOnlyAffectsSecondaryUsers() {
        setup(autoHide = true, autoHideClone = false)
        assertTrue(ConfigStore.shouldHidePackage("com.module", 0, true))
        assertFalse(ConfigStore.shouldHidePackage("com.module", 10, true))
    }

    @Test
    fun selfIsVisibleByDefaultEvenAsDetectedModule() {
        setup(autoHide = true)
        assertFalse(ConfigStore.shouldHidePackage(ConfigStore.SELF_PACKAGE, 0, true))
    }

    @Test
    fun selfIsHiddenWhenHideXHideIconIsEnabled() {
        setup(blacklist = listOf(ConfigStore.SELF_USER0_KEY), whitelist = listOf(ConfigStore.SELF_USER0_KEY))
        assertTrue(ConfigStore.shouldHidePackage(ConfigStore.SELF_PACKAGE, 0, false))
    }

    @Test
    fun invalidKeysAreIgnored() {
        setup(blacklist = listOf("com.x; rm -rf", "../etc", ""))
        assertFalse(ConfigStore.shouldHidePackage("com.x", 0, false))
    }
}
