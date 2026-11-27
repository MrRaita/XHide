package dev.hfw.state

/** Applies edits to a [StateData]. Pure: no files, no Android, so the manager and the hook share it and tests run on the JVM. */
object Reducer {
    class Result(
        val data: StateData,
        /** Packages named by Refresh ops. */
        val refresh: Set<String>,
        val forceRefresh: Boolean,
        /** Packages whose overlay must be unregistered (they no longer want an icon but have one). */
        val overlayRemove: Set<String>,
        /** (user, package) pairs whose per-profile overlay must be unregistered. */
        val overlayRemoveUsers: Set<Pair<Int, String>> = emptySet(),
    )

    /** Throws IllegalArgumentException when the result would exceed a limit. */
    fun reduce(base: StateData, ops: List<Op>, now: Long): Result {
        val hide = LinkedHashMap<Int, MutableMap<String, Boolean>>()
        for ((u, m) in base.hide) hide[u] = LinkedHashMap(m)
        val hideScopes = LinkedHashMap<Int, MutableMap<String, Int>>()
        for ((u, m) in base.hideScopes) hideScopes[u] = LinkedHashMap(m)
        val labels = LinkedHashMap<Int, MutableMap<String, String>>()
        for ((u, m) in base.labels) labels[u] = LinkedHashMap(m)
        val icons = LinkedHashMap<Int, MutableMap<String, AppRule>>()
        for ((u, m) in base.icons) icons[u] = LinkedHashMap(m)
        val apps = LinkedHashMap(base.apps)
        var autoHide = base.autoHide
        var autoHideClone = base.autoHideClone
        var hideIntentChooser = base.hideIntentChooser
        var hideShortcuts = base.hideShortcuts
        var hideShortcutConfigs = base.hideShortcutConfigs
        var hideShareTargets = base.hideShareTargets
        var hideWidgets = base.hideWidgets
        val refresh = LinkedHashSet<String>()
        var force = false

        fun touch(pkg: String, change: (AppRule) -> AppRule) {
            val old = apps[pkg] ?: AppRule()
            val next = change(old)
            if (next == old) return
            if (next == AppRule()) apps.remove(pkg) else apps[pkg] = next
        }

        fun touchUserIcon(user: Int, pkg: String, change: (AppRule) -> AppRule) {
            val m = icons.getOrPut(user) { LinkedHashMap() }
            val old = m[pkg] ?: AppRule()
            val next = change(old)
            if (next != old) {
                if (next == AppRule()) m.remove(pkg) else m[pkg] = next
            }
            if (m.isEmpty()) icons.remove(user)
        }

        for (op in ops) when (op) {
            is Op.Hide -> {
                val m = hide.getOrPut(op.user) { LinkedHashMap() }
                if (op.hide == null) m.remove(op.pkg) else m[op.pkg] = op.hide
                if (m.isEmpty()) hide.remove(op.user)
            }
            is Op.HideScope -> {
                val m = hideScopes.getOrPut(op.user) { LinkedHashMap() }
                if (op.mask == null) m.remove(op.pkg) else m[op.pkg] = op.mask and HideScope.ALL
                if (m.isEmpty()) hideScopes.remove(op.user)
            }
            is Op.Name -> if (!Pkg.isProtectedForLook(op.pkg)) {
                val label = op.label?.trim()?.takeIf { it.isNotEmpty() }?.take(Limits.MAX_LABEL)
                if (op.user != null) {
                    val m = labels.getOrPut(op.user) { LinkedHashMap() }
                    val prev = m[op.pkg]
                    if (label == null) m.remove(op.pkg) else m[op.pkg] = label
                    if (m.isEmpty()) labels.remove(op.user)
                    if (prev != label) touch(op.pkg) { it.copy(changedAt = now) }
                } else {
                    touch(op.pkg) { if (it.label == label) it else it.copy(label = label, changedAt = now) }
                }
            }
            is Op.Icon -> if (!Pkg.isProtectedForLook(op.pkg)) {
                if (op.user != null) {
                    touchUserIcon(op.user, op.pkg) { if (it.iconRev == op.rev) it else it.copy(iconRev = op.rev, changedAt = now) }
                } else {
                    touch(op.pkg) { if (it.iconRev == op.rev) it else it.copy(iconRev = op.rev, changedAt = now) }
                }
            }
            is Op.Auto -> {
                op.autoHide?.let { autoHide = it }
                op.autoHideClone?.let { autoHideClone = it }
                op.hideIntentChooser?.let { hideIntentChooser = it }
                op.hideShortcuts?.let { hideShortcuts = it }
                op.hideShortcutConfigs?.let { hideShortcutConfigs = it }
                op.hideShareTargets?.let { hideShareTargets = it }
                op.hideWidgets?.let { hideWidgets = it }
            }
            is Op.Forget -> {
                for (m in hide.values) m.remove(op.pkg)
                hide.values.removeAll { it.isEmpty() }
                for (m in hideScopes.values) m.remove(op.pkg)
                hideScopes.values.removeAll { it.isEmpty() }
                for (m in labels.values) m.remove(op.pkg)
                labels.values.removeAll { it.isEmpty() }
                for (m in icons.values) m.remove(op.pkg)
                icons.values.removeAll { it.isEmpty() }
                apps.remove(op.pkg)
            }
            is Op.Refresh -> {
                refresh.addAll(op.pkgs)
                if (op.force) force = true
            }
            is Op.Replace -> {
                hide.clear()
                for ((u, m) in op.data.hide) if (m.isNotEmpty()) hide[u] = LinkedHashMap(m)
                hideScopes.clear()
                for ((u, m) in op.data.hideScopes) if (m.isNotEmpty()) hideScopes[u] = LinkedHashMap(m)
                labels.clear()
                for ((u, m) in op.data.labels) {
                    val clean = m.filterKeys { !Pkg.isProtectedForLook(it) }
                    if (clean.isNotEmpty()) labels[u] = LinkedHashMap(clean)
                }
                autoHide = op.data.autoHide
                autoHideClone = op.data.autoHideClone
                hideIntentChooser = op.data.hideIntentChooser
                hideShortcuts = op.data.hideShortcuts
                hideShortcutConfigs = op.data.hideShortcutConfigs
                hideShareTargets = op.data.hideShareTargets
                hideWidgets = op.data.hideWidgets
                val next = LinkedHashMap<String, AppRule>()
                for ((pkg, want) in op.data.apps) {
                    if (!want.isActive || Pkg.isProtectedForLook(pkg)) continue
                    val have = apps[pkg]
                    next[pkg] = if (have != null && have.label == want.label && have.iconRev == want.iconRev) have
                    else AppRule(label = want.label, iconRev = want.iconRev, changedAt = now,
                        overlayRev = have?.overlayRev ?: 0L,
                        overlayStatus = have?.overlayStatus ?: Overlay.NONE)
                }
                // Overlays of packages the replacement dropped still need to be removed: keep a bare ledger record.
                for ((pkg, have) in apps) if (pkg !in next && have.overlayRev > 0) next[pkg] = have.copy(label = null, iconRev = 0L, changedAt = now)
                apps.clear()
                apps.putAll(next)

                val nextIcons = LinkedHashMap<Int, MutableMap<String, AppRule>>()
                for ((u, wantMap) in op.data.icons) {
                    val haveMap = icons[u].orEmpty()
                    val m = LinkedHashMap<String, AppRule>()
                    for ((pkg, want) in wantMap) {
                        if (want.iconRev <= 0L || Pkg.isProtectedForLook(pkg)) continue
                        val have = haveMap[pkg]
                        m[pkg] = if (have != null && have.iconRev == want.iconRev) have
                        else AppRule(iconRev = want.iconRev, changedAt = now,
                            overlayRev = have?.overlayRev ?: 0L,
                            overlayStatus = have?.overlayStatus ?: Overlay.NONE)
                    }
                    if (m.isNotEmpty()) nextIcons[u] = m
                }
                for ((u, haveMap) in icons) {
                    val m = nextIcons.getOrPut(u) { LinkedHashMap() }
                    for ((pkg, have) in haveMap) if (pkg !in m && have.overlayRev > 0) m[pkg] = have.copy(label = null, iconRev = 0L, changedAt = now)
                    if (m.isEmpty()) nextIcons.remove(u)
                }
                icons.clear()
                icons.putAll(nextIcons)
            }
        }

        pruneTombstones(apps, now)
        for (m in icons.values) pruneTombstones(m, now)
        icons.values.removeAll { it.isEmpty() }
        val total = hide.values.sumOf { it.size }
        val totalLabels = labels.values.sumOf { it.size }
        val totalIcons = icons.values.sumOf { it.size }
        require(total <= Limits.MAX_PACKAGES) { "too many hide entries" }
        require(totalLabels <= Limits.MAX_PACKAGES) { "too many label entries" }
        require(totalIcons <= Limits.MAX_PACKAGES) { "too many icon entries" }
        require(apps.size <= Limits.MAX_PACKAGES) { "too many app rules" }

        val removeOverlay = LinkedHashSet<String>()
        for ((pkg, old) in base.apps) {
            if (old.overlayRev > 0 && (apps[pkg]?.iconRev ?: 0L) == 0L) removeOverlay.add(pkg)
        }
        val removeOverlayUsers = LinkedHashSet<Pair<Int, String>>()
        for ((u, oldMap) in base.icons) for ((pkg, old) in oldMap) {
            if (old.overlayRev > 0 && (icons[u]?.get(pkg)?.iconRev ?: 0L) == 0L) removeOverlayUsers.add(u to pkg)
        }
        val data = StateData(
            base.rev,
            autoHide,
            autoHideClone,
            hideIntentChooser,
            hideShortcuts,
            hideShortcutConfigs,
            hideShareTargets,
            hideWidgets,
            hide.mapValues { it.value.toMap() },
            hideScopes.mapValues { it.value.toMap() },
            apps,
            labels.mapValues { it.value.toMap() },
            icons.mapValues { it.value.toMap() },
        )
        return Result(data, refresh, force, removeOverlay, removeOverlayUsers)
    }

    fun pruneTombstones(apps: MutableMap<String, AppRule>, now: Long) {
        val it = apps.entries.iterator()
        while (it.hasNext()) {
            val r = it.next().value
            if (r.isTombstone && now - r.changedAt > Limits.TOMBSTONE_TTL_MS) it.remove()
        }
    }

    fun sameContent(a: StateData, b: StateData): Boolean = a.copy(rev = 0) == b.copy(rev = 0)
}

/** What changed between two snapshots; the notifier turns this into launcher signals. */
class Diff(
    /** (user, package) pairs whose explicit hide choice changed. */
    val hide: Set<Pair<Int, String>>,
    /** The auto-hide flags changed: every detected module may flip. */
    val autoChanged: Boolean,
    val names: Set<String>,
    val icons: Set<String>,
    /** (user, package) pairs whose per-profile label changed. */
    val nameUsers: Set<Pair<Int, String>> = emptySet(),
    /** (user, package) pairs whose per-profile icon changed. */
    val iconUsers: Set<Pair<Int, String>> = emptySet(),
) {
    val isEmpty: Boolean get() = hide.isEmpty() && !autoChanged && names.isEmpty() && icons.isEmpty() && nameUsers.isEmpty() && iconUsers.isEmpty()

    companion object {
        fun between(old: StateData, new: StateData): Diff {
            val hide = LinkedHashSet<Pair<Int, String>>()
            for (u in old.hide.keys + new.hide.keys) {
                val a = old.hide[u].orEmpty()
                val b = new.hide[u].orEmpty()
                for (p in a.keys + b.keys) if (a[p] != b[p]) hide.add(u to p)
            }
            for (u in old.hideScopes.keys + new.hideScopes.keys) {
                val a = old.hideScopes[u].orEmpty()
                val b = new.hideScopes[u].orEmpty()
                for (p in a.keys + b.keys) if (a[p] != b[p]) hide.add(u to p)
            }
            val nameUsers = LinkedHashSet<Pair<Int, String>>()
            for (u in old.labels.keys + new.labels.keys) {
                val a = old.labels[u].orEmpty()
                val b = new.labels[u].orEmpty()
                for (p in a.keys + b.keys) if (a[p] != b[p]) nameUsers.add(u to p)
            }
            val iconUsers = LinkedHashSet<Pair<Int, String>>()
            for (u in old.icons.keys + new.icons.keys) {
                val a = old.icons[u].orEmpty()
                val b = new.icons[u].orEmpty()
                for (p in a.keys + b.keys) if ((a[p]?.iconRev ?: 0L) != (b[p]?.iconRev ?: 0L)) iconUsers.add(u to p)
            }
            val names = LinkedHashSet<String>()
            val icons = LinkedHashSet<String>()
            for (p in old.apps.keys + new.apps.keys) {
                if (old.apps[p]?.label != new.apps[p]?.label) names.add(p)
                if ((old.apps[p]?.iconRev ?: 0L) != (new.apps[p]?.iconRev ?: 0L)) icons.add(p)
            }
            val autoChanged = old.autoHide != new.autoHide || old.autoHideClone != new.autoHideClone ||
                old.hideShortcuts != new.hideShortcuts || old.hideShortcutConfigs != new.hideShortcutConfigs
            return Diff(hide, autoChanged, names, icons, nameUsers, iconUsers)
        }
    }
}
