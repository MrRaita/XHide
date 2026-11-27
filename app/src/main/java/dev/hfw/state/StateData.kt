package dev.hfw.state

import dev.hfw.Identity

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
object HideScope {
    const val LAUNCHER = 1 shl 0
    const val SUGGESTIONS = 1 shl 1
    const val SHORTCUT_CONFIGS = 1 shl 2
    const val SHORTCUTS = 1 shl 3
    const val INTENT_CHOOSER = 1 shl 4
    const val SHARE_TARGETS = 1 shl 5
    const val WIDGETS = 1 shl 6
    const val ALL = LAUNCHER or SUGGESTIONS or SHORTCUT_CONFIGS or SHORTCUTS or INTENT_CHOOSER or SHARE_TARGETS or WIDGETS
}

data class StateData(
    val rev: Long = 0L,
    val autoHide: Boolean = true,
    val autoHideClone: Boolean = true,
    val hideIntentChooser: Boolean = false,
    val hideShortcuts: Boolean = true,
    val hideShortcutConfigs: Boolean = true,
    val hideShareTargets: Boolean = false,
    val hideWidgets: Boolean = true,
    val hide: Map<Int, Map<String, Boolean>> = emptyMap(),
    val hideScopes: Map<Int, Map<String, Int>> = emptyMap(),
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

    fun defaultHideMask(): Int {
        var mask = HideScope.LAUNCHER or HideScope.SUGGESTIONS
        if (hideShortcutConfigs) mask = mask or HideScope.SHORTCUT_CONFIGS
        if (hideShortcuts) mask = mask or HideScope.SHORTCUTS
        if (hideIntentChooser) mask = mask or HideScope.INTENT_CHOOSER
        if (hideShareTargets) mask = mask or HideScope.SHARE_TARGETS
        if (hideWidgets) mask = mask or HideScope.WIDGETS
        return mask
    }

    fun effectiveHideMask(pkg: String, userId: Int, isModule: Boolean): Int {
        hideScopes[userId]?.get(pkg)?.let { return it }
        return if (shouldHide(pkg, userId, isModule)) defaultHideMask() else 0
    }

    fun shouldHideScope(pkg: String, userId: Int, isModule: Boolean, scope: Int): Boolean {
        val explicitMask = hideScopes[userId]?.get(pkg)
        if (explicitMask != null) return (explicitMask and scope) != 0
        val globalAllowed = when (scope) {
            HideScope.INTENT_CHOOSER -> hideIntentChooser
            HideScope.SHORTCUTS -> hideShortcuts
            HideScope.SHORTCUT_CONFIGS -> hideShortcutConfigs
            HideScope.SHARE_TARGETS -> hideShareTargets
            HideScope.WIDGETS -> hideWidgets
            else -> true
        }
        return globalAllowed && shouldHide(pkg, userId, isModule)
    }

    /** Packages hidden from the launcher for [userId], given the modules detected so far. */
    fun hiddenFor(userId: Int, detectedModules: Set<String>): List<String> {
        val out = LinkedHashSet<String>()
        val scoped = hideScopes[userId].orEmpty()
        for ((pkg, mask) in scoped) if ((mask and HideScope.LAUNCHER) != 0) out.add(pkg)
        val explicit = hide[userId].orEmpty()
        for ((pkg, h) in explicit) if (h && pkg !in scoped) out.add(pkg)
        for (pkg in detectedModules) if (pkg !in scoped && pkg !in explicit && shouldHide(pkg, userId, true)) out.add(pkg)
        return out.toList()
    }

    fun ruleOf(pkg: String): AppRule? = apps[pkg]
    fun iconRuleOf(pkg: String, userId: Int): AppRule? = icons[userId]?.get(pkg) ?: apps[pkg]
    fun labelOf(pkg: String): String? = apps[pkg]?.label
    fun labelOf(pkg: String, userId: Int): String? = labels[userId]?.get(pkg) ?: apps[pkg]?.label

    val explicitHideCount: Int get() = hide.values.sumOf { it.size } + hideScopes.values.sumOf { it.size }
}
