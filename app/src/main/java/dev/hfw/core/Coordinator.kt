package dev.hfw.core

import android.os.Process
import android.os.UserHandle
import dev.hfw.state.HideScope
import dev.hfw.state.Overlay

/**
 * Runs after a state update was persisted and published: decides which launcher signals the change needs
 * (see [Notifier] for the matrix) and which icon overlays must be built or removed, then does it off the Binder thread.
 */
internal object Coordinator {
    private val overlays = IconOverlays()

    val overlaysSupported: Boolean get() = overlays.supported
    val overlaysBlocked: Boolean get() = overlays.blocked
    val overlayLast: Int get() = overlays.lastResult

    fun afterUpdate(u: StateStore.Update, impl: Any?, requester: UserHandle?) {
        if (impl == null) {
            // No LauncherAppsImpl captured yet: names and hiding already apply to every query; only the live signal is missing.
            HealthLog.event("update applied (rev ${u.after.rev}) but no LauncherAppsImpl instance to notify yet")
        }
        val be = Runtime.be()
        val ctx = Runtime.contextOf(impl)
        val user = requester ?: Process.myUserHandle()
        val handles = if (impl != null) Notifier.userHandles(impl, user) else listOf(user)
        val byId = LinkedHashMap<Int, UserHandle>()
        for (h in handles) byId[userIdOf(h, be)] = h
        val requesterId = userIdOf(user, be)
        val d = u.diff
        val before = u.before
        val after = u.after

        fun isModule(pkg: String, profile: UserHandle) = ModuleCache.isXposedModule(pkg, null, ctx, profile, be)

        val hideSupported = android.os.Build.VERSION.SDK_INT >= 30
        val hideShow = LinkedHashMap<Int, MutableList<String>>()
        val transitioned = HashSet<Pair<Int, String>>()
        if (hideSupported) {
            // 1) hide / show transitions (explicit changes, plus every detected module when auto-hide flipped)
            val candidates = LinkedHashSet<Pair<Int, String>>(d.hide)
            if (d.autoChanged) {
                for (pkg in ModuleCache.getDetectedModulePackages()) for (id in byId.keys) candidates.add(id to pkg)
            }
            for ((id, pkg) in candidates) {
                val profile = byId[id] ?: continue
                val mod = isModule(pkg, profile)
                if (before.shouldHideScope(pkg, id, mod, HideScope.LAUNCHER) != after.shouldHideScope(pkg, id, mod, HideScope.LAUNCHER)) {
                    hideShow.getOrPut(id) { ArrayList() }.add(pkg)
                    transitioned.add(id to pkg)
                }
            }

            // 2) explicit refresh requests re-announce the current state of the named packages
            for (pkg in u.refresh) for ((id, profile) in byId) {
                if (id != requesterId && !Notifier.installedIn(ctx, pkg, id)) continue
                if (after.shouldHideScope(pkg, id, isModule(pkg, profile), HideScope.LAUNCHER)) {
                    hideShow.getOrPut(id) { ArrayList() }.add(pkg)
                    transitioned.add(id to pkg)
                }
            }
        }

        // 3) icon work first: the overlay must exist before launchers are told
        val iconWork = LinkedHashSet<String>()
        for (pkg in d.icons) if ((after.apps[pkg]?.iconRev ?: 0L) > 0L) iconWork.add(pkg)
        if (u.force) for (pkg in u.refresh) if ((after.apps[pkg]?.iconRev ?: 0L) > 0L) iconWork.add(pkg)
        val removals = u.overlayRemove

        val userIconWork = LinkedHashSet<Pair<Int, String>>()
        for ((uid, pkg) in d.iconUsers) if ((after.icons[uid]?.get(pkg)?.iconRev ?: 0L) > 0L) userIconWork.add(uid to pkg)
        if (u.force) for (pkg in u.refresh) for ((uid, m) in after.icons) if ((m[pkg]?.iconRev ?: 0L) > 0L) userIconWork.add(uid to pkg)
        val userRemovals = u.overlayRemoveUsers

        // 4) name signals for visible packages that need no overlay work (hide/show transitions win)
        val nameCandidates = LinkedHashSet<String>()
        nameCandidates.addAll(d.names)
        nameCandidates.addAll(d.icons)
        nameCandidates.addAll(u.refresh)
        val nameSignals = LinkedHashMap<Int, MutableList<String>>()
        for (pkg in nameCandidates) {
            if (pkg in iconWork || pkg in removals) continue
            for ((id, profile) in byId) {
                if ((id to pkg) in transitioned || (id to pkg) in userIconWork || (id to pkg) in userRemovals) continue
                if (id != requesterId && !Notifier.installedIn(ctx, pkg, id)) continue
                if (hideSupported && after.shouldHideScope(pkg, id, isModule(pkg, profile), HideScope.LAUNCHER)) continue
                if (after.apps[pkg] == null && after.labelOf(pkg, id) == null && pkg !in u.refresh) continue
                nameSignals.getOrPut(id) { ArrayList() }.add(pkg)
            }
        }
        for ((id, pkg) in d.nameUsers) {
            if (pkg in iconWork || pkg in removals || (id to pkg) in transitioned || (id to pkg) in userIconWork || (id to pkg) in userRemovals) continue
            val profile = byId[id] ?: continue
            if (id != requesterId && !Notifier.installedIn(ctx, pkg, id)) continue
            if (hideSupported && after.shouldHide(pkg, id, isModule(pkg, profile))) continue
            val list = nameSignals.getOrPut(id) { ArrayList() }
            if (pkg !in list) list.add(pkg)
        }

        if (impl != null) {
            for ((id, pkgs) in hideShow) byId[id]?.let { Notifier.hideShow(impl, it, pkgs) }
            for ((id, pkgs) in nameSignals) byId[id]?.let { Notifier.changed(impl, it, pkgs) }
        }
        if (iconWork.isNotEmpty() || removals.isNotEmpty() || userIconWork.isNotEmpty() || userRemovals.isNotEmpty()) {
            Work.execute {
                runOverlayJob(impl, user, byId, requesterId, iconWork.toList(), removals, userIconWork.toList(), userRemovals, u.force, transitioned)
            }
        }
    }

    fun removeOverlays(pkgs: Set<String>, userId: Int? = null) {
        if (pkgs.isEmpty()) return
        Work.execute {
            val ctx = Runtime.contextOf(Runtime.impl()) ?: return@execute
            for (pkg in pkgs) {
                overlays.remove(ctx, pkg, userId)
                StateStore.recordOverlay(pkg, 0L, Overlay.NONE, userId)
            }
        }
    }

    /** Re-applies an active icon overlay after a package install or update. */
    fun reapplyOverlay(pkg: String, userId: Int) {
        Work.execute {
            val impl = Runtime.impl()
            val ctx = Runtime.contextOf(impl) ?: return@execute
            val be = Runtime.be()
            val snap = StateStore.snapshot
            val userRule = snap.icons[userId]?.get(pkg)
            val globalRule = snap.apps[pkg]
            if ((userRule?.iconRev ?: 0L) <= 0L && (globalRule?.iconRev ?: 0L) <= 0L) return@execute

            val handles = if (impl != null) Notifier.userHandles(impl, null) else emptyList()
            val byId = LinkedHashMap<Int, UserHandle>()
            for (h in handles) byId[userIdOf(h, be)] = h
            val profile = byId[userId] ?: (runCatching { be.callStaticMethod(UserHandle::class.java, "of", userId) as? UserHandle }.getOrNull()) ?: return@execute

            overlays.beginRound()
            if ((userRule?.iconRev ?: 0L) > 0L) {
                val out = overlays.apply(ctx, pkg, listOf(profile), userId)
                val ok = out.status == Overlay.OK
                StateStore.recordOverlay(pkg, if (ok) out.rev else userRule!!.overlayRev, out.status, userId)
                if (ok && impl != null && !StateStore.snapshot.shouldHideScope(pkg, userId, ModuleCache.isXposedModule(pkg, null, ctx, profile, be), HideScope.LAUNCHER)) {
                    Notifier.changed(impl, profile, listOf(pkg))
                }
            } else if ((globalRule?.iconRev ?: 0L) > 0L) {
                val targets = byId.filter { (id, _) -> Notifier.installedIn(ctx, pkg, id) }.values.toList().ifEmpty { listOf(profile) }
                val out = overlays.apply(ctx, pkg, targets)
                val ok = out.status == Overlay.OK
                StateStore.recordOverlay(pkg, if (ok) out.rev else globalRule!!.overlayRev, out.status)
                if (ok && impl != null) {
                    val latest = StateStore.snapshot
                    for ((id, prof) in byId) {
                        if (!Notifier.installedIn(ctx, pkg, id)) continue
                        if (latest.shouldHideScope(pkg, id, ModuleCache.isXposedModule(pkg, null, ctx, prof, be), HideScope.LAUNCHER)) continue
                        Notifier.changed(impl, prof, listOf(pkg))
                    }
                }
            }
        }
    }

    private fun runOverlayJob(
        impl: Any?,
        user: UserHandle,
        byId: Map<Int, UserHandle>,
        requesterId: Int,
        apply: List<String>,
        removals: Set<String>,
        userApply: List<Pair<Int, String>>,
        userRemovals: Set<Pair<Int, String>>,
        force: Boolean,
        transitioned: Set<Pair<Int, String>>,
    ) {
        val ctx = Runtime.contextOf(impl) ?: run {
            HealthLog.event("overlay job skipped: no system context")
            return
        }
        val be = Runtime.be()
        overlays.beginRound()
        val announce = LinkedHashSet<String>()
        val announceUsers = LinkedHashSet<Pair<Int, String>>()
        for (pkg in removals) {
            overlays.remove(ctx, pkg)
            StateStore.recordOverlay(pkg, 0L, Overlay.NONE)
            announce.add(pkg)
        }
        for ((uid, pkg) in userRemovals) {
            overlays.remove(ctx, pkg, uid)
            StateStore.recordOverlay(pkg, 0L, Overlay.NONE, uid)
            announceUsers.add(uid to pkg)
        }
        for (pkg in apply) {
            val rule = StateStore.snapshot.apps[pkg] ?: continue
            if (rule.iconRev == 0L) continue
            // every profile that has the app (personal, work, clones); the requester's own user is the fallback
            val targets = byId.filter { (id, _) -> Notifier.installedIn(ctx, pkg, id) }.values.toList().ifEmpty { listOf(user) }
            if (!force && rule.overlayRev == rule.iconRev && rule.overlayStatus == Overlay.OK && targets.all { overlays.isEnabled(ctx, pkg, it) }) continue
            val out = overlays.apply(ctx, pkg, targets)
            val ok = out.status == Overlay.OK
            StateStore.recordOverlay(pkg, if (ok) out.rev else rule.overlayRev, out.status)
            if (ok) announce.add(pkg)
        }
        for ((uid, pkg) in userApply) {
            val rule = StateStore.snapshot.icons[uid]?.get(pkg) ?: continue
            if (rule.iconRev == 0L) continue
            val targetHandle = byId[uid] ?: if (uid == requesterId) user else continue
            if (!force && rule.overlayRev == rule.iconRev && rule.overlayStatus == Overlay.OK && overlays.isEnabled(ctx, pkg, targetHandle, uid)) continue
            val out = overlays.apply(ctx, pkg, listOf(targetHandle), uid)
            val ok = out.status == Overlay.OK
            StateStore.recordOverlay(pkg, if (ok) out.rev else rule.overlayRev, out.status, uid)
            if (ok) announceUsers.add(uid to pkg)
        }
        if (impl == null || (announce.isEmpty() && announceUsers.isEmpty())) return
        val snap = StateStore.snapshot
        val perUser = LinkedHashMap<Int, MutableList<String>>()
        for (pkg in announce) for ((id, profile) in byId) {
            if ((id to pkg) in transitioned) continue
            if (id != requesterId && !Notifier.installedIn(ctx, pkg, id)) continue
            if (snap.shouldHideScope(pkg, id, ModuleCache.isXposedModule(pkg, null, ctx, profile, be), HideScope.LAUNCHER)) continue
            perUser.getOrPut(id) { ArrayList() }.add(pkg)
        }
        for ((id, pkg) in announceUsers) {
            if ((id to pkg) in transitioned) continue
            val profile = byId[id] ?: continue
            if (id != requesterId && !Notifier.installedIn(ctx, pkg, id)) continue
            if (snap.shouldHideScope(pkg, id, ModuleCache.isXposedModule(pkg, null, ctx, profile, be), HideScope.LAUNCHER)) continue
            val list = perUser.getOrPut(id) { ArrayList() }
            if (pkg !in list) list.add(pkg)
        }
        for ((id, pkgs) in perUser) byId[id]?.let { Notifier.changed(impl, it, pkgs) }
    }
}
