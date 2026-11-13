package org.lsposed.hidexposed.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Thread-safe configuration store shared between `system_server` (via `/data/system/hidexposed_config.json`)
 * and the companion UI application (via SharedPreferences + zero-lock LauncherApps IPC).
 *
 * Supports both per-user keys (`"userId:packageName"`, e.g. `"10:com.discord"`) and global package keys.
 */
enum class ThemeMode(val code: String) {
    SYSTEM("SYSTEM"),
    DARK("DARK"),
    LIGHT("LIGHT");

    companion object {
        fun fromCode(code: String?): ThemeMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
    }
}

enum class AppLanguage(val code: String) {
    SYSTEM("SYSTEM"),
    EN("EN"),
    TR("TR"),
    DE("DE"),
    RU("RU"),
    ZH("ZH");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
    }
}

enum class AppSortMode(val code: String) {
    NAME_ASC("NAME_ASC"),
    NAME_DESC("NAME_DESC"),
    INSTALL_DATE_DESC("INSTALL_DATE_DESC"),
    INSTALL_DATE_ASC("INSTALL_DATE_ASC"),
    UPDATE_DATE_DESC("UPDATE_DATE_DESC");

    companion object {
        fun fromCode(code: String?): AppSortMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NAME_ASC
    }
}

object ConfigStore {
    const val SELF_PACKAGE = "org.lsposed.hidexposed"
    const val SELF_USER0_KEY = "0:$SELF_PACKAGE"
    const val IPC_PREFIX = "__hidexposed_ipc__:"
    const val IPC_PING = "${IPC_PREFIX}ping"
    const val IPC_GET_ALL_USERS = "${IPC_PREFIX}all_users"
    const val IPC_UPDATE_PREFIX = "${IPC_PREFIX}update:"

    /**
     * Location of the persisted system config. Only overridden by host-side unit tests, which
     * have no `/data/system` and need writes to succeed or fail on demand.
     */
    @Volatile
    internal var systemConfigPath: String = "/data/system/hidexposed_config.json"
    private const val PREFS_NAME = "hidexposed_prefs"
    private const val MAX_IPC_PAYLOAD_CHARS = 96 * 1024 // Safe 96 KB limit avoiding Binder TransactionTooLargeException
    private const val MAX_ENTRIES = 4096
    private val VALID_KEY_REGEX = Regex("^(?:\\d+:)?[a-zA-Z0-9._]{1,200}$")

    fun isValidConfigKey(key: String): Boolean {
        return key.length <= 220 && VALID_KEY_REGEX.matches(key)
    }

    /**
     * Immutable view of every value [shouldHidePackage] depends on. Published through a single
     * `@Volatile` reference so Binder threads never observe a mix of old and new rules
     * (e.g. a new whitelist paired with a stale blacklist or auto-hide flag).
     */
    private class HideRules(
        val autoHide: Boolean,
        val autoHideClone: Boolean,
        val whitelist: Set<String>,
        val blacklist: Set<String>
    )

    @Volatile
    private var rules = HideRules(
        autoHide = true,
        autoHideClone = true,
        whitelist = setOf(SELF_USER0_KEY),
        blacklist = emptySet()
    )

    val autoHideXposedModules: Boolean
        get() = rules.autoHide

    val autoHideCloneModules: Boolean
        get() = rules.autoHideClone

    // UI Preferences
    @Volatile
    var appLanguage: String = "SYSTEM"
        private set

    @Volatile
    var themeMode: String = "SYSTEM" // SYSTEM, DARK, LIGHT
        private set

    @Volatile
    var amoledBlack: Boolean = true
        private set

    @Volatile
    var dynamicColor: Boolean = true
        private set

    @Volatile
    var showSystemApps: Boolean = true
        private set

    @Volatile
    var uiScale: Float = 1.0f
        private set

    @Volatile
    var appSortMode: AppSortMode = AppSortMode.NAME_ASC
        private set

    @Volatile
    private var initializedInSystem = false

    fun userKey(userId: Int, packageName: String): String = "$userId:$packageName"

    fun ensureSystemLoaded() {
        if (initializedInSystem) return
        synchronized(this) {
            if (initializedInSystem) return
            loadFromSystemFile()
            initializedInSystem = true
        }
    }

    fun shouldHidePackage(packageName: String, userId: Int, isDetectedXposedModule: Boolean): Boolean {
        ensureSystemLoaded()
        // Read the snapshot once so every check below sees the same rule set.
        val r = rules
        val uKey = userKey(userId, packageName)
        if (r.blacklist.contains(uKey)) {
            return true
        }
        if (r.whitelist.contains(uKey)) {
            return false
        }
        if (r.blacklist.contains(packageName)) {
            return true
        }
        if (r.whitelist.contains(packageName)) {
            return false
        }
        if (!isDetectedXposedModule) {
            return false
        }
        return if (userId == 0) {
            r.autoHide
        } else {
            r.autoHide && r.autoHideClone
        }
    }

    fun getWhitelist(): Set<String> = rules.whitelist
    fun getBlacklist(): Set<String> = rules.blacklist

    /**
     * Parses a persisted or IPC config object into a [HideRules] snapshot, enforcing entry limits,
     * key validation, and the "Hide XHide Icon" self-whitelist rule.
     */
    private fun parseRules(json: JSONObject): HideRules {
        fun readKeys(name: String): MutableSet<String> {
            val out = mutableSetOf<String>()
            val arr = json.optJSONArray(name) ?: return out
            val limit = minOf(arr.length(), MAX_ENTRIES)
            for (i in 0 until limit) {
                val pkg = arr.optString(i)
                if (isValidConfigKey(pkg)) out.add(pkg)
            }
            return out
        }
        val newWhitelist = readKeys("whitelist")
        val newBlacklist = readKeys("blacklist")

        // Respect user choice: If explicitly blacklisted (via "Hide XHide Icon" setting), keep it blacklisted.
        // Otherwise, default XHide to whitelist so autoHide doesn't accidentally hide it.
        val isSelfBlacklisted = newBlacklist.contains(SELF_USER0_KEY) || newBlacklist.contains(SELF_PACKAGE)
        if (!isSelfBlacklisted) {
            newWhitelist.add(SELF_USER0_KEY)
        } else {
            newWhitelist.remove(SELF_USER0_KEY)
            newWhitelist.remove(SELF_PACKAGE)
        }

        return HideRules(
            autoHide = json.optBoolean("autoHide", true),
            autoHideClone = json.optBoolean("autoHideClone", true),
            whitelist = newWhitelist.toSet(),
            blacklist = newBlacklist.toSet()
        )
    }

    /**
     * Parses an IPC update payload sent by the UI app to `system_server`.
     * Returns the list of affected package names so `LauncherAppsImpl` can notify launchers across all users,
     * or null when the update was rejected (oversized or malformed payload, or the new rules could not be
     * persisted). On null the previously published rules stay in effect.
     */
    @Synchronized
    fun applySystemIpcUpdate(payload: String): List<String>? {
        if (payload.length > MAX_IPC_PAYLOAD_CHARS) return null
        return try {
            val json = JSONObject(payload)
            val newRules = parseRules(json)

            // Persist first and publish only on success, so memory, disk and the UI never diverge:
            // an unpersisted update would otherwise be reported as applied and silently revert on
            // the next system_server restart.
            if (!saveToSystemFile(newRules)) return null

            // Zero-race atomic reference swap: concurrent Binder threads never see a partial update
            rules = newRules
            initializedInSystem = true

            val changed = mutableSetOf<String>()
            val changedArray = json.optJSONArray("changed")
            if (changedArray != null) {
                val limit = minOf(changedArray.length(), MAX_ENTRIES)
                for (i in 0 until limit) {
                    val raw = changedArray.optString(i)
                    if (isValidConfigKey(raw)) {
                        changed.add(raw)
                    }
                }
            }
            changed.toList()
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadFromSystemFile() {
        try {
            val file = File(systemConfigPath)
            if (!file.exists() || !file.canRead() || file.length() > MAX_IPC_PAYLOAD_CHARS) return
            rules = parseRules(JSONObject(file.readText()))
        } catch (_: Throwable) {
        }
    }

    /** Returns true only when the snapshot was durably written; the previous file is kept otherwise. */
    private fun saveToSystemFile(snapshot: HideRules): Boolean {
        return try {
            val bytes = rulesToJson(snapshot).toString().toByteArray(Charsets.UTF_8)
            DurableFiles.writeAtomically(systemConfigPath, bytes)
            true
        } catch (_: Throwable) {
            false
        }
    }

    // --- UI Client Methods ---

    fun loadUiPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appLanguage = prefs.getString("appLanguage", "SYSTEM") ?: "SYSTEM"
        themeMode = prefs.getString("themeMode", "SYSTEM") ?: "SYSTEM"
        amoledBlack = prefs.getBoolean("amoledBlack", true)
        dynamicColor = prefs.getBoolean("dynamicColor", true)
        showSystemApps = prefs.getBoolean("showSystemApps", true)
        uiScale = prefs.getFloat("uiScale", 1.0f).coerceIn(0.90f, 1.50f)
        appSortMode = AppSortMode.fromCode(prefs.getString("appSortMode", "NAME_ASC"))

        val rawBl = prefs.getStringSet("blacklist", emptySet()) ?: emptySet()
        val isSelfBlacklisted = rawBl.contains(SELF_USER0_KEY) || rawBl.contains(SELF_PACKAGE)

        val rawWl = prefs.getStringSet("whitelist", setOf(SELF_USER0_KEY)) ?: setOf(SELF_USER0_KEY)
        val cleanWl = rawWl.map { if (it == SELF_PACKAGE) SELF_USER0_KEY else it }.toMutableSet()
        if (!isSelfBlacklisted) {
            cleanWl.add(SELF_USER0_KEY)
        } else {
            cleanWl.remove(SELF_USER0_KEY)
        }

        rules = HideRules(
            autoHide = prefs.getBoolean("autoHide", true),
            autoHideClone = prefs.getBoolean("autoHideClone", true),
            whitelist = cleanWl.toSet(),
            blacklist = rawBl.toSet()
        )
    }

    fun saveUiAppearancePrefs(
        context: Context,
        language: String = appLanguage,
        theme: String = themeMode,
        amoled: Boolean = amoledBlack,
        dynamic: Boolean = dynamicColor,
        showSystem: Boolean = showSystemApps,
        scale: Float = uiScale
    ) {
        appLanguage = language
        themeMode = theme
        amoledBlack = amoled
        dynamicColor = dynamic
        showSystemApps = showSystem
        uiScale = scale.coerceIn(0.90f, 1.50f)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("appLanguage", language)
            .putString("themeMode", theme)
            .putBoolean("amoledBlack", amoled)
            .putBoolean("dynamicColor", dynamic)
            .putBoolean("showSystemApps", showSystem)
            .putFloat("uiScale", uiScale)
            .apply()
    }

    fun saveSortMode(context: Context, mode: AppSortMode) {
        appSortMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("appSortMode", mode.code)
            .apply()
    }

    fun saveUiPrefsAndSync(
        context: Context,
        autoHide: Boolean,
        autoHideClone: Boolean = autoHideCloneModules,
        whitelist: Set<String>,
        blacklist: Set<String>,
        changedPackages: List<String>
    ): Boolean {
        val isSelfBlacklisted = blacklist.contains(SELF_USER0_KEY) || blacklist.contains(SELF_PACKAGE)
        val safeWl = whitelist.toMutableSet()
        val safeBl = blacklist.toMutableSet()
        if (!isSelfBlacklisted) {
            safeWl.add(SELF_USER0_KEY)
            safeBl.remove(SELF_USER0_KEY)
            safeBl.remove(SELF_PACKAGE)
        } else {
            safeWl.remove(SELF_USER0_KEY)
            safeWl.remove(SELF_PACKAGE)
            safeBl.add(SELF_USER0_KEY)
        }

        val finalWl = safeWl.toSet()
        val finalBl = safeBl.toSet()

        val synced = try {
            val cleanChanged = changedPackages.filter { isValidConfigKey(it) }.distinct()

            val payload = JSONObject().apply {
                put("autoHide", autoHide)
                put("autoHideClone", autoHideClone)
                put("whitelist", JSONArray(finalWl.toList()))
                put("blacklist", JSONArray(finalBl.toList()))
                put("changed", JSONArray(cleanChanged))
            }.toString()

            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val res = launcherApps?.getActivityList("$IPC_UPDATE_PREFIX$payload", Process.myUserHandle())
            res != null && res.isNotEmpty()
        } catch (_: Throwable) {
            false
        }

        // Commit locally only after system_server accepted and persisted the update. Otherwise the
        // UI reverts its on-screen state while local prefs would keep rules the system never applied.
        if (synced) {
            rules = HideRules(autoHide, autoHideClone, finalWl, finalBl)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean("autoHide", autoHide)
                .putBoolean("autoHideClone", autoHideClone)
                .putStringSet("whitelist", finalWl)
                .putStringSet("blacklist", finalBl)
                .apply()
        }
        return synced
    }

    fun exportConfigJson(): String {
        return rulesToJson(rules).toString(2)
    }

    private fun rulesToJson(snapshot: HideRules): JSONObject = JSONObject().apply {
        put("autoHide", snapshot.autoHide)
        put("autoHideClone", snapshot.autoHideClone)
        put("whitelist", JSONArray(snapshot.whitelist.toList()))
        put("blacklist", JSONArray(snapshot.blacklist.toList()))
    }

    fun pingSystemHook(context: Context): Boolean {
        return try {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
            val res = launcherApps.getActivityList(IPC_PING, Process.myUserHandle())
            res != null && res.isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }
}
