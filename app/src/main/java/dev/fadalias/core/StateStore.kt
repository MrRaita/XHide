package dev.fadalias.core

import dev.fadalias.Identity
import dev.fadalias.data.DurableFiles
import dev.fadalias.state.Canonical
import dev.fadalias.state.AppRule
import dev.fadalias.state.Diff
import dev.fadalias.state.Limits
import dev.fadalias.state.Op
import dev.fadalias.state.Overlay
import dev.fadalias.state.Reducer
import dev.fadalias.state.StateCodec
import dev.fadalias.state.StateData
import java.io.File

/**
 * system_server's one store: hide choices, names, icon revisions and the overlay ledger in a single `state` file.
 * One writer (this object's lock), lock-free readers (the volatile immutable [snapshot]). Every change is written
 * durably first and published second, so memory, disk and the manager never diverge.
 */
internal object StateStore {
    class Update(
        val before: StateData,
        val after: StateData,
        val diff: Diff,
        val refresh: Set<String>,
        val force: Boolean,
        val overlayRemove: Set<String>,
        val overlayRemoveUsers: Set<Pair<Int, String>> = emptySet(),
    )

    @Volatile
    internal var dir: String = Identity.STATE_DIR

    @Volatile
    var snapshot: StateData = StateData()
        private set

    @Volatile
    var loadedFrom: String = "none"
        private set

    @Volatile
    private var loaded = false

    private val writeLock = Any()

    private fun stateFile() = File(dir, "state")
    private fun backupFile() = File(dir, "state.bak")

    /** Synchronous: called while the hooks are installed, so no hook ever runs against an empty default. */
    fun load(): StateData = synchronized(writeLock) {
        val now = System.currentTimeMillis()
        var data: StateData? = null
        var from = "default"
        for ((file, name) in listOf(stateFile() to "state", backupFile() to "state.bak")) {
            val d = readFile(file) ?: continue
            data = d
            from = name
            break
        }
        val base = data ?: StateData()
        val apps = LinkedHashMap(base.apps)
        Reducer.pruneTombstones(apps, now)
        val icons = LinkedHashMap<Int, Map<String, AppRule>>()
        for ((u, m) in base.icons) {
            val copy = LinkedHashMap(m)
            Reducer.pruneTombstones(copy, now)
            if (copy.isNotEmpty()) icons[u] = copy
        }
        snapshot = base.copy(apps = apps, icons = icons)
        loadedFrom = from
        loaded = true
        snapshot
    }

    private fun readFile(file: File): StateData? = try {
        if (!file.isFile || file.length() > Limits.MAX_STATE_FILE_BYTES) null
        else StateCodec.decodeText(file.readText(), strict = false)
    } catch (_: Throwable) {
        null
    }

    fun ensureLoaded() {
        if (!loaded) load()
    }

    /**
     * Applies [ops]. Returns null when the request is invalid or could not be persisted; the published snapshot is
     * then unchanged and the manager reports failure instead of believing the update was applied.
     */
    fun update(ops: List<Op>): Update? = synchronized(writeLock) {
        ensureLoaded()
        val before = snapshot
        val reduced = try {
            Reducer.reduce(before, ops, System.currentTimeMillis())
        } catch (_: IllegalArgumentException) {
            return null
        }
        val changed = !Reducer.sameContent(before, reduced.data) || before.apps != reduced.data.apps || before.icons != reduced.data.icons
        val after = if (changed) reduced.data.copy(rev = before.rev + 1) else before
        if (changed && !persist(after)) return null
        snapshot = after
        Update(before, after, Diff.between(before, after), reduced.refresh, reduced.forceRefresh, reduced.overlayRemove, reduced.overlayRemoveUsers)
    }

    /** Records the outcome of an overlay build/removal. Writes only when something actually changed. */
    fun recordOverlay(pkg: String, rev: Long, status: Int, userId: Int? = null): Boolean = synchronized(writeLock) {
        ensureLoaded()
        val before = snapshot
        val after = if (userId != null) {
            val oldMap = before.icons[userId].orEmpty()
            val old = oldMap[pkg] ?: return true
            val updated = old.copy(overlayRev = rev, overlayStatus = status)
            if (updated == old) return true
            val nextUser = LinkedHashMap(oldMap)
            if (updated == AppRule()) nextUser.remove(pkg) else nextUser[pkg] = updated
            val nextIcons = LinkedHashMap(before.icons)
            if (nextUser.isEmpty()) nextIcons.remove(userId) else nextIcons[userId] = nextUser
            before.copy(rev = before.rev + 1, icons = nextIcons)
        } else {
            val old = before.apps[pkg] ?: return true // forgotten in the meantime
            val updated = old.copy(overlayRev = rev, overlayStatus = status)
            if (updated == old) return true
            val apps = LinkedHashMap(before.apps)
            if (updated == AppRule()) apps.remove(pkg) else apps[pkg] = updated
            before.copy(rev = before.rev + 1, apps = apps)
        }
        if (!persist(after)) return false
        snapshot = after
        true
    }

    /** Drops everything known about [pkg] (uninstall). User-scoped only when the package still exists for other users. */
    fun forget(pkg: String, userId: Int?): Update? {
        val ops = if (userId == null) listOf(Op.Forget(pkg)) else listOf(Op.Hide(userId, pkg, null), Op.Name(pkg, null, userId), Op.Icon(pkg, 0L, userId))
        return update(ops)
    }

    private fun persist(data: StateData): Boolean = try {
        val d = File(dir)
        d.mkdirs()
        d.setExecutable(true, false)
        val bytes = StateCodec.encodeText(data).toByteArray(Charsets.UTF_8)
        if (bytes.size > Limits.MAX_STATE_FILE_BYTES) false
        else {
            val f = stateFile()
            if (f.isFile) runCatching { f.copyTo(backupFile(), overwrite = true) }
            DurableFiles.writeAtomically(f.path, bytes)
            true
        }
    } catch (_: Throwable) {
        false
    }

    fun hash(): String = Canonical.hash(snapshot)

    /** Removes leftover icon PNGs that no active rule references anymore. */
    fun pruneOrphanedIcons() {
        val iconsDir = File(dir, "icons")
        val files = iconsDir.listFiles() ?: return
        val snap = snapshot
        for (f in files) {
            if (!f.isFile || !f.name.endsWith(".png")) continue
            val base = f.name.removeSuffix(".png")
            val sep = base.indexOf('_')
            val userPrefix = if (sep > 0) base.substring(0, sep).toIntOrNull() else null
            val active = if (userPrefix != null) {
                (snap.icons[userPrefix]?.get(base.substring(sep + 1))?.iconRev ?: 0L) > 0L
            } else {
                (snap.apps[base]?.iconRev ?: 0L) > 0L
            }
            if (!active) runCatching { f.delete() }
        }
    }
}
