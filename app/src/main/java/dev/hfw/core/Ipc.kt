package dev.hfw.core

import android.os.Binder
import android.os.Build
import android.os.UserHandle
import dev.hfw.BuildConfig
import dev.hfw.Identity
import dev.hfw.state.Limits
import dev.hfw.state.OpCodec
import dev.hfw.state.StateCodec
import org.json.JSONObject

/**
 * Manager -> system_server commands. One prefix, carried in the package-name argument of getLauncherActivities:
 *
 *   ping | status | log | reload | state.get | state.update:<json> | all_users
 *
 * Every command except all_users answers with the manager's own launcher activity whose ApplicationInfo.metaData
 * holds the JSON reply. An empty list means "rejected or unreachable". Protocol changes are additive only.
 */
internal object Ipc {
    const val EXTRA_REPLY = "hfw_reply"
    const val EXTRA_ALL_USERS = "hfw_all_users"

    private const val CMD_UPDATE = "state.update:"

    /** Runs in the hook's `before`: verifies the caller, executes the command and prepares the rewritten query. */
    fun before(param: HookParam, callingPackage: String?, target: String, user: UserHandle?) {
        val impl = param.thisObject
        // Strictly reject commands from anything but the verified manager
        if (callingPackage != Identity.PACKAGE || !Callers.isSelf(Binder.getCallingUid(), impl)) {
            reject(param)
            return
        }
        val cmd = target.removePrefix(Identity.IPC_PREFIX)
        when {
            cmd == "ping" -> reply(param, ping())
            cmd == "status" -> reply(param, status())
            cmd == "log" -> reply(param, JSONObject().put("lines", HealthLog.logJson(120)).toString())
            cmd == "reload" -> {
                StateStore.load()
                reply(param, ping())
            }
            cmd == "state.get" -> {
                val text = StateCodec.encodeText(StateStore.snapshot)
                reply(param, if (text.length <= Limits.MAX_REPLY_CHARS) text else JSONObject().put("error", "too_large").toString())
            }
            cmd == "all_users" -> {
                param.setObjectExtra(EXTRA_ALL_USERS, true)
                param.args[1] = null
            }
            cmd.startsWith(CMD_UPDATE) -> update(param, cmd.removePrefix(CMD_UPDATE), user)
            else -> reject(param)
        }
    }

    private fun update(param: HookParam, payload: String, user: UserHandle?) {
        val req = OpCodec.decode(payload)
        if (req == null) {
            HealthLog.event("update rejected: invalid or oversized payload")
            reject(param)
            return
        }
        val u = StateStore.update(req.ops)
        if (u == null) {
            HealthLog.event("update rejected: limits exceeded or state could not be persisted")
            reject(param)
            return
        }
        Coordinator.afterUpdate(u, param.thisObject, user)
        reply(param, JSONObject().put("ok", true).put("id", req.id).put("rev", u.after.rev).put("hash", StateStore.hash()).toString())
    }

    private fun reply(param: HookParam, json: String) {
        param.setObjectExtra(EXTRA_REPLY, json)
        param.args[1] = Identity.PACKAGE // the query now answers with the manager's own launcher entry
    }

    /** A package name that cannot exist: the client gets an empty list, reports failure and reverts. */
    private fun reject(param: HookParam) {
        param.args[1] = Identity.IPC_REJECTED_PACKAGE
    }

    fun ping(): String = JSONObject().apply {
        put("proto", Identity.PROTOCOL)
        put("schema", Identity.SCHEMA)
        put("hook", BuildConfig.VERSION_CODE)
        put("rev", StateStore.snapshot.rev)
        put("hash", StateStore.hash())
        put("health", HealthLog.hooksJson())
    }.toString()

    private fun status(): String = JSONObject(ping()).apply {
        put("sdk", Build.VERSION.SDK_INT)
        put("api", Runtime.apiVersion)
        put("state", StateStore.loadedFrom)
        put("overlaySupported", Coordinator.overlaysSupported)
        put("overlayBlocked", Coordinator.overlaysBlocked)
        put("events", HealthLog.eventCount)
        put("explicitHides", StateStore.snapshot.explicitHideCount)
        put("modules", ModuleCache.getDetectedModulePackages().size)
    }.toString()
}
