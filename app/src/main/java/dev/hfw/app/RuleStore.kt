package dev.hfw.app

import dev.hfw.data.DurableFiles
import dev.hfw.state.AppRule
import dev.hfw.state.Canonical
import dev.hfw.state.Op
import dev.hfw.state.OpCodec
import dev.hfw.state.PendingQueue
import dev.hfw.state.Reducer
import dev.hfw.state.StateCodec
import dev.hfw.state.StateData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/**
 * The manager's authoritative copy of what the user chose (hide choices, names, icon revisions), plus the icon PNGs
 * and the offline queue of edits the hook has not acknowledged yet. system_server's state is a replica of this.
 */
class RuleStore(private val dir: File) {
    private val lock = Any()

    @Volatile
    var state: StateData = StateData()
        private set

    @Volatile
    private var queue: List<Op> = emptyList()

    @Volatile
    var everSynced: Boolean = false
        private set

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> get() = _changes

    val pendingCount: Int get() = queue.size

    private fun rulesFile() = File(dir, "rules.json")
    private fun pendingFile() = File(dir, "pending.json")
    private fun syncedFile() = File(dir, "synced")
    private fun iconsDir() = File(dir, "icons")
    private fun iconFile(pkg: String) = File(iconsDir(), "$pkg.png")
    private fun iconFile(userId: Int, pkg: String) = File(iconsDir(), "${userId}_$pkg.png")

    private fun iconFileForKey(key: String): File {
        val sep = key.indexOf(':')
        if (sep > 0) {
            val uid = key.substring(0, sep).toIntOrNull()
            if (uid != null) return iconFile(uid, key.substring(sep + 1))
        }
        return iconFile(key)
    }

    init {
        dir.mkdirs()
        iconsDir().mkdirs()
        for (f in listOf(rulesFile(), File(dir, "rules.json.bak"))) {
            val d = try { if (f.isFile) StateCodec.decodeText(f.readText(), strict = false) else null } catch (_: Throwable) { null }
            if (d != null) { state = local(d); break }
        }
        queue = try {
            val f = pendingFile()
            if (f.isFile) OpCodec.decodeOps(JSONObject(f.readText()).getJSONArray("ops")) ?: emptyList() else emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        everSynced = syncedFile().exists()
    }

    private fun local(d: StateData) = d.copy(
        rev = 0L,
        apps = d.apps.filterValues { it.isActive }.mapValues { AppRule(label = it.value.label, iconRev = it.value.iconRev) },
        icons = d.icons.mapValues { (_, m) ->
            m.filterValues { it.iconRev > 0L }.mapValues { AppRule(iconRev = it.value.iconRev) }
        }.filterValues { it.isNotEmpty() },
    )

    fun nextIconRev(pkg: String, userId: Int? = null): Long {
        val cur = if (userId != null) state.iconRuleOf(pkg, userId)?.iconRev else state.apps[pkg]?.iconRev
        return maxOf(System.currentTimeMillis(), (cur ?: 0L) + 1)
    }

    /** Applies [ops] to the local copy, queues them for the hook, and stores [icon] / [extraIcons] (`pkg` or `userId:pkg` to PNG) first. False = refused, nothing changed. */
    fun apply(
        ops: List<Op>,
        icon: Pair<String, ByteArray>? = null,
        extraIcons: List<Pair<String, ByteArray>> = emptyList(),
    ): Boolean = synchronized(lock) {
        val reduced = try {
            Reducer.reduce(state, ops, System.currentTimeMillis())
        } catch (_: IllegalArgumentException) {
            return false
        }
        val allIcons = if (icon != null) extraIcons + icon else extraIcons
        for ((k, bytes) in allIcons) {
            try { DurableFiles.writeAtomically(iconFileForKey(k).path, bytes) } catch (_: Throwable) { return false }
        }
        val next = local(reduced.data)
        var q = PendingQueue.coalesce(queue + ops)
        if (q.size > PendingQueue.MAX) q = listOf(Op.Replace(next))
        if (!persist(next, q)) return false
        state = next
        queue = q
        cleanupIcons(next)
        _changes.value++
        true
    }

    /** What the provider hands to system_server: the PNG and the revision it belongs to. */
    fun iconBytes(pkg: String, userId: Int? = null): Pair<ByteArray, Long>? = synchronized(lock) {
        val rev = if (userId != null) (state.icons[userId]?.get(pkg)?.iconRev ?: 0L) else (state.apps[pkg]?.iconRev ?: 0L)
        if (rev <= 0L) return null
        val f = if (userId != null) iconFile(userId, pkg) else iconFile(pkg)
        if (!f.isFile || f.length() > 2_000_000) return null
        try { f.readBytes() to rev } catch (_: Throwable) { null }
    }

    fun hasIconFile(pkg: String, userId: Int? = null): Boolean =
        if (userId != null) iconFile(userId, pkg).isFile else iconFile(pkg).isFile

    /** Next ops to send, bounded by count and encoded size. */
    fun batch(maxOps: Int, maxChars: Int): List<Op> {
        val out = ArrayList<Op>()
        var size = 0
        for (op in queue) {
            val n = OpCodec.encodeOps(listOf(op)).toString().length
            if (out.isNotEmpty() && (out.size >= maxOps || size + n > maxChars)) break
            out.add(op)
            size += n
        }
        return out
    }

    /** Removes acknowledged ops. Identity-based, so ops queued or merged meanwhile stay queued. */
    fun ack(sent: List<Op>) = synchronized(lock) {
        queue = queue.filterNot { q -> sent.any { it === q } }
        persist(state, queue)
        _changes.value++
    }

    /** Takes the hook's state as the new truth (first run on a fresh store, or a rejected edit): drops the queue. */
    fun adopt(remote: StateData) = synchronized(lock) {
        val next = local(remote)
        persist(next, emptyList())
        state = next
        queue = emptyList()
        cleanupIcons(next)
        _changes.value++
    }

    fun markSynced() {
        if (everSynced) return
        try { syncedFile().writeText("1") } catch (_: Throwable) {}
        everSynced = true
    }

    fun hash(): String = Canonical.hash(state)

    fun isPristine(): Boolean = queue.isEmpty() && state == StateData()

    fun fullReplace(): Op.Replace = Op.Replace(state)

    /** Consistent snapshot of rules and active icon PNGs for backup export. */
    fun exportSnapshot(): Pair<StateData, Map<String, ByteArray>> = synchronized(lock) {
        val snap = state
        val out = LinkedHashMap<String, ByteArray>()
        iconsDir().listFiles()?.forEach { f ->
            if (!f.isFile || !f.name.endsWith(".png")) return@forEach
            val base = f.name.removeSuffix(".png")
            val sep = base.indexOf('_')
            val userPrefix = if (sep > 0) base.substring(0, sep).toIntOrNull() else null
            val active = if (userPrefix != null) {
                (snap.icons[userPrefix]?.get(base.substring(sep + 1))?.iconRev ?: 0L) > 0L
            } else {
                (snap.apps[base]?.iconRev ?: 0L) > 0L
            }
            if (active && f.length() <= 2_000_000) {
                runCatching { out[f.name] = f.readBytes() }
            }
        }
        snap to out
    }

    /** Restores a validated backup, writes its icons, and queues a full Replace for system_server. */
    fun restoreBackup(imported: StateData, icons: Map<String, ByteArray>): Boolean = synchronized(lock) {
        val next = local(imported)
        iconsDir().mkdirs()
        // Remember every icon file we overwrite so a failure halfway leaves the old icons exactly as they were.
        val previous = LinkedHashMap<File, ByteArray?>()
        fun rollback() {
            for ((f, old) in previous) runCatching { if (old == null) f.delete() else DurableFiles.writeAtomically(f.path, old) }
        }
        for ((name, bytes) in icons) {
            if (!isReferencedIconFile(name, next)) continue
            val f = File(iconsDir(), name)
            previous[f] = try { if (f.isFile) f.readBytes() else null } catch (_: Throwable) { rollback(); return false }
            try { DurableFiles.writeAtomically(f.path, bytes) } catch (_: Throwable) { rollback(); return false }
        }
        val q = listOf(Op.Replace(next))
        if (!persist(next, q)) { rollback(); return false }
        state = next
        queue = q
        cleanupIcons(next)
        _changes.value++
        true
    }

    private fun isReferencedIconFile(fileName: String, s: StateData): Boolean {
        if (!fileName.endsWith(".png") || fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) return false
        val base = fileName.removeSuffix(".png")
        val sep = base.indexOf('_')
        val userPrefix = if (sep > 0) base.substring(0, sep).toIntOrNull() else null
        return if (userPrefix != null) {
            (s.icons[userPrefix]?.get(base.substring(sep + 1))?.iconRev ?: 0L) > 0L
        } else {
            (s.apps[base]?.iconRev ?: 0L) > 0L
        }
    }

    private fun cleanupIcons(next: StateData) {
        iconsDir().listFiles()?.forEach { f ->
            val base = f.name.removeSuffix(".png")
            val sep = base.indexOf('_')
            val userPrefix = if (sep > 0) base.substring(0, sep).toIntOrNull() else null
            val keep = if (userPrefix != null) {
                (next.icons[userPrefix]?.get(base.substring(sep + 1))?.iconRev ?: 0L) > 0L
            } else {
                (next.apps[base]?.iconRev ?: 0L) > 0L
            }
            if (!keep) f.delete()
        }
    }

    private fun persist(data: StateData, q: List<Op>): Boolean = try {
        dir.mkdirs()
        val rf = rulesFile()
        if (rf.isFile) runCatching { rf.copyTo(File(dir, "rules.json.bak"), overwrite = true) }
        DurableFiles.writeAtomically(rf.path, StateCodec.encodeText(data, includeRuntime = false).toByteArray(Charsets.UTF_8))
        DurableFiles.writeAtomically(
            pendingFile().path,
            JSONObject().put("ops", OpCodec.encodeOps(q)).toString().toByteArray(Charsets.UTF_8),
        )
        true
    } catch (_: Throwable) {
        false
    }
}
