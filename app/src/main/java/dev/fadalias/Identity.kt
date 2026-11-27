package dev.fadalias

/**
 * Every name the module and the manager app must agree on. Changing the app name or package id later
 * means changing this file, the manifest/gradle id and `sepolicy-module/` only.
 */
object Identity {
    const val APP_NAME = "HFW"
    const val PACKAGE = "dev.hfw"
    const val TAG = "HFW"

    /** system_server's own directory: `state`, `state.bak`, `icons/<pkg>.png`, `modcache.txt`. */
    const val STATE_DIR = "/data/system/hfw"

    /** Prefix of every manager -> system_server command, carried in the package-name argument of getLauncherActivities. */
    const val IPC_PREFIX = "__hfw_ipc__:"

    /** metaData key under which the hook attaches its JSON reply to the copy of the manager's own ApplicationInfo. */
    const val REPLY_META_KEY = "dev.hfw.REPLY"

    /** Not a valid package name: a launcher query for it always returns an empty list (= request rejected). */
    const val IPC_REJECTED_PACKAGE = "__hfw_ipc_rejected__"

    /** system_server (uid 1000) pulls icon bytes from the manager through this provider. */
    const val PROVIDER_AUTHORITY = "$PACKAGE.rules"
    const val PROVIDER_METHOD_ICON = "icon"
    const val PROVIDER_EXTRA_PNG = "png"
    const val PROVIDER_EXTRA_REV = "rev"

    /** Wire protocol between manager and hook. Changes are additive only; see docs. */
    const val PROTOCOL = 1

    /** Version of the on-disk `state` schema. */
    const val SCHEMA = 1
}
