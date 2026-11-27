package dev.hfw

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hfw.app.AppCatalog
import dev.hfw.app.BackupManager
import dev.hfw.app.Services
import dev.hfw.app.Syncer
import dev.hfw.app.IconCodec
import dev.hfw.data.ActivityEntry
import dev.hfw.data.ActivityKind
import dev.hfw.data.AppFilter
import dev.hfw.data.AppItem
import dev.hfw.data.AppProfile
import dev.hfw.data.AppSort
import dev.hfw.data.BulkAction
import dev.hfw.data.Caps
import dev.hfw.data.IconSize
import dev.hfw.data.LanguageMode
import dev.hfw.data.ModuleState
import dev.hfw.data.ThemeMode
import dev.hfw.data.supported
import dev.hfw.state.AppRule
import dev.hfw.state.Op
import dev.hfw.state.Overlay
import dev.hfw.state.Pkg
import dev.hfw.state.StateData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state on top of the real stores: catalog (what is installed), RuleStore (what the user chose), Syncer (what the hook says). */
class HfwViewModel(application: Application) : AndroidViewModel(application) {
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
    /** Profile chips on the Apps screen (union among themselves, combined with the other filters). */
    var profileFilters by mutableStateOf<Set<AppProfile>>(emptySet()); private set
    /** Profiles that actually have apps on this device, in a stable order. */
    val availableProfiles: List<AppProfile> get() = AppProfile.entries.filter { p -> apps.any { it.profile == p } }
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
    private var overlayLast = 0
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
    private var _palette by mutableStateOf(prefs.palette)
    var palette: Int get() = _palette; set(v) { _palette = v; prefs.palette = v }
    private var _customColor by mutableStateOf(prefs.customColor)
    var customColor: Int get() = _customColor; set(v) { _customColor = v; prefs.customColor = v }
    private var _reduce by mutableStateOf(prefs.reduceMotion)
    var reduceMotion: Boolean get() = _reduce; set(v) { _reduce = v; prefs.reduceMotion = v }
    private var _navStyle by mutableStateOf(prefs.navStyle)
    var navStyle: Int get() = _navStyle; set(v) { _navStyle = v; prefs.navStyle = v }
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

    private var _advanced by mutableStateOf(prefs.advancedMode)
    /** Shows the per-app hide scope (Detail) and the default scope switches (Settings). */
    var advancedMode: Boolean get() = _advanced; set(v) { _advanced = v; prefs.advancedMode = v }

    private var _showSystem by mutableStateOf(prefs.showSystemApps)
    /** System apps are not listed unless this is on (the switch in the sort menu). */
    var showSystemApps: Boolean get() = _showSystem; set(v) { _showSystem = v; prefs.showSystemApps = v }
    private var _sysWarn by mutableStateOf(prefs.systemWarningAccepted)
    /** True once the user confirmed the risk warning of that switch; cancelling leaves it false, so it is shown again. */
    var systemWarningAccepted: Boolean get() = _sysWarn; set(v) { _sysWarn = v; prefs.systemWarningAccepted = v }

    private var _hideSelfWarning by mutableStateOf(prefs.hideSelfWarning)
    /** "Don't show again" for the warnings about changing or hiding HFW itself. */
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
    var hideWidgets: Boolean
        get() = store.state.hideWidgets.also { changes }
        set(v) { apply(listOf(Op.Auto(null, null, hideWidgets = v))) }

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
        viewModelScope.launch { syncer.overlayLast.collect { overlayLast = it; updateModuleState() } }
        viewModelScope.launch { syncer.reverted.collect { if (it > 0) _messages.tryEmit(R.string.msg_reverted) } }
        refresh()
    }

    private fun updateModuleState() {
        val anyOk = syncer.hookApps.value.values.any { it.overlayStatus == Overlay.OK } ||
            syncer.hookUserApps.value.values.any { m -> m.values.any { it.overlayStatus == Overlay.OK } }
        val computed = when {
            overlayBlocked || overlayLast == 2 -> ModuleState.MISSING
            anyOk || overlayLast == 1 -> ModuleState.VERIFIED
            else -> ModuleState.UNKNOWN
        }
        // Like Rebrand: the only proof is a real overlay attempt. Remember the outcome so a reboot does not forget it.
        if (computed == ModuleState.VERIFIED) prefs.moduleVerified = true
        else if (computed == ModuleState.MISSING) prefs.moduleVerified = false
        moduleState = if (computed == ModuleState.UNKNOWN && prefs.moduleVerified) ModuleState.VERIFIED else computed
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
                hidden = Caps.hide && s.effectiveHideMask(e.pkg, e.userId, e.isModule) != 0,
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
        val pf = profileFilters
        if (pf.isNotEmpty()) list = list.filter { it.profile in pf }
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

    // ---- multi-select (Apps tab) ------------------------------------------------------
    /** Item keys (user:package) of the selected apps. Not empty = selection mode. */
    var selection by mutableStateOf<Set<String>>(emptySet()); private set
    /** Keys of the apps currently listed in the Apps tab (what "select all" selects); set by that screen. */
    var selectableKeys by mutableStateOf<List<String>>(emptyList())
    val selecting: Boolean get() = selection.isNotEmpty()
    fun toggleSelect(key: String) { selection = if (key in selection) selection - key else selection + key }
    fun selectAll(keys: Collection<String>) { selection = keys.toSet() }
    fun clearSelection() { selection = emptySet() }

    /** Applies [action] to every selected app (each in its own profile) as ONE batch, then leaves selection mode. */
    fun bulk(action: BulkAction) {
        val items = selection.mapNotNull { find(it) }
        val s = store.state
        val auto = StateData(autoHide = s.autoHide, autoHideClone = s.autoHideClone)
        val ops = ArrayList<Op>()
        val extraIcons = ArrayList<Pair<String, ByteArray>>()
        val touched = ArrayList<Pair<ActivityKind, AppItem>>()
        val resetNamePkgs = HashSet<String>()
        val resetIconPkgs = HashSet<String>()
        val usersByPkg = items.groupBy { it.pkg }.mapValues { (_, list) -> list.map { it.userId }.toSet() }
        for (it in items) {
            val before = ops.size
            when (action) {
                BulkAction.RESET_ALL -> {
                    if (Caps.hide) { ops += Op.Hide(it.userId, it.pkg, null); ops += Op.HideScope(it.userId, it.pkg, null) }
                    if (!it.lookProtected) {
                        if (it.renamed && resetNamePkgs.add(it.pkg)) addNameResetOps(it.pkg, usersByPkg[it.pkg].orEmpty(), ops)
                        if (Caps.icon && it.iconChanged && resetIconPkgs.add(it.pkg)) addIconResetOps(it.pkg, usersByPkg[it.pkg].orEmpty(), ops, extraIcons)
                    }
                }
                BulkAction.RESET_NAME -> if (!it.lookProtected && it.renamed && resetNamePkgs.add(it.pkg)) {
                    addNameResetOps(it.pkg, usersByPkg[it.pkg].orEmpty(), ops)
                }
                BulkAction.RESET_ICON -> if (Caps.icon && !it.lookProtected && it.iconChanged && resetIconPkgs.add(it.pkg)) {
                    addIconResetOps(it.pkg, usersByPkg[it.pkg].orEmpty(), ops, extraIcons)
                }
                BulkAction.HIDE, BulkAction.SHOW -> if (Caps.hide && it.pkg != Identity.PACKAGE) {
                    val hide = action == BulkAction.HIDE
                    ops += Op.Hide(it.userId, it.pkg, if (hide == auto.shouldHide(it.pkg, it.userId, it.isModule)) null else hide)
                    ops += Op.HideScope(it.userId, it.pkg, null)
                }
            }
            if (ops.size > before) {
                touched += when (action) {
                    BulkAction.HIDE -> ActivityKind.HIDDEN
                    BulkAction.SHOW -> ActivityKind.UNHIDDEN
                    else -> ActivityKind.RESET
                } to it
            }
        }
        val batch = ops.distinct()
        clearSelection()
        if (batch.isEmpty()) { _messages.tryEmit(R.string.msg_bulk_nothing); return }
        if (apply(batch, extraIcons = extraIcons)) {
            touched.take(5).forEach { (kind, item) -> log(kind, item) }
            _messages.tryEmit(R.string.msg_bulk_done)
        }
    }

    /** Other profiles of the same package, this one included (used by the detail screen's profile switcher). */
    fun siblings(item: AppItem): List<AppItem> = apps.filter { it.pkg == item.pkg }.sortedBy { it.userId }

    // ---- actions ---------------------------------------------------------------------
    fun toggleFilter(filter: AppFilter) {
        filters = if (filter in filters) filters - filter else filters + filter
        filters = filters.filter { it.supported() }.toSet()
    }

    fun toggleProfileFilter(profile: AppProfile) {
        profileFilters = if (profile in profileFilters) profileFilters - profile else profileFilters + profile
    }

    fun clearFilters() { filters = emptySet(); profileFilters = emptySet() }

    private fun log(kind: ActivityKind, item: AppItem) {
        activity = (listOf(ActivityEntry(kind, item.pkg, item.displayLabel, System.currentTimeMillis(), item.key)) + activity).take(20)
        prefs.saveActivity(activity)
    }

    private fun apply(
        ops: List<Op>,
        icon: Pair<String, ByteArray>? = null,
        extraIcons: List<Pair<String, ByteArray>> = emptyList(),
    ): Boolean {
        val ok = store.apply(ops, icon, extraIcons)
        if (!ok) _messages.tryEmit(R.string.msg_save_failed) else syncer.syncAsync()
        return ok
    }

    fun setHidden(item: AppItem, hidden: Boolean) {
        val s = store.state
        val default = StateData(autoHide = s.autoHide, autoHideClone = s.autoHideClone).shouldHide(item.pkg, item.userId, item.isModule)
        if (apply(listOf(Op.Hide(item.userId, item.pkg, if (hidden == default) null else hidden), Op.HideScope(item.userId, item.pkg, null)))) {
            log(if (hidden) ActivityKind.HIDDEN else ActivityKind.UNHIDDEN, item)
        }
    }

    fun hideScopeOf(item: AppItem): Int {
        changes
        return store.state.effectiveHideMask(item.pkg, item.userId, item.isModule)
    }

    fun hasCustomHideScope(item: AppItem): Boolean {
        changes
        return store.state.hideScopes[item.userId]?.containsKey(item.pkg) == true
    }

    fun setHideScope(item: AppItem, mask: Int?) {
        apply(listOf(Op.HideScope(item.userId, item.pkg, mask)))
    }

    fun toggleHideScope(item: AppItem, flag: Int) {
        val current = store.state.effectiveHideMask(item.pkg, item.userId, item.isModule)
        setHideScope(item, current xor flag)
    }

    private fun addNameResetOps(pkg: String, targetUsers: Set<Int>, ops: MutableList<Op>) {
        val s = store.state
        for (uid in targetUsers) ops.add(Op.Name(pkg, null, uid))
        val legacyLabel = s.apps[pkg]?.label ?: return
        for (otherUser in entries.asSequence().filter { it.pkg == pkg && it.userId !in targetUsers }.map { it.userId }.distinct()) {
            if (s.labels[otherUser]?.containsKey(pkg) != true) {
                ops.add(Op.Name(pkg, legacyLabel, otherUser))
            }
        }
        ops.add(Op.Name(pkg, null, null))
    }

    private fun addIconResetOps(
        pkg: String,
        targetUsers: Set<Int>,
        ops: MutableList<Op>,
        extraIcons: MutableList<Pair<String, ByteArray>>,
    ) {
        val s = store.state
        for (uid in targetUsers) ops.add(Op.Icon(pkg, 0L, uid))
        val hasLegacyIcon = (s.apps[pkg]?.iconRev ?: 0L) > 0L
        if (!hasLegacyIcon) return
        val otherUsers = entries.asSequence()
            .filter { it.pkg == pkg && it.userId !in targetUsers }
            .map { it.userId }
            .distinct()
            .filter { (s.icons[it]?.get(pkg)?.iconRev ?: 0L) <= 0L }
            .toList()
        if (otherUsers.isNotEmpty()) {
            val legacyBytes = store.iconBytes(pkg)?.first ?: return
            for (otherUser in otherUsers) {
                val rev = store.nextIconRev(pkg, otherUser)
                ops.add(Op.Icon(pkg, rev, otherUser))
                extraIcons.add("$otherUser:$pkg" to legacyBytes)
            }
        }
        ops.add(Op.Icon(pkg, 0L, null))
    }

    private fun targets(item: AppItem, allProfiles: Boolean): List<AppItem> = if (allProfiles) siblings(item) else listOf(item)

    fun setCustomLabel(item: AppItem, label: String?, allProfiles: Boolean = false) {
        val clean = label?.trim()?.takeIf { it.isNotEmpty() }
        val ops = ArrayList<Op>(4)
        val list = targets(item, allProfiles)
        if (clean == null) {
            addNameResetOps(item.pkg, list.map { it.userId }.toSet(), ops)
        } else {
            for (t in list) ops.add(Op.Name(t.pkg, clean, t.userId))
        }
        if (apply(ops.distinct())) log(if (clean == null) ActivityKind.RESET else ActivityKind.RENAMED, item)
    }

    fun setIconFromUri(item: AppItem, uri: Uri, allProfiles: Boolean = false) {
        viewModelScope.launch {
            val png = withContext(Dispatchers.IO) {
                val bmp = IconCodec.decodeSampled({ getApplication<Application>().contentResolver.openInputStream(uri) })
                bmp?.let { IconCodec.encodePng(it, prefs.iconSize.px) }
            }
            if (png == null) { _messages.tryEmit(R.string.msg_icon_unreadable); return@launch }
            var any = false
            for (t in targets(item, allProfiles)) {
                val rev = store.nextIconRev(t.pkg, t.userId)
                if (apply(listOf(Op.Icon(t.pkg, rev, t.userId)), "${t.userId}:${t.pkg}" to png)) any = true
            }
            if (any) {
                log(ActivityKind.ICON_CHANGED, item)
                watchOverlay()
            }
        }
    }

    fun resetIcon(item: AppItem, allProfiles: Boolean = false) {
        val ops = ArrayList<Op>(2)
        val extraIcons = ArrayList<Pair<String, ByteArray>>()
        addIconResetOps(item.pkg, targets(item, allProfiles).map { it.userId }.toSet(), ops, extraIcons)
        if (apply(ops.distinct(), extraIcons = extraIcons)) log(ActivityKind.RESET, item)
    }

    /** Resets both custom name and custom icon for [item]'s profile (or all profiles when [allProfiles] is true). */
    fun resetLook(item: AppItem, allProfiles: Boolean = false) {
        val s = store.state
        val ops = ArrayList<Op>()
        val extraIcons = ArrayList<Pair<String, ByteArray>>()
        if (allProfiles) {
            for ((uid, m) in s.labels) if (item.pkg in m) ops.add(Op.Name(item.pkg, null, uid))
            for ((uid, m) in s.icons) if (item.pkg in m) ops.add(Op.Icon(item.pkg, 0L, uid))
            if (s.apps[item.pkg]?.label != null) ops.add(Op.Name(item.pkg, null, null))
            if ((s.apps[item.pkg]?.iconRev ?: 0L) > 0L) ops.add(Op.Icon(item.pkg, 0L, null))
        } else {
            addNameResetOps(item.pkg, setOf(item.userId), ops)
            addIconResetOps(item.pkg, setOf(item.userId), ops, extraIcons)
        }
        if (ops.isNotEmpty() && apply(ops, extraIcons = extraIcons)) log(ActivityKind.RESET, item)
    }

    /** Resets name, icon, and hide state for [item] (single profile or all profiles via [Op.Forget]). */
    fun resetApp(item: AppItem, allProfiles: Boolean = false) {
        val extraIcons = ArrayList<Pair<String, ByteArray>>()
        val ops = if (allProfiles) {
            listOf(Op.Forget(item.pkg))
        } else {
            buildList<Op> {
                add(Op.Hide(item.userId, item.pkg, null))
                add(Op.HideScope(item.userId, item.pkg, null))
                addNameResetOps(item.pkg, setOf(item.userId), this)
                addIconResetOps(item.pkg, setOf(item.userId), this, extraIcons)
            }
        }
        if (apply(ops, extraIcons = extraIcons)) log(ActivityKind.RESET, item)
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
            _messages.tryEmit(if (ok) R.string.msg_backup_exported else R.string.msg_backup_export_failed)
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
            _messages.tryEmit(if (ok) R.string.msg_backup_imported else R.string.msg_backup_import_failed)
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
