package dev.hfw.core

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Binder
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Custom names. Every method of the framework's package-info generator that returns an ApplicationInfo, ActivityInfo
 * or PackageInfo gets the custom label (written to a COPY via nonLocalizedLabel, never to cached objects). PackageInfo's
 * lastUpdateTime is raised for launcher callers only, as a cache hint. The manager itself always sees real names.
 *
 * Android 11+: PackageInfoUtils. Android 8-10: the static generate* methods live in PackageParser (untested path).
 */
internal object NameHooks {
    private const val GROUP = "Names"
    private val rewriteErrorLogged = AtomicBoolean(false)

    private inline fun guardRewrite(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (rewriteErrorLogged.compareAndSet(false, true)) HealthLog.event("NameHooks rewrite failed", t)
        }
    }

    fun install(cl: ClassLoader): Int {
        var total = 0
        for (name in listOf("com.android.server.pm.parsing.PackageInfoUtils", "android.content.pm.PackageParser")) {
            val cls = runCatching { cl.loadClass(name) }.getOrNull() ?: continue
            val shortName = name.substringAfterLast('.')
            val n = hookGenerators(cls)
            if (n[2] == 0 && shortName == "PackageInfoUtils") {
                val ce = runCatching { cl.loadClass("com.android.server.pm.ComputerEngine") }.getOrNull()
                if (ce != null) n[2] = hookComputerEnginePackageInfo(ce)
            }
            HealthLog.hook("$GROUP.$shortName.ApplicationInfo", if (n[0] > 0) HealthLog.OK else HealthLog.FAILED)
            HealthLog.hook("$GROUP.$shortName.ActivityInfo", if (n[1] > 0) HealthLog.OK else HealthLog.FAILED)
            HealthLog.hook("$GROUP.$shortName.PackageInfo", if (n[2] > 0) HealthLog.OK else HealthLog.FAILED)
            HealthLog.event("$name -> ${n[0]} ApplicationInfo, ${n[1]} ActivityInfo, ${n[2]} PackageInfo methods")
            total += n.sum()
            if (total > 0) break // the first class that works is the one this Android version uses
        }
        if (total == 0) {
            HealthLog.hook("$GROUP.generators", HealthLog.FAILED)
            HealthLog.event("no package info generator found: names will not change on this Android version")
        }
        return total
    }

    private fun packageInfoCallback(): HookCallback = object : HookCallback() {
        override fun after(param: HookParam) = guardRewrite {
            val r = param.result
            if (r is PackageInfo) param.setResult(rewritePackage(r))
        }
    }

    private fun hookComputerEnginePackageInfo(clazz: Class<*>): Int {
        val be = Runtime.be()
        var count = 0
        val pkgCb = packageInfoCallback()
        for (m in clazz.declaredMethods) {
            if (Modifier.isAbstract(m.modifiers) || m.isSynthetic || m.isBridge) continue
            if (m.name == "generatePackageInfo" && m.returnType == PackageInfo::class.java) {
                if (hookOne(be, m, pkgCb)) count++
            }
        }
        return count
    }

    /** Matches by return type so renamed or overloaded generate* methods are all covered. */
    private fun hookGenerators(clazz: Class<*>): IntArray {
        val be = Runtime.be()
        val counts = IntArray(3)
        val appCb = object : HookCallback() {
            override fun after(param: HookParam) = guardRewrite {
                val r = param.result
                if (r is ApplicationInfo) param.setResult(rewriteApp(r))
            }
        }
        val actCb = object : HookCallback() {
            override fun after(param: HookParam) = guardRewrite {
                val r = param.result
                if (r is ActivityInfo) param.setResult(rewriteActivity(r))
            }
        }
        val pkgCb = packageInfoCallback()
        for (m in clazz.declaredMethods) {
            if (Modifier.isAbstract(m.modifiers) || m.isSynthetic || m.isBridge) continue
            val slot: Int
            val cb: HookCallback
            when (m.returnType) {
                ApplicationInfo::class.java -> { slot = 0; cb = appCb }
                ActivityInfo::class.java -> { slot = 1; cb = actCb }
                PackageInfo::class.java -> { slot = 2; cb = pkgCb }
                else -> continue
            }
            if (hookOne(be, m, cb)) counts[slot]++
        }
        return counts
    }

    private fun hookOne(be: HookBackend, m: Method, cb: HookCallback): Boolean = try {
        be.hookMethod(m, cb)
        true
    } catch (t: Throwable) {
        HealthLog.event("hook failed: ${m.declaringClass.name}.${m.name}", t)
        false
    }

    // ---- rewriting ---------------------------------------------------------------------------------------------

    /** The custom label for [pkg] in [userId] as the current caller should see it; null = leave the object alone. */
    private fun labelFor(pkg: String?, userId: Int): String? {
        if (pkg == null) return null
        val label = StateStore.snapshot.labelOf(pkg, userId) ?: return null
        if (Callers.inSelfScope()) return null // the manager is asking through LauncherApps: real names
        if (Callers.isVerifying()) return null // the caller check itself reads HFW' package info: never recurse
        if (Callers.isSelf(Binder.getCallingUid(), null)) return null
        return label
    }

    private fun rewriteApp(ai: ApplicationInfo): ApplicationInfo {
        val label = labelFor(ai.packageName, ai.uid / 100_000) ?: return ai
        // labelRes stays untouched on purpose: nonLocalizedLabel wins in loadLabel()
        return ApplicationInfo(ai).also { it.nonLocalizedLabel = label }
    }

    private fun rewriteActivity(ai: ActivityInfo): ActivityInfo {
        val label = labelFor(ai.packageName, (ai.applicationInfo?.uid ?: 0) / 100_000) ?: return ai
        if (!labelApplies(ai)) return ai // activity-specific labels (e.g. "New message") are left alone
        return ActivityInfo(ai).also { it.nonLocalizedLabel = label }
    }

    private fun rewritePackage(pi: PackageInfo): PackageInfo {
        val snap = StateStore.snapshot
        val userId = (pi.applicationInfo?.uid ?: 0) / 100_000
        val changedAt = maxOf(
            snap.apps[pi.packageName]?.changedAt ?: 0L,
            snap.icons[userId]?.get(pi.packageName)?.changedAt ?: 0L,
        )
        // The raised lastUpdateTime is only a cache hint for launchers; everyone else keeps seeing the real install time.
        if (changedAt > pi.lastUpdateTime && Callers.isLauncher(Binder.getCallingUid())) {
            pi.lastUpdateTime = changedAt
        }
        pi.applicationInfo?.let { pi.applicationInfo = rewriteApp(it) }
        return pi
    }

    /** Activity-specific labels are left alone; only those matching the app's own apply. */
    fun labelApplies(info: ActivityInfo): Boolean = info.labelRes == 0 || info.labelRes == info.applicationInfo?.labelRes
}
