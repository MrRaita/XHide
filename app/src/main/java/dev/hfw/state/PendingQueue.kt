package dev.hfw.state

/**
 * The manager's offline queue. Edits made while the hook cannot be reached are coalesced so the queue stays small and
 * a replay never re-applies outdated intermediate steps: Hide per (user, package), Name/Icon per package, Auto merged,
 * Forget drops the earlier edits of its package, Replace supersedes everything before it. Order of last occurrence is kept.
 */
object PendingQueue {
    const val MAX = 512

    fun coalesce(ops: List<Op>): List<Op> {
        val m = LinkedHashMap<String, Op>()
        fun put(k: String, op: Op) { m.remove(k); m[k] = op }
        for (op in ops) when (op) {
            is Op.Hide -> put("h:${op.user}:${op.pkg}", op)
            is Op.HideScope -> put("hs:${op.user}:${op.pkg}", op)
            is Op.Name -> put("n:${op.user ?: "*"}:${op.pkg}", op)
            is Op.Icon -> put("i:${op.user ?: "*"}:${op.pkg}", op)
            is Op.Auto -> {
                val prev = m["a"] as? Op.Auto
                put("a", Op.Auto(
                    op.autoHide ?: prev?.autoHide,
                    op.autoHideClone ?: prev?.autoHideClone,
                    op.hideIntentChooser ?: prev?.hideIntentChooser,
                    op.hideShortcuts ?: prev?.hideShortcuts,
                    op.hideShortcutConfigs ?: prev?.hideShortcutConfigs,
                    op.hideShareTargets ?: prev?.hideShareTargets,
                    op.hideWidgets ?: prev?.hideWidgets,
                ))
            }
            is Op.Forget -> {
                val suffix = ":${op.pkg}"
                m.keys.removeAll {
                    ((it.startsWith("h:") || it.startsWith("hs:") || it.startsWith("n:") || it.startsWith("i:")) && it.endsWith(suffix)) ||
                        it == "f:${op.pkg}"
                }
                put("f:${op.pkg}", op)
            }
            is Op.Refresh -> {
                val prev = m["r"] as? Op.Refresh
                put("r", Op.Refresh(((prev?.pkgs ?: emptyList()) + op.pkgs).distinct(), (prev?.force ?: false) || op.force))
            }
            is Op.Replace -> { m.clear(); put("x", op) }
        }
        return m.values.toList()
    }
}
