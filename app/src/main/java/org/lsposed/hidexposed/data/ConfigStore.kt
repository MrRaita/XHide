package org.lsposed.hidexposed.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe configuration store shared between `system_server` (via `/data/system/hidexposed_config.json`)
 * and the companion UI application (via SharedPreferences + zero-lock LauncherApps IPC).
 *
 * Supports both per-user keys (`"userId:packageName"`, e.g. `"10:com.discord"`) and global package keys.
 */
object ConfigStore {
    const val SELF_PACKAGE = "org.lsposed.hidexposed"
    const val SELF_USER0_KEY = "0:$SELF_PACKAGE"
    const val IPC_PREFIX = "__hidexposed_ipc__:"
    const val IPC_PING = "${IPC_PREFIX}ping"
    const val IPC_GET_ALL_USERS = "${IPC_PREFIX}all_users"
    const val IPC_UPDATE_PREFIX = "${IPC_PREFIX}update:"

    private const val SYSTEM_CONFIG_PATH = "/data/system/hidexposed_config.json"
    private const val PREFS_NAME = "hidexposed_prefs"
    private const val MAX_IPC_PAYLOAD_CHARS = 512 * 1024
    private const val MAX_ENTRIES = 4096
    private val VALID_KEY_REGEX = Regex("^(?:\\d+:)?[a-zA-Z0-9._]{1,200}$")

    fun isValidConfigKey(key: String): Boolean {
        return key.length <= 220 && VALID_KEY_REGEX.matches(key)
    }

    @Volatile
    var autoHideXposedModules: Boolean = true
        private set

    @Volatile
    var autoHideCloneModules: Boolean = true
        private set

    // UI Preferences
    @Volatile
    var appLanguage: String = "SYSTEM"
        private set

    @Volatile
    var themeMode: String = "SYSTEM" // SYSTEM, DARK, LIGHT
        private set

    @Volatile
    var amoledBlack: Boolean = false
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

    private val whitelistedPackages: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply {
        add(SELF_USER0_KEY)
    }

    private val blacklistedPackages: MutableSet<String> = ConcurrentHashMap.newKeySet()

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
        val uKey = userKey(userId, packageName)
        if (blacklistedPackages.contains(uKey)) {
            return true
        }
        if (whitelistedPackages.contains(uKey)) {
            return false
        }
        if (blacklistedPackages.contains(packageName)) {
            return true
        }
        if (whitelistedPackages.contains(packageName)) {
            return false
        }
        if (!isDetectedXposedModule) {
            return false
        }
        return if (userId == 0) {
            autoHideXposedModules
        } else {
            autoHideXposedModules && autoHideCloneModules
        }
    }

    fun getWhitelist(): Set<String> = whitelistedPackages.toSet()
    fun getBlacklist(): Set<String> = blacklistedPackages.toSet()

    /**
     * Parses an IPC update payload sent by the UI app to `system_server`.
     * Returns the list of affected package names so `LauncherAppsImpl` can notify launchers across all users.
     */
    fun applySystemIpcUpdate(payload: String): List<String> {
        if (payload.length > MAX_IPC_PAYLOAD_CHARS) return emptyList()
        return try {
            val json = JSONObject(payload)
            autoHideXposedModules = json.optBoolean("autoHide", true)
            autoHideCloneModules = json.optBoolean("autoHideClone", true)

            val newWhitelist = mutableSetOf<String>()
            val wlArray = json.optJSONArray("whitelist")
            if (wlArray != null) {
                val limit = minOf(wlArray.length(), MAX_ENTRIES)
                for (i in 0 until limit) {
                    val pkg = wlArray.optString(i)
                    if (isValidConfigKey(pkg)) newWhitelist.add(pkg)
                }
            }

            val newBlacklist = mutableSetOf<String>()
            val blArray = json.optJSONArray("blacklist")
            if (blArray != null) {
                val limit = minOf(blArray.length(), MAX_ENTRIES)
                for (i in 0 until limit) {
                    val pkg = blArray.optString(i)
                    if (isValidConfigKey(pkg)) newBlacklist.add(pkg)
                }
            }

            whitelistedPackages.clear()
            whitelistedPackages.addAll(newWhitelist)
            blacklistedPackages.clear()
            blacklistedPackages.addAll(newBlacklist)

            saveToSystemFile()

            val changed = mutableSetOf<String>()
            val changedArray = json.optJSONArray("changed")
            if (changedArray != null) {
                val limit = minOf(changedArray.length(), MAX_ENTRIES)
                for (i in 0 until limit) {
                    val raw = changedArray.optString(i)
                    if (isValidConfigKey(raw)) {
                        val pkgOnly = raw.substringAfter(':', raw)
                        changed.add(pkgOnly)
                    }
                }
            }
            changed.toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun loadFromSystemFile() {
        try {
            val file = File(SYSTEM_CONFIG_PATH)
            if (!file.exists() || !file.canRead() || file.length() > MAX_IPC_PAYLOAD_CHARS) return
            val json = JSONObject(file.readText())
            autoHideXposedModules = json.optBoolean("autoHide", true)
            autoHideCloneModules = json.optBoolean("autoHideClone", true)

            whitelistedPackages.clear()
            val wlArray = json.optJSONArray("whitelist")
            if (wlArray != null) {
                val limit = minOf(wlArray.length(), MAX_ENTRIES)
                for (i in 0 until limit) {
                    val pkg = wlArray.optString(i)
                    if (isValidConfigKey(pkg)) whitelistedPackages.add(pkg)
                }
            } else {
                whitelistedPackages.add(SELF_USER0_KEY)
            }

            blacklistedPackages.clear()
            val blArray = json.optJSONArray("blacklist")
            if (blArray != null) {
                val limit = minOf(blArray.length(), MAX_ENTRIES)
                for (i in 0 until limit) {
                    val pkg = blArray.optString(i)
                    if (isValidConfigKey(pkg)) blacklistedPackages.add(pkg)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun saveToSystemFile() {
        try {
            val json = JSONObject().apply {
                put("autoHide", autoHideXposedModules)
                put("autoHideClone", autoHideCloneModules)
                put("whitelist", JSONArray(whitelistedPackages.toList()))
                put("blacklist", JSONArray(blacklistedPackages.toList()))
            }
            val targetFile = File(SYSTEM_CONFIG_PATH)
            val tmpFile = File("$SYSTEM_CONFIG_PATH.tmp")
            tmpFile.writeText(json.toString())
            tmpFile.setReadable(false, false)
            tmpFile.setReadable(true, true)
            tmpFile.setWritable(false, false)
            tmpFile.setWritable(true, true)
            if (!tmpFile.renameTo(targetFile)) {
                targetFile.writeText(json.toString())
                targetFile.setReadable(false, false)
                targetFile.setReadable(true, true)
                targetFile.setWritable(false, false)
                targetFile.setWritable(true, true)
                tmpFile.delete()
            }
        } catch (_: Throwable) {
        }
    }

    // --- UI Client Methods ---

    fun loadUiPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        autoHideXposedModules = prefs.getBoolean("autoHide", true)
        autoHideCloneModules = prefs.getBoolean("autoHideClone", true)
        appLanguage = prefs.getString("appLanguage", "SYSTEM") ?: "SYSTEM"
        themeMode = prefs.getString("themeMode", "SYSTEM") ?: "SYSTEM"
        amoledBlack = prefs.getBoolean("amoledBlack", false)
        dynamicColor = prefs.getBoolean("dynamicColor", true)
        showSystemApps = prefs.getBoolean("showSystemApps", true)
        uiScale = prefs.getFloat("uiScale", 1.0f).coerceIn(0.90f, 1.15f)

        val rawWl = prefs.getStringSet("whitelist", setOf(SELF_USER0_KEY)) ?: setOf(SELF_USER0_KEY)
        val wl = rawWl.map { if (it == SELF_PACKAGE) SELF_USER0_KEY else it }.toSet()
        val bl = prefs.getStringSet("blacklist", emptySet()) ?: emptySet()
        whitelistedPackages.clear()
        whitelistedPackages.addAll(wl)
        blacklistedPackages.clear()
        blacklistedPackages.addAll(bl)
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
        uiScale = scale.coerceIn(0.90f, 1.15f)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("appLanguage", language)
            .putString("themeMode", theme)
            .putBoolean("amoledBlack", amoled)
            .putBoolean("dynamicColor", dynamic)
            .putBoolean("showSystemApps", showSystem)
            .putFloat("uiScale", uiScale)
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
        autoHideXposedModules = autoHide
        autoHideCloneModules = autoHideClone
        whitelistedPackages.clear()
        whitelistedPackages.addAll(whitelist)
        blacklistedPackages.clear()
        blacklistedPackages.addAll(blacklist)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("autoHide", autoHide)
            .putBoolean("autoHideClone", autoHideClone)
            .putStringSet("whitelist", whitelist)
            .putStringSet("blacklist", blacklist)
            .apply()

        return try {
            val cleanChanged = changedPackages.map { it.substringAfter(':', it) }.distinct()

            val payload = JSONObject().apply {
                put("autoHide", autoHide)
                put("autoHideClone", autoHideClone)
                put("whitelist", JSONArray(whitelist.toList()))
                put("blacklist", JSONArray(blacklist.toList()))
                put("changed", JSONArray(cleanChanged))
            }.toString()

            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
            val res = launcherApps.getActivityList("$IPC_UPDATE_PREFIX$payload", Process.myUserHandle())
            res != null && res.isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    fun exportConfigJson(): String {
        return JSONObject().apply {
            put("autoHide", autoHideXposedModules)
            put("autoHideClone", autoHideCloneModules)
            put("whitelist", JSONArray(whitelistedPackages.toList()))
            put("blacklist", JSONArray(blacklistedPackages.toList()))
        }.toString(2)
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
