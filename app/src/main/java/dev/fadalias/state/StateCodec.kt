package dev.fadalias.state

import dev.fadalias.Identity
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** The `state` file, the `state.get` reply and the `Replace` payload all use this one JSON shape. */
object StateCodec {
    /** [includeRuntime] adds the hook-side fields (rev, changedAt, overlay ledger); the manager never sends them. */
    fun toJson(d: StateData, includeRuntime: Boolean = true): JSONObject {
        val hide = JSONObject()
        for (user in d.hide.keys.sorted()) {
            val m = d.hide.getValue(user)
            if (m.isEmpty()) continue
            val o = JSONObject()
            for (pkg in m.keys.sorted()) o.put(pkg, m.getValue(pkg))
            hide.put(user.toString(), o)
        }
        val apps = JSONObject()
        for (pkg in d.apps.keys.sorted()) {
            val r = d.apps.getValue(pkg)
            if (!includeRuntime && !r.isActive) continue
            val o = JSONObject()
            r.label?.let { o.put("l", it) }
            if (r.iconRev > 0) o.put("i", r.iconRev)
            if (includeRuntime) {
                if (r.changedAt > 0) o.put("t", r.changedAt)
                if (r.overlayRev > 0) o.put("o", r.overlayRev)
                if (r.overlayStatus != Overlay.NONE) o.put("s", r.overlayStatus)
            }
            apps.put(pkg, o)
        }
        val labels = JSONObject()
        for (user in d.labels.keys.sorted()) {
            val m = d.labels.getValue(user)
            if (m.isEmpty()) continue
            val o = JSONObject()
            for (pkg in m.keys.sorted()) o.put(pkg, m.getValue(pkg))
            labels.put(user.toString(), o)
        }
        val icons = JSONObject()
        for (user in d.icons.keys.sorted()) {
            val m = d.icons.getValue(user)
            val uo = JSONObject()
            for (pkg in m.keys.sorted()) {
                val r = m.getValue(pkg)
                if (!includeRuntime && !r.isActive) continue
                val ro = JSONObject()
                if (r.iconRev > 0) ro.put("i", r.iconRev)
                if (includeRuntime) {
                    if (r.changedAt > 0) ro.put("t", r.changedAt)
                    if (r.overlayRev > 0) ro.put("o", r.overlayRev)
                    if (r.overlayStatus != Overlay.NONE) ro.put("s", r.overlayStatus)
                }
                uo.put(pkg, ro)
            }
            if (uo.length() > 0) icons.put(user.toString(), uo)
        }
        return JSONObject().apply {
            put("schema", Identity.SCHEMA)
            if (includeRuntime) put("rev", d.rev)
            put("autoHide", d.autoHide)
            put("autoHideClone", d.autoHideClone)
            put("hideIntentChooser", d.hideIntentChooser)
            put("hideShortcuts", d.hideShortcuts)
            put("hideShortcutConfigs", d.hideShortcutConfigs)
            put("hideShareTargets", d.hideShareTargets)
            put("hide", hide)
            if (d.hideScopes.isNotEmpty()) {
                val hs = JSONObject()
                for (user in d.hideScopes.keys.sorted()) {
                    val m = d.hideScopes.getValue(user)
                    if (m.isEmpty()) continue
                    val o = JSONObject()
                    for (pkg in m.keys.sorted()) o.put(pkg, m.getValue(pkg))
                    hs.put(user.toString(), o)
                }
                if (hs.length() > 0) put("hideScopes", hs)
            }
            if (labels.length() > 0) put("labels", labels)
            if (icons.length() > 0) put("icons", icons)
            put("apps", apps)
        }
    }

    /**
     * [strict] (IPC): reject instead of dropping invalid entries, so the manager never commits something the hook
     * applied differently. Lenient (file, reply): keep whatever is valid. Null only when the shape is unusable.
     */
    fun fromJson(o: JSONObject, strict: Boolean): StateData? {
        return try {
            if (o.optInt("schema", Identity.SCHEMA) > Identity.SCHEMA) return null // written by a newer version
            val hide = LinkedHashMap<Int, Map<String, Boolean>>()
            var count = 0
            val ho = o.optJSONObject("hide")
            if (ho != null) for (key in ho.keys()) {
                val user = key.toIntOrNull()
                if (user == null || user < 0 || user > Limits.MAX_USER_ID) { if (strict) return null else continue }
                val uo = ho.optJSONObject(key)
                if (uo == null) { if (strict) return null else continue }
                val m = LinkedHashMap<String, Boolean>()
                for (pkg in uo.keys()) {
                    if (!Pkg.isValid(pkg)) { if (strict) return null else continue }
                    if (++count > Limits.MAX_PACKAGES) { if (strict) return null else break }
                    val v = uo.opt(pkg) as? Boolean
                    if (v == null) { if (strict) return null else continue }
                    m[pkg] = v
                }
                if (m.isNotEmpty()) hide[user] = m
            }
            val apps = LinkedHashMap<String, AppRule>()
            val ao = o.optJSONObject("apps")
            if (ao != null) for (pkg in ao.keys()) {
                if (!Pkg.isValid(pkg) || Pkg.isProtectedForLook(pkg)) { if (strict) return null else continue }
                if (apps.size >= Limits.MAX_PACKAGES) { if (strict) return null else break }
                val r = ao.optJSONObject(pkg)
                if (r == null) { if (strict) return null else continue }
                apps[pkg] = AppRule(
                    label = if (r.has("l")) r.optString("l", "").trim().takeIf { it.isNotEmpty() }?.take(Limits.MAX_LABEL) else null,
                    iconRev = r.optLong("i", 0L).coerceAtLeast(0L),
                    changedAt = r.optLong("t", 0L).coerceAtLeast(0L),
                    overlayRev = r.optLong("o", 0L).coerceAtLeast(0L),
                    overlayStatus = r.optInt("s", Overlay.NONE),
                )
            }
            val labels = LinkedHashMap<Int, Map<String, String>>()
            var labelCount = 0
            val lo = o.optJSONObject("labels")
            if (lo != null) for (key in lo.keys()) {
                val user = key.toIntOrNull()
                if (user == null || user < 0 || user > Limits.MAX_USER_ID) { if (strict) return null else continue }
                val uo = lo.optJSONObject(key)
                if (uo == null) { if (strict) return null else continue }
                val m = LinkedHashMap<String, String>()
                for (pkg in uo.keys()) {
                    if (!Pkg.isValid(pkg) || Pkg.isProtectedForLook(pkg)) { if (strict) return null else continue }
                    if (++labelCount > Limits.MAX_PACKAGES) { if (strict) return null else break }
                    val v = (uo.opt(pkg) as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(Limits.MAX_LABEL)
                    if (v == null) { if (strict) return null else continue }
                    m[pkg] = v
                }
                if (m.isNotEmpty()) labels[user] = m
            }
            val icons = LinkedHashMap<Int, Map<String, AppRule>>()
            var iconCount = 0
            val io = o.optJSONObject("icons")
            if (io != null) for (key in io.keys()) {
                val user = key.toIntOrNull()
                if (user == null || user < 0 || user > Limits.MAX_USER_ID) { if (strict) return null else continue }
                val uo = io.optJSONObject(key)
                if (uo == null) { if (strict) return null else continue }
                val m = LinkedHashMap<String, AppRule>()
                for (pkg in uo.keys()) {
                    if (!Pkg.isValid(pkg) || Pkg.isProtectedForLook(pkg)) { if (strict) return null else continue }
                    if (++iconCount > Limits.MAX_PACKAGES) { if (strict) return null else break }
                    val r = uo.optJSONObject(pkg)
                    if (r == null) { if (strict) return null else continue }
                    m[pkg] = AppRule(
                        iconRev = r.optLong("i", 0L).coerceAtLeast(0L),
                        changedAt = r.optLong("t", 0L).coerceAtLeast(0L),
                        overlayRev = r.optLong("o", 0L).coerceAtLeast(0L),
                        overlayStatus = r.optInt("s", Overlay.NONE),
                    )
                }
                if (m.isNotEmpty()) icons[user] = m
            }
            val hideScopes = LinkedHashMap<Int, Map<String, Int>>()
            var hsCount = 0
            val hso = o.optJSONObject("hideScopes")
            if (hso != null) for (key in hso.keys()) {
                val user = key.toIntOrNull()
                if (user == null || user < 0 || user > Limits.MAX_USER_ID) { if (strict) return null else continue }
                val uo = hso.optJSONObject(key)
                if (uo == null) { if (strict) return null else continue }
                val m = LinkedHashMap<String, Int>()
                for (pkg in uo.keys()) {
                    if (!Pkg.isValid(pkg)) { if (strict) return null else continue }
                    if (++hsCount > Limits.MAX_PACKAGES) { if (strict) return null else break }
                    val mask = uo.optInt(pkg, -1)
                    if (mask < 0 || mask > HideScope.ALL) { if (strict) return null else continue }
                    m[pkg] = mask
                }
                if (m.isNotEmpty()) hideScopes[user] = m
            }
            StateData(
                rev = o.optLong("rev", 0L).coerceAtLeast(0L),
                autoHide = o.optBoolean("autoHide", true),
                autoHideClone = o.optBoolean("autoHideClone", true),
                hideIntentChooser = o.optBoolean("hideIntentChooser", true),
                hideShortcuts = o.optBoolean("hideShortcuts", true),
                hideShortcutConfigs = o.optBoolean("hideShortcutConfigs", true),
                hideShareTargets = o.optBoolean("hideShareTargets", true),
                hide = hide,
                hideScopes = hideScopes,
                apps = apps,
                labels = labels,
                icons = icons,
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeText(text: String, strict: Boolean = false): StateData? =
        try { fromJson(JSONObject(text), strict) } catch (_: Throwable) { null }

    fun encodeText(d: StateData, includeRuntime: Boolean = true): String = toJson(d, includeRuntime).toString()
}

/**
 * Hash of what the manager and the hook must agree on (settings, explicit hide choices, names, icon revisions).
 * Runtime fields (rev, overlay ledger, tombstones) are excluded so the two sides compare equal when in sync.
 */
object Canonical {
    fun hash(d: StateData): String {
        val sb = StringBuilder()
        sb.append("A|").append(d.autoHide).append('|').append(d.autoHideClone)
            .append('|').append(d.hideIntentChooser)
            .append('|').append(d.hideShortcuts)
            .append('|').append(d.hideShortcutConfigs)
            .append('|').append(d.hideShareTargets)
            .append('\n')
        for (user in d.hide.keys.sorted()) {
            val m = d.hide.getValue(user)
            for (pkg in m.keys.sorted()) sb.append("H|").append(user).append('|').append(pkg).append('|').append(m.getValue(pkg)).append('\n')
        }
        for (user in d.hideScopes.keys.sorted()) {
            val m = d.hideScopes.getValue(user)
            for (pkg in m.keys.sorted()) sb.append("HS|").append(user).append('|').append(pkg).append('|').append(m.getValue(pkg)).append('\n')
        }
        for (pkg in d.apps.keys.sorted()) {
            val r = d.apps.getValue(pkg)
            if (!r.isActive) continue
            sb.append("N|").append(pkg).append('|').append(r.label ?: "").append('|').append(r.iconRev).append('\n')
        }
        for (user in d.labels.keys.sorted()) {
            val m = d.labels.getValue(user)
            for (pkg in m.keys.sorted()) sb.append("L|").append(user).append('|').append(pkg).append('|').append(m.getValue(pkg)).append('\n')
        }
        for (user in d.icons.keys.sorted()) {
            val m = d.icons.getValue(user)
            for (pkg in m.keys.sorted()) {
                val r = m.getValue(pkg)
                if (r.iconRev > 0) sb.append("I|").append(user).append('|').append(pkg).append('|').append(r.iconRev).append('\n')
            }
        }
        val md = MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray(Charsets.UTF_8))
        return md.take(8).joinToString("") { "%02x".format(it) }
    }
}
