package dev.hfw.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import dev.hfw.Identity

/** system_server pulls icon PNGs from here (not exported; only uid 1000 or this app may ask). */
class RulesProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != Identity.PROVIDER_METHOD_ICON || arg == null) return null
        val uid = Binder.getCallingUid()
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) return null
        val ctx = context ?: return null
        val store = Services.get(ctx).store
        val sep = arg.indexOf(':')
        val userId = if (sep > 0) arg.substring(0, sep).toIntOrNull() else null
        val pkg = if (userId != null) arg.substring(sep + 1) else arg
        val (png, rev) = store.iconBytes(pkg, userId) ?: return null
        return Bundle().apply {
            putByteArray(Identity.PROVIDER_EXTRA_PNG, png)
            putLong(Identity.PROVIDER_EXTRA_REV, rev)
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
