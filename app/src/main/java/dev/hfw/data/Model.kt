package dev.hfw.data

import android.os.Build
import dev.hfw.state.Overlay

enum class AppProfile { MAIN, WORK, CLONE, PRIVATE }

/** Filter chips on the Apps screen. Several can be active at once (union). */
enum class AppFilter { ICON_CHANGED, RENAMED, HIDDEN }

enum class AppSort { CHANGED_FIRST, NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, INSTALLED_NEWEST, UPDATED_NEWEST }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class LanguageMode { SYSTEM, TURKISH, ENGLISH }

enum class IconSize(val px: Int) { PX_192(192), PX_256(256), PX_512(512) }

enum class ActivityKind { HIDDEN, UNHIDDEN, RENAMED, ICON_CHANGED, RESET }

/** One app in one profile. Hiding is per profile; name and icon belong to the package (all profiles). */
data class AppItem(
    val userId: Int,
    val pkg: String,
    val label: String,
    val sizeMb: Float,
    val installedAt: Long,
    val updatedAt: Long,
    val version: String,
    val profile: AppProfile = AppProfile.MAIN,
    val isSystem: Boolean = false,
    val isModule: Boolean = false,
    val customLabel: String? = null,
    /** 0 = original icon, otherwise the revision of the custom icon the user chose. */
    val iconRev: Long = 0L,
    /** What the hook reports for that icon: [Overlay.OK] once applied, FAILED/BLOCKED when it could not be. */
    val iconStatus: Int = Overlay.NONE,
    val hidden: Boolean = false,
    val lookProtected: Boolean = false,
    /** Core system app: name/icon can be changed after the user confirmed a warning. */
    val lookRisky: Boolean = false,
    /** Has any user customization: a custom name or icon, or an explicit hide/show choice. */
    val modified: Boolean = false,
    /** Changes whenever the icon on screen must be reloaded (custom icon revision or the hook's overlay state). */
    val iconToken: Long = 0L,
) {
    val key: String get() = "$userId:$pkg"
    val displayLabel: String get() = customLabel ?: label
    val renamed: Boolean get() = customLabel != null
    val iconChanged: Boolean get() = iconRev > 0L
}

data class ActivityEntry(
    val kind: ActivityKind,
    val pkg: String,
    val label: String,
    val at: Long,
    val key: String = "",
)

/** What the running Android version can do: name 8+, hiding 11+, icons 14+. Unsupported features are hidden from the UI. */
object Caps {
    val name: Boolean get() = true
    val hide: Boolean get() = Build.VERSION.SDK_INT >= 30
    val icon: Boolean get() = Build.VERSION.SDK_INT >= 34
}

fun AppFilter.supported(): Boolean = when (this) {
    AppFilter.HIDDEN -> Caps.hide
    AppFilter.ICON_CHANGED -> Caps.icon
    AppFilter.RENAMED -> Caps.name
}

/** State of the SELinux rule module as far as an unprivileged app can tell. */
enum class ModuleState { UNKNOWN, VERIFIED, MISSING }

/** What the selection bar can do to all selected apps at once. */
enum class BulkAction { RESET_ALL, RESET_NAME, RESET_ICON, HIDE, SHOW }
