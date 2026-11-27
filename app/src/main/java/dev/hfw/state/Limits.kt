package dev.hfw.state

import dev.hfw.Identity

/** Size limits shared by the manager (which refuses early) and the hook (which refuses for real). */
object Limits {
    const val MAX_PAYLOAD_CHARS = 96 * 1024
    const val MAX_OPS = 100
    const val MAX_LABEL = 100
    const val MAX_PACKAGES = 4096
    const val MAX_STATE_FILE_BYTES = 1024 * 1024
    const val MAX_REPLY_CHARS = 300 * 1024
    const val MAX_ICON_BYTES = 600_000
    const val MAX_ICON_PX = 512
    const val MAX_USER_ID = 99_999
    const val TOMBSTONE_TTL_MS = 3L * 24 * 60 * 60 * 1000
}

object Pkg {
    private val valid = Regex("^[A-Za-z0-9_.]{1,255}$")

    fun isValid(pkg: String): Boolean = valid.matches(pkg) && !pkg.contains("..")

    /** These can never be renamed or re-iconed: the framework itself (not an app a user can see anyway). */
    private val protectedForLook = setOf("android", "system")

    /** Core apps that CAN be changed (like every system app), but only after the user confirmed a warning. */
    private val riskyForLook = setOf(
        "com.android.systemui", "com.android.settings", "com.android.providers.settings", "org.lsposed.manager",
    )

    fun isProtectedForLook(pkg: String): Boolean = pkg in protectedForLook

    fun isRiskyForLook(pkg: String): Boolean = pkg in riskyForLook
}
