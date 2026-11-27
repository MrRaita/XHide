package dev.fadalias.app

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import dev.fadalias.Identity
import dev.fadalias.state.OpCodec
import dev.fadalias.state.Request
import dev.fadalias.state.StateCodec
import dev.fadalias.state.StateData
import org.json.JSONObject

/** Talks to the system_server hook through LauncherApps.getActivityList (the command travels in the package-name argument). */
class HookClient(private val ctx: Context) {
    class Ping(val proto: Int, val schema: Int, val hook: Int, val rev: Long, val hash: String, val health: JSONObject?)

    sealed interface UpdateResult {
        class Ok(val rev: Long, val hash: String) : UpdateResult
        data object Rejected : UpdateResult
        data object Unreachable : UpdateResult
    }

    private fun query(cmd: String): List<LauncherActivityInfo>? = try {
        ctx.getSystemService(LauncherApps::class.java)?.getActivityList(Identity.IPC_PREFIX + cmd, Process.myUserHandle())
    } catch (_: Throwable) {
        null
    }

    private fun reply(cmd: String): String? =
        query(cmd)?.firstOrNull()?.applicationInfo?.metaData?.getString(Identity.REPLY_META_KEY)

    private fun json(cmd: String): JSONObject? = try { reply(cmd)?.let { JSONObject(it) } } catch (_: Throwable) { null }

    fun ping(): Ping? {
        val o = json("ping") ?: return null
        if (!o.has("hash")) return null
        return Ping(o.optInt("proto"), o.optInt("schema"), o.optInt("hook"), o.optLong("rev"), o.optString("hash"), o.optJSONObject("health"))
    }

    fun status(): JSONObject? = json("status")

    fun log(): List<String> = try {
        val a = json("log")?.optJSONArray("lines")
        if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
    } catch (_: Throwable) {
        emptyList()
    }

    /** The hook's full state, or null (also when the hook answered with an error object). */
    fun stateGet(): StateData? {
        val text = reply("state.get") ?: return null
        return try {
            val o = JSONObject(text)
            if (o.has("error") || !o.has("schema")) null else StateCodec.fromJson(o, strict = false)
        } catch (_: Throwable) {
            null
        }
    }

    fun update(req: Request): UpdateResult {
        val text = reply("state.update:" + OpCodec.encode(req))
        if (text == null) return if (ping() != null) UpdateResult.Rejected else UpdateResult.Unreachable
        return try {
            val o = JSONObject(text)
            if (o.optBoolean("ok") && o.optString("id") == req.id.take(64)) UpdateResult.Ok(o.optLong("rev"), o.optString("hash"))
            else UpdateResult.Rejected
        } catch (_: Throwable) {
            UpdateResult.Rejected
        }
    }

    fun allUsers(): List<LauncherActivityInfo> = query("all_users").orEmpty()
}
