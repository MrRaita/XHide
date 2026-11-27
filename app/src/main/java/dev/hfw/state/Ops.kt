package dev.hfw.state

import org.json.JSONArray
import org.json.JSONObject

/** One edit made in the manager. Edits are replayed in order, so a replay never overwrites what it did not touch. */
sealed class Op {
    /** [hide] null clears the explicit choice for this user (the default applies again). */
    data class Hide(val user: Int, val pkg: String, val hide: Boolean?) : Op()
    /** [mask] null clears the per-app scope mask so the normal hide toggle applies. */
    data class HideScope(val user: Int, val pkg: String, val mask: Int?) : Op()
    data class Name(val pkg: String, val label: String?, val user: Int? = null) : Op()
    /** [rev] 0 removes the custom icon. */
    data class Icon(val pkg: String, val rev: Long, val user: Int? = null) : Op()
    data class Auto(
        val autoHide: Boolean?,
        val autoHideClone: Boolean?,
        val hideIntentChooser: Boolean? = null,
        val hideShortcuts: Boolean? = null,
        val hideShortcutConfigs: Boolean? = null,
        val hideShareTargets: Boolean? = null,
        val hideWidgets: Boolean? = null,
    ) : Op()
    data class Forget(val pkg: String) : Op()
    /** Ask the hook to re-apply and re-announce [pkgs] ([force] also rebuilds their overlays). */
    data class Refresh(val pkgs: List<String>, val force: Boolean) : Op()
    /** Full resync: the manager's rule store replaces everything (used when the hash of the two sides differs). */
    data class Replace(val data: StateData) : Op()
}

class Request(val id: String, val ops: List<Op>)

object OpCodec {
    fun encode(req: Request): String {
        val arr = JSONArray()
        for (op in req.ops) arr.put(encodeOp(op))
        return JSONObject().put("id", req.id).put("ops", arr).toString()
    }

    fun encodeOps(ops: List<Op>): JSONArray = JSONArray().also { a -> ops.forEach { a.put(encodeOp(it)) } }

    private fun encodeOp(op: Op): JSONObject = JSONObject().apply {
        when (op) {
            is Op.Hide -> { put("k", "h"); put("u", op.user); put("p", op.pkg); op.hide?.let { put("v", it) } }
            is Op.HideScope -> { put("k", "hs"); put("u", op.user); put("p", op.pkg); op.mask?.let { put("m", it) } }
            is Op.Name -> { put("k", "n"); put("p", op.pkg); op.label?.let { put("v", it) }; op.user?.let { put("u", it) } }
            is Op.Icon -> { put("k", "i"); put("p", op.pkg); put("r", op.rev); op.user?.let { put("u", it) } }
            is Op.Auto -> {
                put("k", "a")
                op.autoHide?.let { put("a", it) }
                op.autoHideClone?.let { put("c", it) }
                op.hideIntentChooser?.let { put("ic", it) }
                op.hideShortcuts?.let { put("sc", it) }
                op.hideShortcutConfigs?.let { put("cfg", it) }
                op.hideShareTargets?.let { put("st", it) }
                op.hideWidgets?.let { put("w", it) }
            }
            is Op.Forget -> { put("k", "f"); put("p", op.pkg) }
            is Op.Refresh -> { put("k", "r"); put("p", JSONArray(op.pkgs)); put("f", op.force) }
            is Op.Replace -> { put("k", "x"); put("d", StateCodec.toJson(op.data, includeRuntime = false)) }
        }
    }

    /** Strict: any malformed or out-of-limit content rejects the whole request (null). */
    fun decode(text: String): Request? {
        if (text.length > Limits.MAX_PAYLOAD_CHARS) return null
        return try {
            val root = JSONObject(text)
            val id = root.optString("id", "").take(64)
            val arr = root.getJSONArray("ops")
            if (arr.length() > Limits.MAX_OPS) return null
            val ops = ArrayList<Op>(arr.length())
            for (i in 0 until arr.length()) ops.add(decodeOp(arr.getJSONObject(i)) ?: return null)
            Request(id, ops)
        } catch (_: Throwable) {
            null
        }
    }

    /** Lenient decode for the manager's offline queue file. Unknown content yields null per op. */
    fun decodeOps(arr: JSONArray): List<Op>? {
        val out = ArrayList<Op>(arr.length())
        for (i in 0 until arr.length()) out.add(decodeOp(arr.optJSONObject(i) ?: return null) ?: return null)
        return out
    }

    private fun decodeOp(o: JSONObject): Op? = when (o.optString("k")) {
        "h" -> {
            val pkg = o.getString("p")
            val user = o.getInt("u")
            if (!Pkg.isValid(pkg) || user < 0 || user > Limits.MAX_USER_ID) null
            else Op.Hide(user, pkg, if (o.has("v")) o.getBoolean("v") else null)
        }
        "hs" -> {
            val pkg = o.getString("p")
            val user = o.getInt("u")
            val mask = if (o.has("m")) o.getInt("m") else null
            if (!Pkg.isValid(pkg) || user < 0 || user > Limits.MAX_USER_ID || (mask != null && (mask < 0 || mask > dev.hfw.state.HideScope.ALL))) null
            else Op.HideScope(user, pkg, mask)
        }
        "n" -> o.getString("p").takeIf { Pkg.isValid(it) }?.let { pkg ->
            val user = if (o.has("u")) o.getInt("u") else null
            if (user != null && (user < 0 || user > Limits.MAX_USER_ID)) null
            else Op.Name(pkg, if (o.has("v")) o.getString("v") else null, user)
        }
        "i" -> o.getString("p").takeIf { Pkg.isValid(it) }?.let { pkg ->
            val r = o.getLong("r")
            val user = if (o.has("u")) o.getInt("u") else null
            if (r < 0 || (user != null && (user < 0 || user > Limits.MAX_USER_ID))) null else Op.Icon(pkg, r, user)
        }
        "a" -> Op.Auto(
            if (o.has("a")) o.getBoolean("a") else null,
            if (o.has("c")) o.getBoolean("c") else null,
            if (o.has("ic")) o.getBoolean("ic") else null,
            if (o.has("sc")) o.getBoolean("sc") else null,
            if (o.has("cfg")) o.getBoolean("cfg") else null,
            if (o.has("st")) o.getBoolean("st") else null,
            if (o.has("w")) o.getBoolean("w") else null,
        )
        "f" -> o.getString("p").takeIf { Pkg.isValid(it) }?.let { Op.Forget(it) }
        "r" -> {
            val arr = o.getJSONArray("p")
            if (arr.length() > Limits.MAX_OPS) null
            else {
                val pkgs = (0 until arr.length()).map { arr.getString(it) }
                if (pkgs.all { Pkg.isValid(it) }) Op.Refresh(pkgs, o.optBoolean("f", false)) else null
            }
        }
        "x" -> StateCodec.fromJson(o.getJSONObject("d"), strict = true)?.let { Op.Replace(it) }
        else -> null
    }
}
