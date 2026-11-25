package dev.fadalias.core

import android.content.Context
import java.lang.ref.WeakReference

/** Process-wide handles of the hook side (system_server). One place for the backend, the contexts and the reload lifecycle. */
internal object Runtime {
    @Volatile
    var backend: HookBackend? = null

    /** libxposed API level the framework gave this module (101 or 102); 0 until known. */
    @Volatile
    var apiVersion = 0

    @Volatile
    private var lastContext: Context? = null

    @Volatile
    private var implRef: WeakReference<Any>? = null

    fun be(): HookBackend = backend ?: throw IllegalStateException("hook backend not installed")

    /** The LauncherAppsImpl instance, captured from any hook call. */
    fun remember(impl: Any?) {
        if (impl != null && implRef?.get() !== impl) implRef = WeakReference(impl)
    }

    fun impl(): Any? = implRef?.get()

    fun contextOf(impl: Any?): Context? {
        if (impl != null) {
            val c = try { be().getObjectField(impl, "mContext") as? Context } catch (_: Throwable) { null }
            if (c != null) {
                lastContext = c
                return c
            }
        }
        return lastContext ?: systemContext()
    }

    fun systemContext(): Context? = runCatching {
        val at = Class.forName("android.app.ActivityThread")
        at.getMethod("currentActivityThread").invoke(null)?.let { at.getMethod("getSystemContext").invoke(it) as? Context }
    }.getOrNull()?.also { lastContext = it }

    fun lastKnownContext(): Context? = lastContext

    // ---- hot reload lifecycle (API 102 only; on 101 acceptingReload simply stays false) ------------

    val lifecycleLock = Any()

    @Volatile
    var acceptingReload = false

    @Volatile
    var reloadStartNanos = 0L

    const val RELOAD_TIMEOUT_NANOS = 5_000_000_000L

    @Volatile
    var onReloadTimeout: (() -> Unit)? = null

    fun isReloadPending(): Boolean {
        if (!acceptingReload) return false
        if (System.nanoTime() - reloadStartNanos > RELOAD_TIMEOUT_NANOS) {
            onReloadTimeout?.invoke()
            return false
        }
        return true
    }
}
