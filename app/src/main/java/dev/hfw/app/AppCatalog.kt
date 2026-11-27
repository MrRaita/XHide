package dev.hfw.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import dev.hfw.core.ModuleCache
import dev.hfw.data.AppProfile
import java.io.File

/** Reads the installed apps of every reachable profile. Runs as the manager, so names are the real ones. */
class AppCatalog(private val ctx: Context, private val client: HookClient) {
    class Entry(
        val userId: Int,
        val pkg: String,
        val label: String,
        val sizeMb: Float,
        val installedAt: Long,
        val updatedAt: Long,
        val version: String,
        val profile: AppProfile,
        val isSystem: Boolean,
        val isModule: Boolean,
        val hasLauncher: Boolean,
    ) {
        val key: String get() = "$userId:$pkg"
    }

    class Result(val entries: List<Entry>, val inventoryOk: Boolean)

    @Volatile
    private var infos: Map<String, LauncherActivityInfo> = emptyMap()
    private val moduleCache = HashMap<String, Boolean>()

    private val ownUser get() = Process.myUid() / 100_000

    private fun userIdOf(h: UserHandle): Int = try {
        UserHandle::class.java.getMethod("getIdentifier").invoke(h) as Int
    } catch (_: Throwable) {
        h.hashCode()
    }

    fun load(): Result {
        val pm = ctx.packageManager
        val la = ctx.getSystemService(LauncherApps::class.java)
        val um = ctx.getSystemService(UserManager::class.java)
        val profiles = LinkedHashSet<UserHandle>()
        profiles.add(Process.myUserHandle())
        try { um?.userProfiles?.let { profiles.addAll(it) } } catch (_: Throwable) {}

        val activities = ArrayList<LauncherActivityInfo>()
        for (p in profiles) try { la?.getActivityList(null, p)?.let { activities.addAll(it) } } catch (_: Throwable) {}
        if (profiles.size <= 1) activities.addAll(client.allUsers().filter { userIdOf(it.user) != ownUser })

        val out = LinkedHashMap<String, Entry>()
        val infoMap = HashMap<String, LauncherActivityInfo>()
        for (a in activities) {
            val ai = a.applicationInfo ?: continue
            val pkg = ai.packageName ?: continue
            if (pkg == "android") continue
            val uid = userIdOf(a.user)
            val key = "$uid:$pkg"
            if (out.containsKey(key)) continue
            // getPackageInfo only sees the manager's own profile; for another profile it would return the personal copy
            // (or nothing). There, read the version from the APK itself and take its file time as the update time.
            val own = uid == ownUser
            val pi = if (own) try { pm.getPackageInfo(pkg, 0) } catch (_: Throwable) { null } else null
            val arch = if (own) null else archiveInfo(pm, ai)
            val installed = pi?.firstInstallTime ?: a.firstInstallTime
            out[key] = Entry(
                userId = uid, pkg = pkg,
                label = try { a.label?.toString() ?: pkg } catch (_: Throwable) { pkg },
                sizeMb = sizeMb(ai), installedAt = installed, updatedAt = pi?.lastUpdateTime ?: maxOf(installed, arch?.second ?: 0L),
                version = pi?.versionName ?: arch?.first ?: "—",
                profile = profileOf(uid, a.user, la),
                isSystem = isSystem(ai), isModule = isModule(ai, pm), hasLauncher = true,
            )
            infoMap[key] = a
        }
        infos = infoMap

        // headless Xposed modules have no launcher activity but still need to be listed (and auto-hidden)
        val installed: List<ApplicationInfo>? = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA).takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
        for (ai in installed.orEmpty()) {
            val pkg = ai.packageName ?: continue
            val key = "$ownUser:$pkg"
            if (pkg == "android" || out.containsKey(key) || !isModule(ai, pm)) continue
            val pi = try { pm.getPackageInfo(pkg, 0) } catch (_: Throwable) { null }
            out[key] = Entry(
                userId = ownUser, pkg = pkg,
                label = try { pm.getApplicationLabel(ai).toString() } catch (_: Throwable) { pkg },
                sizeMb = sizeMb(ai), installedAt = pi?.firstInstallTime ?: 0L, updatedAt = pi?.lastUpdateTime ?: 0L,
                version = pi?.versionName ?: "—", profile = AppProfile.MAIN,
                isSystem = isSystem(ai), isModule = true, hasLauncher = false,
            )
        }
        return Result(out.values.toList(), installed != null)
    }

    /** Whether [pkg] is installed for the manager's own user (used to decide if a stale rule may be forgotten). */
    fun isInstalledHere(pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Throwable) {
        true
    }

    fun original(key: String, px: Int): Bitmap? {
        val d: Drawable = try {
            infos[key]?.getIcon(0) ?: ctx.packageManager.getApplicationIcon(key.substringAfter(':'))
        } catch (_: Throwable) {
            return null
        }
        return try {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, px, px)
            d.draw(c)
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    /** Version name and APK file time of an app that is not installed for the manager's own profile. */
    private fun archiveInfo(pm: PackageManager, ai: ApplicationInfo): Pair<String?, Long>? {
        val path = ai.sourceDir ?: return null
        return try {
            Pair(pm.getPackageArchiveInfo(path, 0)?.versionName, File(path).lastModified())
        } catch (_: Throwable) {
            null
        }
    }

    private fun sizeMb(ai: ApplicationInfo): Float {
        var n = 0L
        try { ai.sourceDir?.let { n += File(it).length() } } catch (_: Throwable) {}
        try { ai.splitSourceDirs?.forEach { n += File(it).length() } } catch (_: Throwable) {}
        return n / (1024f * 1024f)
    }

    private fun isSystem(ai: ApplicationInfo) =
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

    private fun profileOf(userId: Int, handle: UserHandle, la: LauncherApps?): AppProfile {
        if (userId == ownUser) return AppProfile.MAIN
        if (Build.VERSION.SDK_INT >= 31) try {
            val type = la?.getLauncherUserInfo(handle)?.userType.orEmpty()
            when {
                type.contains("CLONE", true) -> return AppProfile.CLONE
                type.contains("PRIVATE", true) -> return AppProfile.PRIVATE
                type.contains("MANAGED", true) -> return AppProfile.WORK
            }
        } catch (_: Throwable) {}
        return if (userId in 900..999) AppProfile.CLONE else AppProfile.WORK
    }

    private fun isModule(ai: ApplicationInfo, pm: PackageManager): Boolean {
        val pkg = ai.packageName ?: return false
        val cacheKey = "$pkg|${ai.sourceDir}"
        synchronized(moduleCache) { moduleCache[cacheKey]?.let { return it } }
        var meta = ai.metaData
        if (meta == null) meta = try { pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA).metaData } catch (_: Throwable) { null }
        var result = ModuleCache.hasXposedMetadata(meta)
        if (!result) {
            result = ModuleCache.hasXposedArchiveMarkers(ai) == true
        }
        synchronized(moduleCache) { if (moduleCache.size > 2048) moduleCache.clear(); moduleCache[cacheKey] = result }
        return result
    }
}
