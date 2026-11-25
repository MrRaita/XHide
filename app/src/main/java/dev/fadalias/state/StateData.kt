package dev.fadalias.state

import dev.fadalias.Identity

object Overlay {
    const val NONE = 0
    const val OK = 1
    const val FAILED = 2
    /** SELinux refused the overlay (idmap2d may not read system_server's file descriptors). */
    const val BLOCKED = 3
    const val UNSUPPORTED = 4
}

/** What is known about one package's look. Name and icon are global (not per user) in this version. */
data class AppRule(
    val label: String? = null,
    /** Desired icon revision; 0 = original icon. The PNG itself lives in the manager (provider) and in `icons/`. */
    val iconRev: Long = 0L,
    /** Last change time (ms). A rule that was reset keeps this as a short-lived tombstone so launchers drop cached entries. */
    val changedAt: Long = 0L,
    /** Revision of the overlay that is registered right now; 0 = none. */
    val overlayRev: Long = 0L,
    val overlayStatus: Int = Overlay.NONE,
) {
    val isActive: Boolean get() = label != null || iconRev > 0L
    val isTombstone: Boolean get() = !isActive && overlayRev == 0L
}

/**
 * The single published unit of state. Immutable: readers on Binder threads take the reference once and never lock.
 * [hide] is a nested map by user id: package -> explicit choice (true = hidden, false = shown). No entry means the
 * default applies (Xposed modules are auto-hidden when [autoHide] is on).
 */
data class StateData(
    val rev: Long = 0L,
    val autoHide: Boolean = true,
    val autoHideClone: Boolean = true,
    val hideIntentChooser: Boolean = true,
    val hideShortcuts: Boolean = true,
    val hideShortcutConfigs: Boolean = true,
    val hideShareTargets: Boolean = true,
    val hide: Map<Int, Map<String, Boolean>> = emptyMap(),
    val apps: Map<String, AppRule> = emptyMap(),
    val labels: Map<Int, Map<String, String>> = emptyMap(),
    val icons: Map<Int, Map<String, AppRule>> = emptyMap(),
) {
    fun shouldHide(pkg: String, userId: Int, isModule: Boolean): Boolean {
        val explicit = hide[userId]?.get(pkg)
        if (explicit != null) return explicit
        if (pkg == Identity.PACKAGE) return false // the manager is never auto-hidden
        if (!isModule) return false
        return if (userId == 0) autoHide else autoHide && autoHideClone
    }

    /** Packages hidden for [userId], given the modules detected so far. */
    fun hiddenFor(userId: Int, detectedModules: Set<String>): List<String> {
        val out = LinkedHashSet<String>()
        val explicit = hide[userId].orEmpty()
        for ((pkg, h) in explicit) if (h) out.add(pkg)
        for (pkg in detectedModules) if (pkg !in explicit && shouldHide(pkg, userId, true)) out.add(pkg)
        return out.toList()
    }

    fun ruleOf(pkg: String): AppRule? = apps[pkg]
    fun iconRuleOf(pkg: String, userId: Int): AppRule? = icons[userId]?.get(pkg) ?: apps[pkg]
    fun labelOf(pkg: String): String? = apps[pkg]?.label
    fun labelOf(pkg: String, userId: Int): String? = labels[userId]?.get(pkg) ?: apps[pkg]?.label

    val explicitHideCount: Int get() = hide.values.sumOf { it.size }
}
