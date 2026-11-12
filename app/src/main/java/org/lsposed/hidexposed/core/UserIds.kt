package org.lsposed.hidexposed.core

import android.os.UserHandle
import de.robv.android.xposed.XposedHelpers

/**
 * Resolves the numeric user ID of a [UserHandle] inside `system_server`.
 *
 * Prefers the hidden `getIdentifier()` API, then reads the backing `mHandle` field directly
 * instead of relying on `hashCode() == mHandle`, which is an AOSP implementation detail
 * OEM ROMs are not obliged to keep. `hashCode()` is only the last resort.
 */
internal fun userIdOf(userHandle: UserHandle?): Int {
    if (userHandle == null) return 0
    return try {
        XposedHelpers.callMethod(userHandle, "getIdentifier") as Int
    } catch (_: Throwable) {
        try {
            XposedHelpers.getIntField(userHandle, "mHandle")
        } catch (_: Throwable) {
            userHandle.hashCode()
        }
    }
}
