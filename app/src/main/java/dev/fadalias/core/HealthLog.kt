package dev.fadalias.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hook health report and a small event ring buffer, kept in system_server memory and readable through the `status`
 * and `log` IPC commands. Works in release builds: it is the only way to see what happened inside system_server
 * without adb.
 */
internal object HealthLog {
    private const val MAX_LINES = 200
    private const val MAX_LINE_CHARS = 300

    private val lines = ArrayDeque<String>()
    private val hooks = LinkedHashMap<String, String>()

    const val OK = "ok"
    /** The method does not exist on this Android version (expected for version-specific fallbacks). */
    const val MISSING = "missing"
    const val FAILED = "failed"

    fun event(msg: String, error: Throwable? = null) {
        val text = if (error == null) msg else "$msg: ${error.javaClass.simpleName}: ${error.message}"
        val line = String.format("%tT %s", System.currentTimeMillis(), text).take(MAX_LINE_CHARS)
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
        runCatching { Runtime.backend?.log("[${dev.fadalias.Identity.TAG}] $text", error) }
    }

    fun tail(max: Int): List<String> = synchronized(lines) { lines.toList().takeLast(max) }

    fun resetHooks() = synchronized(hooks) { hooks.clear() }

    fun hook(name: String, status: String) = synchronized(hooks) {
        // A real hook beats a "missing" alternative: several Android versions are tried under the same name.
        if (hooks[name] != OK) hooks[name] = status
    }

    fun hooksJson(): JSONObject {
        val snap = synchronized(hooks) { LinkedHashMap(hooks) }
        val ok = snap.count { it.value == OK }
        val missing = snap.count { it.value == MISSING }
        val failed = snap.count { it.value == FAILED }
        val detail = JSONObject()
        for ((k, v) in snap) if (v != OK) detail.put(k, v)
        return JSONObject().put("ok", ok).put("missing", missing).put("failed", failed).put("problems", detail)
    }

    fun logJson(max: Int): JSONArray = JSONArray().also { arr -> tail(max).forEach { arr.put(it) } }

    val eventCount: Int get() = synchronized(lines) { lines.size }
}
