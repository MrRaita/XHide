package dev.hfw.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.UserHandle
import dev.hfw.Identity
import dev.hfw.data.DurableFiles
import dev.hfw.state.Limits
import dev.hfw.state.Overlay
import java.io.File

/**
 * system_server only. Custom icons are FabricatedOverlays (runtime resource overlays) on the target app's launcher
 * icon resource, so every process that loads that resource - launcher, Settings, apps that list installed apps -
 * sees the new icon without being in the module's scope and without any client-side hook.
 *
 * Android 14+ only (FabricatedOverlay values from file descriptors). All platform classes are reached by reflection.
 * The ledger (which overlay is registered at which revision) lives in [StateStore], not here.
 */
internal class IconOverlays {
    class Outcome(val status: Int, val rev: Long)

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 34

    /** idmap2d died on our file descriptor (SELinux). Reset per round with [beginRound]; every package would fail alike. */
    @Volatile
    var blocked = false
        private set

    fun beginRound() {
        blocked = false
    }

    private fun iconDir() = File(StateStore.dir, "icons")

    private class Api(
        val builderCtor: java.lang.reflect.Constructor<*>,
        val setValue: java.lang.reflect.Method,
        val build: java.lang.reflect.Method,
        val getIdentifier: java.lang.reflect.Method,
        val idCtor: java.lang.reflect.Constructor<*>,
        val txBuilderCtor: java.lang.reflect.Constructor<*>,
        val txRegister: java.lang.reflect.Method,
        val txUnregister: java.lang.reflect.Method,
        val txSetEnabled: java.lang.reflect.Method,
        val txBuild: java.lang.reflect.Method,
        val commit: java.lang.reflect.Method,
        val getInfo: java.lang.reflect.Method?,
        val isEnabled: java.lang.reflect.Method?,
    )

    private val api: Api? by lazy {
        runCatching {
            val fo = Class.forName("android.content.om.FabricatedOverlay")
            val fb = Class.forName("android.content.om.FabricatedOverlay\$Builder")
            val id = Class.forName("android.content.om.OverlayIdentifier")
            val tx = Class.forName("android.content.om.OverlayManagerTransaction")
            val tb = Class.forName("android.content.om.OverlayManagerTransaction\$Builder")
            val om = Class.forName("android.content.om.OverlayManager")
            val info = runCatching { Class.forName("android.content.om.OverlayInfo") }.getOrNull()
            Api(
                builderCtor = fb.getConstructor(String::class.java, String::class.java, String::class.java),
                setValue = fb.getMethod("setResourceValue", String::class.java, ParcelFileDescriptor::class.java, String::class.java),
                build = fb.getMethod("build"),
                getIdentifier = fo.getMethod("getIdentifier"),
                idCtor = id.getConstructor(String::class.java, String::class.java),
                txBuilderCtor = tb.getConstructor(),
                txRegister = tb.getMethod("registerFabricatedOverlay", fo),
                txUnregister = tb.getMethod("unregisterFabricatedOverlay", id),
                txSetEnabled = tb.getMethod("setEnabled", id, java.lang.Boolean.TYPE, Integer.TYPE),
                txBuild = tb.getMethod("build"),
                commit = om.getMethod("commit", tx),
                getInfo = info?.let { runCatching { om.getMethod("getOverlayInfo", id, UserHandle::class.java) }.getOrNull() },
                isEnabled = info?.let { runCatching { it.getMethod("isEnabled") }.getOrNull() },
            )
        }.onFailure { HealthLog.event("overlay API not available on this Android version", it) }.getOrNull()
    }

    private fun overlayName(pkg: String, scopedUserId: Int? = null) =
        if (scopedUserId != null) "hfw_u${scopedUserId}_" + pkg.replace('.', '_')
        else "hfw_" + pkg.replace('.', '_')

    private fun iconFileName(pkg: String, scopedUserId: Int? = null) =
        if (scopedUserId != null) "${scopedUserId}_$pkg.png" else "$pkg.png"

    private fun overlayManager(ctx: Context): Any? = ctx.getSystemService("overlay")

    fun isEnabled(ctx: Context, pkg: String, user: UserHandle, scopedUserId: Int? = null): Boolean {
        val a = api ?: return false
        val om = overlayManager(ctx) ?: return false
        val gi = a.getInfo ?: return false
        val en = a.isEnabled ?: return false
        return runCatching {
            val id = a.idCtor.newInstance("android", overlayName(pkg, scopedUserId))
            val info = gi.invoke(om, id, user) ?: return false
            en.invoke(info) as Boolean
        }.getOrDefault(false)
    }

    /** Builds (or rebuilds) the overlay for [pkg] from the icon the manager provides. Must run off the binder thread. */
    fun apply(ctx: Context, pkg: String, users: List<UserHandle>, scopedUserId: Int? = null): Outcome {
        if (!supported) return Outcome(Overlay.UNSUPPORTED, 0L)
        val a = api ?: return Outcome(Overlay.UNSUPPORTED, 0L)
        if (blocked) return Outcome(Overlay.BLOCKED, 0L)
        val identity = Binder.clearCallingIdentity()
        try {
            val om = overlayManager(ctx) ?: run {
                HealthLog.event("overlay service not available")
                return Outcome(Overlay.FAILED, 0L)
            }
            val res = runCatching { iconResources(ctx, pkg, users) }
                .onFailure { HealthLog.event("$pkg: icon resource lookup failed", it) }.getOrNull()
                ?.takeIf { it.isNotEmpty() } ?: return Outcome(Overlay.FAILED, 0L)
            val fetchArg = if (scopedUserId != null) "$scopedUserId:$pkg" else pkg
            val (png, rev) = fetchIcon(ctx, fetchArg, pkg) ?: return Outcome(Overlay.FAILED, 0L)
            val dir = iconDir()
            dir.mkdirs()
            dir.setExecutable(true, false)
            val file = File(dir, iconFileName(pkg, scopedUserId))
            DurableFiles.writeAtomically(file.path, png, worldReadable = true)

            val sdk = Build.VERSION.SDK_INT
            // every density with the current SDK qualifier: beats whatever variant the app ships (mdpi..xxxhdpi, anydpi-v26)
            val full = listOf("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "anydpi", "nodpi").map { "$it-v$sdk" }
            val ovName = overlayName(pkg, scopedUserId)

            var id: Any?
            try {
                id = register(a, om, pkg, ovName, res, file, full)
            } catch (t: Throwable) {
                HealthLog.event("overlay register failed for $pkg (${full.size} configs): ${root(t)}")
                if (root(t).contains("DeadObject")) {
                    // idmap2d is killed on the fd by SELinux; every package would fail the same way
                    blocked = true
                    HealthLog.event("SELinux blocks icon overlays: install the ${Identity.APP_NAME} SELinux module")
                    return Outcome(Overlay.BLOCKED, 0L)
                }
                try {
                    id = register(a, om, pkg, ovName, res, file, listOf(null))
                    HealthLog.event("overlay for $pkg works with a single unqualified entry only")
                } catch (t2: Throwable) {
                    HealthLog.event("overlay register failed for $pkg even with one entry: ${root(t2)}")
                    return Outcome(Overlay.FAILED, 0L)
                }
            }
            // the overlay is registered once for everybody; enabling is per user (personal profile, work profile, clones)
            val be = Runtime.be()
            var enabledFor = 0
            for (user in users) {
                val uid = userIdOf(user, be)
                try {
                    val tx = a.txBuilderCtor.newInstance()
                    a.txSetEnabled.invoke(tx, id, true, uid)
                    a.commit.invoke(om, a.txBuild.invoke(tx))
                    if (a.getInfo != null && a.isEnabled != null && !isEnabled(ctx, pkg, user, scopedUserId)) {
                        HealthLog.event("overlay not active for $pkg in user $uid (no idmap)")
                    } else {
                        enabledFor++
                    }
                } catch (t: Throwable) {
                    HealthLog.event("overlay enable failed for $pkg in user $uid: ${root(t)}")
                }
            }
            if (enabledFor == 0) {
                runCatching { remove(ctx, pkg, scopedUserId) }
                return Outcome(Overlay.FAILED, 0L)
            }
            HealthLog.event("icon overlay applied: $pkg -> ${res.joinToString(" + ")} (rev $rev, $enabledFor user(s))")
            return Outcome(Overlay.OK, rev)
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    fun remove(ctx: Context, pkg: String, scopedUserId: Int? = null) {
        val a = api ?: return
        val om = overlayManager(ctx) ?: return
        val identity = Binder.clearCallingIdentity()
        try {
            val id = a.idCtor.newInstance("android", overlayName(pkg, scopedUserId))
            val tx = a.txBuilderCtor.newInstance()
            a.txUnregister.invoke(tx, id)
            a.commit.invoke(om, a.txBuild.invoke(tx))
            HealthLog.event("icon overlay removed: $pkg${if (scopedUserId != null) " (user $scopedUserId)" else ""}")
        } catch (t: Throwable) {
            HealthLog.event("removing overlay of $pkg failed: ${root(t)}")
        } finally {
            runCatching { File(iconDir(), iconFileName(pkg, scopedUserId)).delete() }
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun fetchIcon(ctx: Context, arg: String, pkg: String): Pair<ByteArray, Long>? {
        val b = ctx.contentResolver.call(Uri.parse("content://${Identity.PROVIDER_AUTHORITY}"), Identity.PROVIDER_METHOD_ICON, arg, null)
        val png = b?.getByteArray(Identity.PROVIDER_EXTRA_PNG)
        if (png == null || png.isEmpty() || png.size > Limits.MAX_ICON_BYTES) {
            HealthLog.event("manager returned no usable icon for $pkg")
            return null
        }
        return png to (b.getLong(Identity.PROVIDER_EXTRA_REV, 0L))
    }

    /**
     * Resource names ("pkg:mipmap/ic_launcher") of every icon the launcher can show for [pkg]: the application icon plus
     * the icon of each enabled launcher entry (activities and aliases often declare their own android:icon).
     */
    private fun iconResources(ctx: Context, pkg: String, users: List<UserHandle>): List<String> {
        val pm = ctx.packageManager
        val be = Runtime.be()
        // the app may exist only in the work profile: ask per user instead of assuming user 0
        var ai: android.content.pm.ApplicationInfo? = null
        for (u in users) {
            ai = runCatching { be.callMethod(pm, "getApplicationInfoAsUser", pkg, 0, userIdOf(u, be)) as? android.content.pm.ApplicationInfo }.getOrNull()
            if (ai != null) break
        }
        if (ai == null) ai = pm.getApplicationInfo(pkg, 0)
        val res = pm.getResourcesForApplication(ai!!)
        val ids = LinkedHashSet<Int>()
        if (ai.icon != 0) ids.add(ai.icon)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
        val entries = ArrayList<android.content.pm.ResolveInfo>()
        for (u in users) {
            @Suppress("UNCHECKED_CAST")
            val r = runCatching { be.callMethod(pm, "queryIntentActivitiesAsUser", intent, PackageManager.MATCH_DISABLED_COMPONENTS, userIdOf(u, be)) as? List<android.content.pm.ResolveInfo> }
                .onFailure { HealthLog.event("$pkg: launcher entry lookup failed", it) }.getOrNull()
            if (r != null) entries.addAll(r)
        }
        for (ri in entries) {
            val info = ri.activityInfo ?: continue
            // disabled aliases are other logos of the app: leave them alone
            if (info.icon != 0 && info.enabled) ids.add(info.icon)
        }
        if (ids.isEmpty()) {
            HealthLog.event("$pkg has no icon resource; nothing to overlay")
            return emptyList()
        }
        return ids.mapNotNull { id ->
            runCatching { res.getResourceName(id) }.onFailure { HealthLog.event("$pkg: no name for icon 0x${Integer.toHexString(id)}", it) }.getOrNull()
        }.distinct()
    }

    /** Builds the overlay from [file] (one fd per config) and registers it; returns its OverlayIdentifier. */
    private fun register(a: Api, om: Any, pkg: String, ovName: String, res: List<String>, file: File, configs: List<String?>): Any? {
        val fds = ArrayList<ParcelFileDescriptor>()
        try {
            val builder = a.builderCtor.newInstance("android", ovName, pkg)
            for (r in res) for (c in configs) {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                fds.add(fd)
                a.setValue.invoke(builder, r, fd, c)
            }
            val overlay = a.build.invoke(builder)
            val tx = a.txBuilderCtor.newInstance()
            a.txRegister.invoke(tx, overlay)
            a.commit.invoke(om, a.txBuild.invoke(tx))
            return a.getIdentifier.invoke(overlay)
        } finally {
            fds.forEach { runCatching { it.close() } }
        }
    }

    private fun root(t: Throwable): String {
        var c: Throwable = t
        while (c.cause != null && c.cause !== c) c = c.cause!!
        return c.toString()
    }
}
