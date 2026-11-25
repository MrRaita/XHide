package dev.fadalias.core

import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo

/** Helpers for the result types of LauncherAppsImpl (ParceledListSlice of LauncherActivityInfoInternal / ResolveInfo). */
internal object Slices {
    fun list(result: Any): List<*>? {
        if (result is List<*>) return result
        val be = Runtime.be()
        return try {
            be.callMethod(result, "getList") as? List<*>
        } catch (_: Throwable) {
            try {
                be.getObjectField(result, "mList") as? List<*>
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun rebuild(original: Any, items: ArrayList<Any>): Any {
        if (original is List<*>) return items
        val be = Runtime.be()
        return try {
            be.newInstance(original.javaClass, items) ?: original
        } catch (_: Throwable) {
            try {
                be.setObjectField(original, "mList", items)
                original
            } catch (_: Throwable) {
                original
            }
        }
    }

    /** ResolveInfo (Android 11), LauncherActivityInfoInternal (12+), or a bare ActivityInfo. */
    fun activityInfo(item: Any?): ActivityInfo? {
        if (item == null) return null
        if (item is ActivityInfo) return item
        if (item is ResolveInfo) return item.activityInfo
        val be = Runtime.be()
        return try {
            be.callMethod(item, "getActivityInfo") as? ActivityInfo
        } catch (_: Throwable) {
            try {
                be.getObjectField(item, "mActivityInfo") as? ActivityInfo
            } catch (_: Throwable) {
                try {
                    be.getObjectField(item, "activityInfo") as? ActivityInfo
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }

    fun setActivityInfo(item: Any, ai: ActivityInfo) {
        if (item is ResolveInfo) {
            item.activityInfo = ai
            return
        }
        val be = Runtime.be()
        try {
            be.setObjectField(item, "mActivityInfo", ai)
        } catch (_: Throwable) {
            val f = item.javaClass.declaredFields.firstOrNull { it.type == ActivityInfo::class.java } ?: return
            f.isAccessible = true
            f.set(item, ai)
        }
    }
}
