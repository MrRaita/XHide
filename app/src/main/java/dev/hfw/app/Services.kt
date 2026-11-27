package dev.hfw.app

import android.content.Context
import java.io.File

/** Process-wide singletons. Created lazily from any entry point (activity, provider, boot receiver). */
class Services private constructor(val ctx: Context) {
    val prefs = Prefs(ctx)
    val store = RuleStore(File(ctx.filesDir, "rules"))
    val client = HookClient(ctx)
    val syncer = Syncer(store, client)
    val catalog = AppCatalog(ctx, client)

    companion object {
        @Volatile private var inst: Services? = null
        fun get(ctx: Context): Services = inst ?: synchronized(this) {
            inst ?: Services(ctx.applicationContext).also { inst = it }
        }
    }
}
