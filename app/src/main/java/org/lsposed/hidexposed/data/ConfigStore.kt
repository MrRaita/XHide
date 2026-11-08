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

    @Volatile
    var autoHideXposedModules: Boolean = true
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
        if (blacklistedPackages.contains(uKey) || blacklistedPackages.contains(packageName)) {
            return true
        }
        if (whitelistedPackages.contains(uKey) || whitelistedPackages.contains(packageName)) {
            return false
        }
        return autoHideXposedModules && isDetectedXposedModule
    }

    fun getWhitelist(): Set<String> = whitelistedPackages.toSet()
    fun getBlacklist(): Set<String> = blacklistedPackages.toSet()

    /**
     * Parses an IPC update payload sent by the UI app to `system_server`.
     * Returns the list of affected package names so `LauncherAppsImpl` can notify launchers across all users.
     */
    fun applySystemIpcUpdate(payload: String): List<String> {
        return try {
            val json = JSONObject(payload)
            autoHideXposedModules = json.optBoolean("autoHide", true)

            val newWhitelist = mutableSetOf<String>()
            val wlArray = json.optJSONArray("whitelist")
            if (wlArray != null) {
                for (i in 0 until wlArray.length()) {
                    val pkg = wlArray.optString(i)
                    if (pkg.isNotBlank()) newWhitelist.add(pkg)
                }
            }

            val newBlacklist = mutableSetOf<String>()
            val blArray = json.optJSONArray("blacklist")
            if (blArray != null) {
                for (i in 0 until blArray.length()) {
                    val pkg = blArray.optString(i)
                    if (pkg.isNotBlank()) newBlacklist.add(pkg)
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
                for (i in 0 until changedArray.length()) {
                    val raw = changedArray.optString(i)
                    if (raw.isNotBlank()) {
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
            if (!file.exists() || !file.canRead()) return
            val json = JSONObject(file.readText())
            autoHideXposedModules = json.optBoolean("autoHide", true)

            whitelistedPackages.clear()
            val wlArray = json.optJSONArray("whitelist")
            if (wlArray != null) {
                for (i in 0 until wlArray.length()) {
                    val pkg = wlArray.optString(i)
                    if (pkg.isNotBlank()) whitelistedPackages.add(pkg)
                }
            } else {
                whitelistedPackages.add(SELF_USER0_KEY)
            }

            blacklistedPackages.clear()
            val blArray = json.optJSONArray("blacklist")
            if (blArray != null) {
                for (i in 0 until blArray.length()) {
                    val pkg = blArray.optString(i)
                    if (pkg.isNotBlank()) blacklistedPackages.add(pkg)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun saveToSystemFile() {
        try {
            val json = JSONObject().apply {
                put("autoHide", autoHideXposedModules)
                put("whitelist", JSONArray(whitelistedPackages.toList()))
                put("blacklist", JSONArray(blacklistedPackages.toList()))
            }
            File(SYSTEM_CONFIG_PATH).writeText(json.toString())
        } catch (_: Throwable) {
        }
    }

    // --- UI Client Methods ---

    fun loadUiPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        autoHideXposedModules = prefs.getBoolean("autoHide", true)
        val rawWl = prefs.getStringSet("whitelist", setOf(SELF_USER0_KEY)) ?: setOf(SELF_USER0_KEY)
        // Migrate legacy unprefixed SELF_PACKAGE to 0:SELF_PACKAGE so User 10 duplicate is hidden by default
        val wl = rawWl.map { if (it == SELF_PACKAGE) SELF_USER0_KEY else it }.toSet()
        val bl = prefs.getStringSet("blacklist", emptySet()) ?: emptySet()
        whitelistedPackages.clear()
        whitelistedPackages.addAll(wl)
        blacklistedPackages.clear()
        blacklistedPackages.addAll(bl)
    }

    fun saveUiPrefsAndSync(
        context: Context,
        autoHide: Boolean,
        whitelist: Set<String>,
        blacklist: Set<String>,
        changedPackages: List<String>
    ): Boolean {
        autoHideXposedModules = autoHide
        whitelistedPackages.clear()
        whitelistedPackages.addAll(whitelist)
        blacklistedPackages.clear()
        blacklistedPackages.addAll(blacklist)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("autoHide", autoHide)
            .putStringSet("whitelist", whitelist)
            .putStringSet("blacklist", blacklist)
            .apply()

        return try {
            val payload = JSONObject().apply {
                put("autoHide", autoHide)
                put("whitelist", JSONArray(whitelist.toList()))
                put("blacklist", JSONArray(blacklist.toList()))
                put("changed", JSONArray(changedPackages))
            }.toString()

            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
            val res = launcherApps.getActivityList("$IPC_UPDATE_PREFIX$payload", Process.myUserHandle())
            res != null && res.isNotEmpty()
        } catch (_: Throwable) {
            false
        }
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
