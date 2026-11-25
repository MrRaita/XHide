package dev.fadalias

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.fadalias.app.AppCatalog
import dev.fadalias.app.BackupManager
import dev.fadalias.app.Services
import dev.fadalias.app.Syncer
import dev.fadalias.app.IconCodec
import dev.fadalias.data.ActivityEntry
import dev.fadalias.data.ActivityKind
import dev.fadalias.data.AppFilter
import dev.fadalias.data.AppItem
import dev.fadalias.data.AppSort
import dev.fadalias.data.Caps
import dev.fadalias.data.IconSize
import dev.fadalias.data.LanguageMode
import dev.fadalias.data.ModuleState
import dev.fadalias.data.ThemeMode
import dev.fadalias.data.supported
import dev.fadalias.state.AppRule
import dev.fadalias.state.Op
import dev.fadalias.state.Overlay
import dev.fadalias.state.Pkg
import dev.fadalias.state.StateData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state on top of the real stores: catalog (what is installed), RuleStore (what the user chose), Syncer (what the hook says). */
class FadaliasViewModel(application: Application) : AndroidViewModel(application) {
    private val sv = Services.get(application)
    private val store = sv.store
    private val syncer = sv.syncer
    private val prefs = sv.prefs

    @Volatile private var entries: List<AppCatalog.Entry> = emptyList()
    var apps by mutableStateOf<List<AppItem>>(emptyList()); private set
    var activity by mutableStateOf(prefs.loadActivity()); private set
    var loaded by mutableStateOf(false); private set

    var query by mutableStateOf("")
    var filters by mutableStateOf(setOf<AppFilter>())
    var refreshing by mutableStateOf(false)

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    /** String resource ids to show once (snackbar). */
    val messages: SharedFlow<Int> get() = _messages

    // ---- live status ---------------------------------------------------------------
    var link by mutableStateOf(Syncer.Link.UNKNOWN); private set
    var hookVersion by mutableStateOf(0); private set
    var hooksOk by mutableStateOf(0); private set
    var hooksProblems by mutableStateOf(0); private set
    var overlayBlocked by mutableStateOf(false); private set
    var pending by mutableStateOf(0); private set
    var moduleState by mutableStateOf(ModuleState.UNKNOWN); private set

    /** The module the hook runs is older/newer than this app: a reboot (or LSPosed reload) is needed. */
    val restartNeeded: Boolean get() = link == Syncer.Link.ONLINE && hookVersion != 0 && hookVersion != BuildConfig.VERSION_CODE

    // ---- settings (persisted) -------------------------------------------------------
    private var _theme by mutableStateOf(prefs.themeMode)
    var themeMode: ThemeMode get() = _theme; set(v) { _theme = v; prefs.themeMode = v }
    private var _dynamic by mutableStateOf(prefs.dynamicColor)
    var dynamicColor: Boolean get() = _dynamic; set(v) { _dynamic = v; prefs.dynamicColor = v }
    private var _amoled by mutableStateOf(prefs.amoled)
    var amoled: Boolean get() = _amoled; set(v) { _amoled = v; prefs.amoled = v }
    private var _scale by mutableStateOf(prefs.uiScale)
    var uiScale: Float get() = _scale; set(v) { _scale = v; prefs.uiScale = v }
    private var _lang by mutableStateOf(prefs.language)
    var language: LanguageMode get() = _lang; set(v) { _lang = v; prefs.language = v }
    private var _iconSize by mutableStateOf(prefs.iconSize)
    var iconSize: IconSize get() = _iconSize; set(v) { _iconSize = v; prefs.iconSize = v }
    private var _sort by mutableStateOf(prefs.sort)
    var sort: AppSort get() = _sort; set(v) { _sort = v; prefs.sort = v }

    private var _hideProtectedWarning by mutableStateOf(prefs.hideProtectedWarning)
    /** "Don't show again" for the warning shown when a protected system app's name or icon is tapped. */
    var hideProtectedWarning: Boolean get() = _hideProtectedWarning; set(v) { _hideProtectedWarning = v; prefs.hideProtectedWarning = v }

    private var _showSystem by mutableStateOf(prefs.showSystemApps)
    /** System apps are not listed unless this is on (the switch in the sort menu). */
    var showSystemApps: Boolean get() = _showSystem; set(v) { _showSystem = v; prefs.showSystemApps = v }
    private var _sysWarn by mutableStateOf(prefs.systemWarningAccepted)
    /** True once the user confirmed the risk warning of that switch; cancelling leaves it false, so it is shown again. */
    var systemWarningAccepted: Boolean get() = _sysWarn; set(v) { _sysWarn = v; prefs.systemWarningAccepted = v }

    private var _hideSelfWarning by mutableStateOf(prefs.hideSelfWarning)
    /** "Don't show again" for the warnings about changing or hiding Fadalias itself. */
    var hideSelfWarning: Boolean get() = _hideSelfWarning; set(v) { _hideSelfWarning = v; prefs.hideSelfWarning = v }

    var autoHideModules: Boolean
        get() = store.state.autoHide.also { changes }
        set(v) { apply(listOf(Op.Auto(v, null))) }
    var autoHideClones: Boolean
        get() = store.state.autoHideClone.also { changes }
        set(v) { apply(listOf(Op.Auto(null, v))) }
    var hideIntentChooser: Boolean
        get() = store.state.hideIntentChooser.also { changes }
        set(v) { apply(listOf(Op.Auto(null, null, hideIntentChooser = v))) }
    var hideShortcuts: Boolean
        get() = store.state.hideShortcuts.also { changes }
        set(v) { apply(listOf(Op.Auto(null, null, hideShortcuts = v))) }
    var hideShortcutConfigs: Boolean
        get() = store.state.hideShortcutConfigs.also { changes }
        set(v) { apply(listOf(Op.Auto(null, null, hideShortcutConfigs = v))) }
    var hideShareTargets: Boolean
        get() = store.state.hideShareTargets.also { changes }
        set(v) { apply(listOf(Op.Auto(null, null, hideShareTargets = v))) }

    /** Reading this inside a getter makes Compose recompose when the store changes. */
    private var changes by mutableStateOf(0L)

    init {
        viewModelScope.launch {
            store.changes.collect { changes = it; rebuild(); pending = store.pendingCount }
        }
        viewModelScope.launch { syncer.link.collect { link = it } }
        viewModelScope.launch {
            syncer.ping.collect { p ->
                hookVersion = p?.hook ?: 0
                hooksOk = p?.health?.optInt("ok") ?: 0
                hooksProblems = p?.health?.optInt("failed") ?: 0 // "missing" optional hooks simply do not exist on this Android version
            }
        }
        viewModelScope.launch { syncer.hookApps.collect { rebuild(); updateModuleState() } }
        viewModelScope.launch { syncer.hookUserApps.collect { rebuild(); updateModuleState() } }
        viewModelScope.launch { syncer.overlayBlocked.collect { overlayBlocked = it; updateModuleState() } }
        viewModelScope.launch { syncer.reverted.collect { if (it > 0) _messages.tryEmit(R.string.msg_reverted) } }
        refresh()
    }

    private fun updateModuleState() {
        val anyOk = syncer.hookApps.value.values.any { it.overlayStatus == Overlay.OK } ||
            syncer.hookUserApps.value.values.any { m -> m.values.any { it.overlayStatus == Overlay.OK } }
        moduleState = when {
            overlayBlocked -> ModuleState.MISSING
            anyOk -> ModuleState.VERIFIED
            else -> ModuleState.UNKNOWN
        }
    }

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            withContext(Dispatchers.IO) {
                syncer.sync()
                val r = sv.catalog.load()
                entries = r.entries
                if (r.inventoryOk) forgetStale(r)
            }
            rebuild()
            loaded = true
            refreshing = false
        }
    }

    /** Rules of packages that are gone everywhere: the hook already forgot them, so the local copy must too. */
    private fun forgetStale(r: AppCatalog.Result) {
        val present = r.entries.map { it.pkg }.toSet()
        val known = store.state.apps.keys +
            store.state.labels.values.flatMap { it.keys } +
            store.state.icons.values.flatMap { it.keys }
        val stale = known.filter { it !in present && !sv.catalog.isInstalledHere(it) }
        if (stale.isNotEmpty()) store.apply(stale.map { Op.Forget(it) })
    }

    private fun rebuild() {
        val s: StateData = store.state
        val hook = syncer.hookApps.value
        val hookUsers = syncer.hookUserApps.value
        apps = entries.map { e ->
            val rule: AppRule? = s.iconRuleOf(e.pkg, e.userId)
            val customLabel = s.labelOf(e.pkg, e.userId)
            val want = rule?.iconRev ?: 0L
            val h = hookUsers[e.userId]?.get(e.pkg) ?: hook[e.pkg]
            AppItem(
                userId = e.userId, pkg = e.pkg, label = e.label, sizeMb = e.sizeMb,
                installedAt = e.installedAt, updatedAt = e.updatedAt, version = e.version,
                profile = e.profile, isSystem = e.isSystem, isModule = e.isModule,
                customLabel = customLabel,
                iconRev = if (Caps.icon) want else 0L,
                iconStatus = if (want > 0 && h != null && h.overlayRev == want) h.overlayStatus else if (want > 0 && h != null && h.overlayStatus != Overlay.OK) h.overlayStatus else Overlay.NONE,
                hidden = Caps.hide && s.shouldHide(e.pkg, e.userId, e.isModule),
                lookProtected = Pkg.isProtectedForLook(e.pkg),
                lookRisky = e.isSystem || Pkg.isRiskyForLook(e.pkg),
                modified = customLabel != null || (Caps.icon && want > 0) || (Caps.hide && s.hide[e.userId]?.containsKey(e.pkg) == true),
                iconToken = (if (Caps.icon) want else 0L) * 31 + (h?.overlayRev ?: 0L) * 7 + (h?.overlayStatus ?: 0),
            )
        }
    }

    // ---- derived lists ---------------------------------------------------------------
    fun filteredApps(): List<AppItem> {
        val q = query.trim()
        var list: List<AppItem> = if (showSystemApps) apps else apps.filter { !it.isSystem }
        if (q.isNotEmpty()) {
            list = list.filter {
                it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) ||
                    (it.customLabel?.contains(q, ignoreCase = true) == true)
            }
        }
        val f = filters
        if (f.isNotEmpty()) {
            list = list.filter { a ->
                (AppFilter.ICON_CHANGED in f && a.iconChanged) || (AppFilter.RENAMED in f && a.renamed) || (AppFilter.HIDDEN in f && a.hidden)
            }
        }
        val byName = compareBy<AppItem, String>(String.CASE_INSENSITIVE_ORDER) { it.displayLabel }
        return when (sort) {
            AppSort.CHANGED_FIRST -> list.sortedWith(compareByDescending<AppItem> { it.modified }.then(byName))
            AppSort.NAME_ASC -> list.sortedWith(byName)
            AppSort.NAME_DESC -> list.sortedWith(byName).reversed()
            AppSort.SIZE_ASC -> list.sortedBy { it.sizeMb }
            AppSort.SIZE_DESC -> list.sortedByDescending { it.sizeMb }
            AppSort.INSTALLED_NEWEST -> list.sortedByDescending { it.installedAt }
            AppSort.UPDATED_NEWEST -> list.sortedByDescending { it.updatedAt }
        }
    }

    fun find(key: String): AppItem? = apps.firstOrNull { it.key == key }

    fun visibleActivity(): List<ActivityEntry> = activity.filter {
        when (it.kind) {
            ActivityKind.HIDDEN, ActivityKind.UNHIDDEN -> Caps.hide
            ActivityKind.ICON_CHANGED -> Caps.icon
            else -> true
        }
    }

    // counts for the Home tiles (one number per package for name/icon, per profile entry for hidden)
    val hiddenCount: Int get() = apps.count { it.hidden }
    val renamedCount: Int get() = apps.count { it.renamed }
    val iconCount: Int get() = apps.count { it.iconChanged }

    // ---- actions ---------------------------------------------------------------------
    fun toggleFilter(filter: AppFilter) {
        filters = if (filter in filters) filters - filter else filters + filter
        filters = filters.filter { it.supported() }.toSet()
    }

    fun clearFilters() { filters = emptySet() }

    private fun log(kind: ActivityKind, item: AppItem) {
        activity = (listOf(ActivityEntry(kind, item.pkg, item.displayLabel, System.currentTimeMillis())) + activity).take(20)
        prefs.saveActivity(activity)
    }

    private fun apply(ops: List<Op>, icon: Pair<String, ByteArray>? = null): Boolean {
        val ok = store.apply(ops, icon)
        if (!ok) _messages.tryEmit(R.string.msg_save_failed) else syncer.syncAsync()
        return ok
    }

    fun setHidden(item: AppItem, hidden: Boolean) {
        val s = store.state
        val default = StateData(autoHide = s.autoHide, autoHideClone = s.autoHideClone).shouldHide(item.pkg, item.userId, item.isModule)
        if (apply(listOf(Op.Hide(item.userId, item.pkg, if (hidden == default) null else hidden)))) {
            log(if (hidden) ActivityKind.HIDDEN else ActivityKind.UNHIDDEN, item)
        }
    }

    private fun addSingleProfileNameResetOps(item: AppItem, ops: MutableList<Op>) {
        val s = store.state
        ops.add(Op.Name(item.pkg, null, item.userId))
        val legacyLabel = s.apps[item.pkg]?.label ?: return
        for (otherUser in entries.asSequence().filter { it.pkg == item.pkg && it.userId != item.userId }.map { it.userId }.distinct()) {
            if (s.labels[otherUser]?.containsKey(item.pkg) != true) {
                ops.add(Op.Name(item.pkg, legacyLabel, otherUser))
            }
        }
        ops.add(Op.Name(item.pkg, null, null))
    }

    private fun addSingleProfileIconResetOps(item: AppItem, ops: MutableList<Op>) {
        ops.add(Op.Icon(item.pkg, 0L, item.userId))
        val hasLegacyIcon = (store.state.apps[item.pkg]?.iconRev ?: 0L) > 0L
        val existsInOtherProfile = entries.any { it.pkg == item.pkg && it.userId != item.userId }
        if (hasLegacyIcon && !existsInOtherProfile) ops.add(Op.Icon(item.pkg, 0L, null))
    }

    fun setCustomLabel(item: AppItem, label: String?) {
        val clean = label?.trim()?.takeIf { it.isNotEmpty() }
        val ops = ArrayList<Op>(4)
        if (clean == null) addSingleProfileNameResetOps(item, ops)
        else ops.add(Op.Name(item.pkg, clean, item.userId))
        if (apply(ops)) log(if (clean == null) ActivityKind.RESET else ActivityKind.RENAMED, item)
    }

    fun setIconFromUri(item: AppItem, uri: Uri) {
        viewModelScope.launch {
            val png = withContext(Dispatchers.IO) {
                val bmp = IconCodec.decodeSampled({ getApplication<Application>().contentResolver.openInputStream(uri) })
                bmp?.let { IconCodec.encodePng(it, prefs.iconSize.px) }
            }
            if (png == null) { _messages.tryEmit(R.string.msg_icon_unreadable); return@launch }
            val rev = store.nextIconRev(item.pkg, item.userId)
            if (apply(listOf(Op.Icon(item.pkg, rev, item.userId)), "${item.userId}:${item.pkg}" to png)) {
                log(ActivityKind.ICON_CHANGED, item)
                watchOverlay()
            }
        }
    }

    fun resetIcon(item: AppItem) {
        val ops = ArrayList<Op>(2)
        addSingleProfileIconResetOps(item, ops)
        if (apply(ops)) log(ActivityKind.RESET, item)
    }

    /** Resets both custom name and custom icon for [item]'s profile (or all profiles when [allProfiles] is true). */
    fun resetLook(item: AppItem, allProfiles: Boolean = false) {
        val s = store.state
        val ops = ArrayList<Op>()
        if (allProfiles) {
            for ((uid, m) in s.labels) if (item.pkg in m) ops.add(Op.Name(item.pkg, null, uid))
            for ((uid, m) in s.icons) if (item.pkg in m) ops.add(Op.Icon(item.pkg, 0L, uid))
            if (s.apps[item.pkg]?.label != null) ops.add(Op.Name(item.pkg, null, null))
            if ((s.apps[item.pkg]?.iconRev ?: 0L) > 0L) ops.add(Op.Icon(item.pkg, 0L, null))
        } else {
            addSingleProfileNameResetOps(item, ops)
            addSingleProfileIconResetOps(item, ops)
        }
        if (ops.isNotEmpty() && apply(ops)) log(ActivityKind.RESET, item)
    }

    /** Resets name, icon, and hide state for [item] (single profile or all profiles via [Op.Forget]). */
    fun resetApp(item: AppItem, allProfiles: Boolean = false) {
        val ops = if (allProfiles) {
            listOf(Op.Forget(item.pkg))
        } else {
            buildList<Op> {
                add(Op.Hide(item.userId, item.pkg, null))
                addSingleProfileNameResetOps(item, this)
                addSingleProfileIconResetOps(item, this)
            }
        }
        if (apply(ops)) log(ActivityKind.RESET, item)
    }

    /** Resets all name, icon, and hide customizations for an entire profile [userId]. */
    fun resetProfile(userId: Int) {
        val s = store.state
        val ops = ArrayList<Op>()
        s.hide[userId]?.keys?.forEach { pkg -> ops.add(Op.Hide(userId, pkg, null)) }
        s.labels[userId]?.keys?.forEach { pkg -> ops.add(Op.Name(pkg, null, userId)) }
        s.icons[userId]?.keys?.forEach { pkg -> ops.add(Op.Icon(pkg, 0L, userId)) }
        if (ops.isNotEmpty()) apply(ops)
    }

    /** Asks the hook to re-apply and re-announce every customized package, rebuilding overlays too. */
    fun refreshLaunchers() {
        val pkgs = store.state.apps.keys.toList() +
            store.state.hide.values.flatMap { it.keys } +
            store.state.labels.values.flatMap { it.keys } +
            store.state.icons.values.flatMap { it.keys }
        if (apply(listOf(Op.Refresh(pkgs.distinct().take(90), true)))) { _messages.tryEmit(R.string.msg_refresh_sent); watchOverlay() }
    }

    /** Overlay building happens asynchronously in system_server: poll its ledger for a few seconds. */
    private fun watchOverlay() {
        viewModelScope.launch { repeat(6) { delay(1500); syncer.refreshOverlays() } }
    }

    fun requestSync() = syncer.syncAsync()

    fun exportBackup(uri: Uri, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    BackupManager.exportZip(store, it)
                } ?: false
            }
            onDone(ok)
        }
    }

    fun importBackup(uri: Uri, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    BackupManager.importZip(store, it)
                } ?: false
            }
            if (ok) refresh()
            onDone(ok)
        }
    }

    // ---- diagnostics -----------------------------------------------------------------
    var logLines by mutableStateOf<List<String>>(emptyList()); private set
    var logLoading by mutableStateOf(false); private set

    /** Pulls the hook's event log plus a status summary (the same data a bug report needs). */
    fun loadLog() {
        viewModelScope.launch {
            logLoading = true
            val lines = withContext(Dispatchers.IO) {
                val head = ArrayList<String>()
                head.add("app ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), android ${android.os.Build.VERSION.SDK_INT}, ${android.os.Build.MODEL}")
                head.add("link=${syncer.link.value} pending=${store.pendingCount} synced=${store.everSynced}")
                sv.client.status()?.let { head.add("status: $it") } ?: head.add("status: hook not reachable")
                head + sv.client.log()
            }
            logLines = lines
            logLoading = false
        }
    }
}
