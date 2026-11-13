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
    const val IPC_GET_CONFIG = "${IPC_PREFIX}get_config"

    /** metaData key under which the hook attaches the system config to the IPC reply. */
    const val IPC_CONFIG_META_KEY = "org.lsposed.hidexposed.SYSTEM_CONFIG"

    /**
     * Location of the persisted system config. Only overridden by host-side unit tests, which
     * have no `/data/system` and need writes to succeed or fail on demand.
     */
    @Volatile
    internal var systemConfigPath: String = "/data/system/hidexposed_config.json"
    private const val PREFS_NAME = "hidexposed_prefs"
    private const val PENDING_OPS_KEY = "pendingOps"
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
    data class HideRules(
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
    private fun parseRules(json: JSONObject, strict: Boolean = false): HideRules {
        // strict (IPC updates): reject instead of truncating or dropping entries, otherwise the UI
        // would commit the full list while system_server applies a reduced one and the two diverge.
        // Lenient (persisted file): keep whatever is valid rather than losing every rule.
        fun readKeys(name: String): MutableSet<String> {
            val out = mutableSetOf<String>()
            val arr = json.optJSONArray(name) ?: return out
            if (strict) {
                if (arr.length() > MAX_ENTRIES) throw IllegalArgumentException("Too many $name entries")
            }
            val limit = minOf(arr.length(), MAX_ENTRIES)
            for (i in 0 until limit) {
                val pkg = arr.optString(i)
                if (isValidConfigKey(pkg)) {
                    out.add(pkg)
                } else if (strict) {
                    throw IllegalArgumentException("Invalid $name entry")
                }
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
            val newRules = parseRules(json, strict = true)

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

    /** system_server side: the persisted rules as JSON, returned to the companion via IPC. */
    fun systemConfigJson(): String {
        ensureSystemLoaded()
        return rulesToJson(rules).toString()
    }

    /** Parses a config received from system_server; null for missing or malformed data. */
    internal fun decodeSystemConfig(json: String?): HideRules? {
        if (json.isNullOrBlank()) return null
        if (json.length > MAX_IPC_PAYLOAD_CHARS) return null
        return try {
            parseRules(JSONObject(json))
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
            // loadFromSystemFile refuses files above this size, and the normalized snapshot can be
            // larger than the accepted payload. Reject it here so an update that could not be
            // reloaded after a restart is never reported as applied.
            if (bytes.size > MAX_IPC_PAYLOAD_CHARS) return false
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

        pendingOps = PendingOps.decode(prefs.getString(PENDING_OPS_KEY, null))
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

    // Serializes every UI-side sync (user edits and stale-rule cleanup) so the IPC step and the
    // local commit of one caller can never interleave with another caller's.
    private val uiSyncLock = Any()

    // Set once the system rules were read and adopted in this process. Older hooks do not know the
    // get_config command and answer with an empty list, in which case adoption is simply skipped.
    @Volatile
    private var adoptedSystemRules = false

    @Volatile
    private var restoredNotice = false

    private fun readSystemConfig(context: Context): HideRules? {
        return try {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
            val res = launcherApps.getActivityList(IPC_GET_CONFIG, Process.myUserHandle())
            val meta = res?.firstOrNull()?.applicationInfo?.metaData
            decodeSystemConfig(meta?.getString(IPC_CONFIG_META_KEY))
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Replaces the local rules with the rules persisted by system_server, once per process, before
     * anything is sent. The system always wins: local rules are only committed after the system
     * accepted them, so they can only differ after a reinstall, cleared app data, or another build.
     * Without this, the first sync after a reinstall would overwrite every system rule with the
     * empty local state. Queued edits are kept and replayed on top.
     */
    private fun ensureAdoptedLocked(context: Context) {
        if (adoptedSystemRules) return
        val sys = readSystemConfig(context) ?: return
        adoptedSystemRules = true
        if (sys == rules) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("autoHide", sys.autoHide)
            .putBoolean("autoHideClone", sys.autoHideClone)
            .putStringSet("whitelist", sys.whitelist)
            .putStringSet("blacklist", sys.blacklist)
            .apply()
        rules = sys
        restoredNotice = true
    }

    /** Adopts the system rules if not done yet in this process. */
    fun adoptSystemRules(context: Context) = synchronized(uiSyncLock) {
        ensureAdoptedLocked(context)
    }

    /** True once after adoption replaced local rules that differed from the system's. */
    fun consumeRestoredNotice(): Boolean {
        val v = restoredNotice
        restoredNotice = false
        return v
    }

    /** The rules last committed locally (after the system accepted them). */
    fun currentRules(): HideRules = rules

    enum class SyncStatus { OK, QUEUED, TOO_LARGE, FAILED }

    /** Outcome of a UI-side sync; [rules] are the effective rules (committed plus queued edits). */
    class SyncResult(val status: SyncStatus, val rules: HideRules)

    /**
     * Applies [op] on top of the committed rules and any queued edits, and syncs the result, all
     * under the sync lock, so concurrent callers can never overwrite each other's changes. Queued
     * edits are delivered first, preserving order. If delivery fails because the system hook is
     * unreachable, [op] is queued for later ([SyncStatus.QUEUED]); any other failure leaves the
     * queue untouched. The returned rules are always [effectiveRules] after the attempt.
     */
    fun updateRules(
        context: Context,
        changedPackages: List<String>,
        op: PendingOp,
        moduleKeys: List<String> = emptyList()
    ): SyncResult = synchronized(uiSyncLock) {
        ensureAdoptedLocked(context)
        val queued = pendingOps
        val all = PendingOps.enqueue(queued, op)
            ?: return@synchronized SyncResult(SyncStatus.TOO_LARGE, effectiveRules())
        val next = PendingOps.applyAll(rules, all)
        // A queued auto-hide change affects every detected module, so launchers must be told about
        // all module keys when it is delivered here, exactly as the flush path does.
        val queuedModuleKeys = if (PendingOps.touchesAutoHide(queued)) moduleKeys else emptyList()
        val changed = (PendingOps.changedKeys(queued) + queuedModuleKeys + changedPackages).distinct()
        // On success the delivered queue is cleared in the same SharedPreferences write as the rules.
        var status = saveUiPrefsAndSyncLocked(
            context, next.autoHide, next.autoHideClone, next.whitelist, next.blacklist, changed,
            newPending = if (queued.isNotEmpty()) emptyList() else null
        )
        if (status == SyncStatus.FAILED) {
            // Queue only when the hook is genuinely unreachable. A hook that answers the ping but
            // rejected the update has a real error the user must see, so nothing is queued.
            if (!pingSystemHook(context)) {
                setPendingLocked(context, all)
                status = SyncStatus.QUEUED
            }
        }
        SyncResult(status, effectiveRules())
    }

    /**
     * Edits made while the system hook was inactive, in the order the user made them. They are
     * replayed on top of [rules] once the hook answers again. Only written under [uiSyncLock].
     */
    @Volatile
    private var pendingOps: List<PendingOp> = emptyList()

    /** The rules as the user sees them: committed rules plus queued edits not yet delivered. */
    fun effectiveRules(): HideRules = PendingOps.applyAll(rules, pendingOps)

    fun pendingCount(): Int = pendingOps.size

    private fun setPendingLocked(context: Context, ops: List<PendingOp>) {
        pendingOps = ops
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (ops.isEmpty()) {
            editor.remove(PENDING_OPS_KEY)
        } else {
            editor.putString(PENDING_OPS_KEY, PendingOps.encode(ops))
        }
        editor.apply()
    }

    /**
     * Replaces all rules (reset, import). Never queued: the user must see the result immediately.
     * On success any queued edits are discarded, since the replacement supersedes them.
     */
    fun replaceRules(context: Context, changedPackages: List<String>, newRules: HideRules): SyncResult =
        synchronized(uiSyncLock) {
            ensureAdoptedLocked(context)
            // On success the superseded queue is cleared in the same SharedPreferences write.
            val status = saveUiPrefsAndSyncLocked(
                context, newRules.autoHide, newRules.autoHideClone, newRules.whitelist, newRules.blacklist,
                changedPackages,
                newPending = emptyList()
            )
            SyncResult(status, effectiveRules())
        }

    /**
     * Delivers queued edits in one update. OK clears the queue; TOO_LARGE also clears it because it
     * can never succeed; FAILED keeps it for the next attempt. An empty queue returns OK.
     */
    fun flushPendingOps(context: Context, changedPackages: List<String>): SyncStatus = synchronized(uiSyncLock) {
        ensureAdoptedLocked(context)
        val queued = pendingOps
        if (queued.isEmpty()) return@synchronized SyncStatus.OK
        val next = PendingOps.applyAll(rules, queued)
        val changed = (PendingOps.changedKeys(queued) + changedPackages).distinct()
        // OK clears the queue in the same SharedPreferences write as the delivered rules.
        val status = saveUiPrefsAndSyncLocked(
            context, next.autoHide, next.autoHideClone, next.whitelist, next.blacklist, changed,
            newPending = emptyList()
        )
        // TOO_LARGE wrote no rules, so the hopeless queue is dropped on its own.
        if (status == SyncStatus.TOO_LARGE) setPendingLocked(context, emptyList())
        status
    }

    /** Drops all queued edits without delivering them. */
    fun discardPendingOps(context: Context) = synchronized(uiSyncLock) {
        setPendingLocked(context, emptyList())
    }

    /**
     * Removes stale whitelist/blacklist keys and syncs the result. The current rules are re-read
     * under the sync lock, so a user edit committed while the app list was loading is never
     * overwritten by an outdated snapshot. Returns OK when nothing was stale or the sync succeeded.
     */
    fun purgeStaleRules(context: Context, isStale: (String) -> Boolean): SyncStatus = synchronized(uiSyncLock) {
        ensureAdoptedLocked(context)
        val current = rules
        val staleWl = current.whitelist.filter(isStale)
        val staleBl = current.blacklist.filter(isStale)
        if (staleWl.isEmpty() and staleBl.isEmpty()) return@synchronized SyncStatus.OK
        saveUiPrefsAndSyncLocked(
            context = context,
            autoHide = current.autoHide,
            autoHideClone = current.autoHideClone,
            whitelist = current.whitelist - staleWl.toSet(),
            blacklist = current.blacklist - staleBl.toSet(),
            changedPackages = (staleWl + staleBl).distinct()
        )
    }

    private fun saveUiPrefsAndSyncLocked(
        context: Context,
        autoHide: Boolean,
        autoHideClone: Boolean,
        whitelist: Set<String>,
        blacklist: Set<String>,
        changedPackages: List<String>,
        newPending: List<PendingOp>? = null
    ): SyncStatus {
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

        val payload = try {
            JSONObject().apply {
                put("autoHide", autoHide)
                put("autoHideClone", autoHideClone)
                put("whitelist", JSONArray(finalWl.toList()))
                put("blacklist", JSONArray(finalBl.toList()))
                put("changed", JSONArray(changedPackages.filter { isValidConfigKey(it) }.distinct()))
            }.toString()
        } catch (_: Throwable) {
            return SyncStatus.FAILED
        }

        // Reject locally, before any IPC, what system_server would refuse anyway (too many entries,
        // an oversized payload, or a persisted file above the reload limit), so the UI can show a
        // specific message instead of a generic sync failure.
        if (finalWl.size > MAX_ENTRIES) return SyncStatus.TOO_LARGE
        if (finalBl.size > MAX_ENTRIES) return SyncStatus.TOO_LARGE
        if (payload.length > MAX_IPC_PAYLOAD_CHARS) return SyncStatus.TOO_LARGE
        val fileBytes = rulesToJson(HideRules(autoHide, autoHideClone, finalWl, finalBl))
            .toString().toByteArray(Charsets.UTF_8).size
        if (fileBytes > MAX_IPC_PAYLOAD_CHARS) return SyncStatus.TOO_LARGE

        val synced = try {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val res = launcherApps?.getActivityList("$IPC_UPDATE_PREFIX$payload", Process.myUserHandle())
            !res.isNullOrEmpty()
        } catch (_: Throwable) {
            false
        }

        // Commit locally only after system_server accepted and persisted the update. Otherwise the
        // UI reverts its on-screen state while local prefs would keep rules the system never applied.
        if (!synced) return SyncStatus.FAILED
        // Rules and the pending queue go into one editor/apply so a crash can never persist the new
        // rules while keeping an already-delivered queue (which would be replayed a second time).
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("autoHide", autoHide)
            .putBoolean("autoHideClone", autoHideClone)
            .putStringSet("whitelist", finalWl)
            .putStringSet("blacklist", finalBl)
        if (newPending != null) {
            if (newPending.isEmpty()) {
                editor.remove(PENDING_OPS_KEY)
            } else {
                editor.putString(PENDING_OPS_KEY, PendingOps.encode(newPending))
            }
        }
        editor.apply()
        rules = HideRules(autoHide, autoHideClone, finalWl, finalBl)
        if (newPending != null) pendingOps = newPending
        return SyncStatus.OK
    }

    /**
     * Exports what the user sees: committed rules plus queued edits not yet delivered, so a backup
     * taken while the hook is inactive still contains every pending change.
     */
    fun exportConfigJson(): String {
        return rulesToJson(effectiveRules()).toString(2)
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
