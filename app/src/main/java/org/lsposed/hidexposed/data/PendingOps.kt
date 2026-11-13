package org.lsposed.hidexposed.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A single rule edit made in the companion app. Edits that could not be delivered because the
 * system hook was inactive are queued as a list of these and replayed, in order, on top of the
 * current rules once the hook answers again. Storing edits instead of full lists means a replay
 * never overwrites changes it did not make.
 *
 * Free of Android dependencies so it is unit-testable.
 */
sealed class PendingOp {
    /** Hide or unhide one launcher entry. Fully determines the state of [key] when applied. */
    data class Toggle(
        val key: String,
        val packageName: String,
        val userId: Int,
        val isXposedModule: Boolean,
        val hide: Boolean
    ) : PendingOp()

    data class SetAutoHide(val value: Boolean) : PendingOp()

    data class SetAutoHideClone(val value: Boolean) : PendingOp()
}

/** Applies this edit to [r]. Single source of truth for hide/unhide semantics in the UI. */
fun PendingOp.applyTo(r: ConfigStore.HideRules): ConfigStore.HideRules = when (this) {
    is PendingOp.SetAutoHide -> r.copy(autoHide = value)
    is PendingOp.SetAutoHideClone -> r.copy(autoHideClone = value)
    is PendingOp.Toggle -> {
        val newWl = r.whitelist.toMutableSet()
        val newBl = r.blacklist.toMutableSet()

        // A user-scoped choice replaces any legacy global entry for the package.
        newWl.remove(packageName)
        newBl.remove(packageName)

        // ConfigStore force-whitelists XHide itself unless it is explicitly blacklisted, so auto-hide
        // never applies to it. Treat it as visible-by-default so hiding writes a blacklist entry.
        val isSelf = key == ConfigStore.SELF_USER0_KEY
        val autoHideForUser = if (userId == 0) r.autoHide else (r.autoHide and r.autoHideClone)
        val defaultHidden = !isSelf and isXposedModule and autoHideForUser

        if (hide) {
            newWl.remove(key)
            if (!defaultHidden) newBl.add(key)
        } else {
            newBl.remove(key)
            if (defaultHidden) newWl.add(key)
        }
        r.copy(whitelist = newWl, blacklist = newBl)
    }
}

object PendingOps {
    const val MAX_PENDING_OPS = 512
    private const val VERSION = 1

    /**
     * Appends [op] to [ops]. Returns null when the queue is full.
     *
     * Earlier toggles are never dropped: a toggle does not fully determine its key's state (hiding an
     * auto-hidden module keeps an existing blacklist entry, for example), so removing an earlier one
     * can change the replayed result. Keeping every op makes delivery identical to ordered replay.
     */
    fun enqueue(ops: List<PendingOp>, op: PendingOp): List<PendingOp>? {
        if (ops.size >= MAX_PENDING_OPS) return null
        return ops + op
    }

    /** Replays [ops] on top of [base], in order. */
    fun applyAll(base: ConfigStore.HideRules, ops: List<PendingOp>): ConfigStore.HideRules =
        ops.fold(base) { r, op -> op.applyTo(r) }

    /** Launcher keys affected by [ops], used to tell launchers which packages changed. */
    fun changedKeys(ops: List<PendingOp>): List<String> =
        ops.filterIsInstance<PendingOp.Toggle>().map { it.key }.distinct()

    fun encode(ops: List<PendingOp>): String {
        val arr = JSONArray()
        for (op in ops) {
            val o = JSONObject()
            when (op) {
                is PendingOp.Toggle -> {
                    o.put("t", "toggle")
                    o.put("key", op.key)
                    o.put("pkg", op.packageName)
                    o.put("user", op.userId)
                    o.put("module", op.isXposedModule)
                    o.put("hide", op.hide)
                }
                is PendingOp.SetAutoHide -> {
                    o.put("t", "autoHide")
                    o.put("value", op.value)
                }
                is PendingOp.SetAutoHideClone -> {
                    o.put("t", "autoHideClone")
                    o.put("value", op.value)
                }
            }
            arr.put(o)
        }
        return JSONObject().put("v", VERSION).put("ops", arr).toString()
    }

    /** Parses [text]; any malformed or unknown content yields an empty queue. */
    fun decode(text: String?): List<PendingOp> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val root = JSONObject(text)
            if (root.optInt("v", -1) != VERSION) return emptyList()
            val arr = root.getJSONArray("ops")
            if (arr.length() > MAX_PENDING_OPS) return emptyList()
            val out = ArrayList<PendingOp>(arr.length())
            for (i in 0 until arr.length()) {
                out.add(decodeOp(arr.getJSONObject(i)) ?: return emptyList())
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun decodeOp(o: JSONObject): PendingOp? = when (o.optString("t")) {
        "toggle" -> {
            val key = o.getString("key")
            val pkg = o.getString("pkg")
            val user = o.getInt("user")
            val keyOk = ConfigStore.isValidConfigKey(key)
            val pkgOk = ConfigStore.isValidConfigKey(pkg)
            if (keyOk and pkgOk and (key == ConfigStore.userKey(user, pkg))) {
                PendingOp.Toggle(key, pkg, user, o.getBoolean("module"), o.getBoolean("hide"))
            } else {
                null
            }
        }
        "autoHide" -> PendingOp.SetAutoHide(o.getBoolean("value"))
        "autoHideClone" -> PendingOp.SetAutoHideClone(o.getBoolean("value"))
        else -> null
    }
}