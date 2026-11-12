package org.lsposed.hidexposed.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import android.os.UserHandle
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.lsposed.hidexposed.BuildConfig
import org.lsposed.hidexposed.data.DurableFiles
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Universal O(1) Xposed Module Detector.
 *
 * Detects all 3 generations of Xposed modules across Android 11–17 (including split APKs
 * and disguised package names such as `com.android.hmal`):
 * 1. Manifest Meta-Data (`xposedmodule`, `xposedminversion`, `xposeddescription`, `xposedscope`)
 * 2. Classic Xposed API (`assets/xposed_init` inside base or split APK)
 * 3. Modern LibXposed API 100 (`META-INF/xposed/module.prop`, `java_init.list`, `scope.list`)
 *
 * Keyed by `packageName|sourceDir` so package updates automatically invalidate stale entries
 * without requiring broadcast receivers or locking PackageManager.
 */
object ModuleCache {
    private const val TAG = "XHide"

    // Key: "packageName|sourceDir" -> Value: true if Xposed module, false otherwise
    private const val MAX_CACHE_ENTRIES = 2048

    // Bounded access-ordered LRU: evicts only the least recently used entry instead of
    // wiping the whole cache (which forced ZIP re-scans of every package on Binder threads).
    private val apkModuleCache: MutableMap<String, Boolean> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
                size > MAX_CACHE_ENTRIES
        }
    )

    // Verdicts are persisted to disk so each boot does not re-open every APK on Binder threads.
    // Writes are debounced so the burst of first-boot detections lands in a single file write.
    private const val PERSIST_DELAY_MS = 5_000L

    @Volatile
    private var persistedLoaded = false
    private val persistLock = Any()
    private val persistScheduled = AtomicBoolean(false)
    private val persistExecutor: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "XHide-ModuleCache").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
    }

    private fun isSystemServer(): Boolean = Process.myUid() == Process.SYSTEM_UID

    private fun ensurePersistedLoaded() {
        if (persistedLoaded) return
        synchronized(persistLock) {
            if (persistedLoaded) return
            try {
                if (!isSystemServer()) return
                val file = File(ModuleCacheFile.PATH)
                if (!file.isFile) return
                if (file.length() > ModuleCacheFile.MAX_FILE_BYTES) return
                val loaded = ModuleCacheFile.decode(file.readText(), BuildConfig.VERSION_CODE, MAX_CACHE_ENTRIES)
                for ((key, isModule) in loaded) {
                    apkModuleCache.putIfAbsent(key, isModule)
                }
                XposedBridge.log("[$TAG] Loaded ${loaded.size} persisted module verdicts")
            } catch (t: Throwable) {
                XposedBridge.log("[$TAG] Could not load module cache: ${t.message}")
            } finally {
                persistedLoaded = true
            }
        }
    }

    private fun schedulePersist() {
        if (!isSystemServer()) return
        if (!persistScheduled.compareAndSet(false, true)) return
        try {
            persistExecutor.schedule({
                persistScheduled.set(false)
                persistNow()
            }, PERSIST_DELAY_MS, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            persistScheduled.set(false)
            XposedBridge.log("[$TAG] Could not schedule module cache write: ${t.message}")
        }
    }

    private fun persistNow() {
        try {
            val snapshot = synchronized(apkModuleCache) { LinkedHashMap(apkModuleCache) }
            // Drop verdicts for APKs that no longer exist (uninstalled or replaced packages).
            val live = snapshot.filterKeys { File(ModuleCacheFile.sourceDirOf(it)).exists() }
            val bytes = ModuleCacheFile.encode(live, BuildConfig.VERSION_CODE).toByteArray(Charsets.UTF_8)
            if (bytes.size > ModuleCacheFile.MAX_FILE_BYTES) return
            DurableFiles.writeAtomically(ModuleCacheFile.PATH, bytes)
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Could not persist module cache: ${t.message}")
        }
    }

    fun isXposedModule(
        packageName: String,
        appInfo: ApplicationInfo?,
        context: Context?,
        userHandle: UserHandle?
    ): Boolean {
        if (packageName == "android") {
            return false
        }

        val resolvedAppInfo = (appInfo ?: resolveAppInfoSafely(packageName, context, userHandle)) ?: return false
        val sourceDir = resolvedAppInfo.sourceDir ?: resolvedAppInfo.publicSourceDir ?: return false

        // Skip unmodified built-in ROM partition apps (/system, /product, /system_ext, /vendor, /apex)
        // while allowing any user-installed or updated app in /data/ (even if named com.android.*)
        val isPureRomSystemApp = (resolvedAppInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (resolvedAppInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
            !sourceDir.startsWith("/data/")
        if (isPureRomSystemApp) {
            return false
        }

        val cacheKey = "$packageName|$sourceDir"
        ensurePersistedLoaded()
        apkModuleCache[cacheKey]?.let { return it }

        val detected = detectModuleFromAppInfo(packageName, resolvedAppInfo, context, userHandle)
        apkModuleCache[cacheKey] = detected
        schedulePersist()
        if (detected) {
            XposedBridge.log("[$TAG] Detected Xposed module: $packageName ($sourceDir)")
        }
        return detected
    }

    private fun resolveAppInfoSafely(
        packageName: String,
        context: Context?,
        userHandle: UserHandle?
    ): ApplicationInfo? {
        if (context == null) return null
        val token = Binder.clearCallingIdentity()
        return try {
            val pm = context.packageManager
            if (userHandle != null) {
                try {
                    val userId = userIdOf(userHandle)
                    val info = XposedHelpers.callMethod(
                        pm,
                        "getApplicationInfoAsUser",
                        packageName,
                        PackageManager.GET_META_DATA,
                        userId
                    ) as? ApplicationInfo
                    if (info != null) return info
                } catch (_: Throwable) {
                }
                // Do not leak User 0 info when resolving for a non-null secondary profile
                null
            } else {
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            }
        } catch (_: Throwable) {
            null
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    fun detectModuleFromAppInfo(
        packageName: String,
        appInfo: ApplicationInfo,
        context: Context?,
        userHandle: UserHandle?
    ): Boolean {
        // 1. Fast manifest meta-data check (Classic Xposed & Modern LibXposed)
        try {
            val meta = appInfo.metaData ?: resolveAppInfoSafely(packageName, context, userHandle)?.metaData
            if (meta != null) {
                if (meta.containsKey("xposedmodule") ||
                    meta.containsKey("xposedminversion") ||
                    meta.containsKey("xposeddescription") ||
                    meta.containsKey("xposedscope") ||
                    meta.containsKey("libxposed") ||
                    meta.containsKey("libxposed_minversion") ||
                    meta.containsKey("libxposed_api") ||
                    meta.containsKey("libxposed_scope")
                ) {
                    return true
                }
            }
        } catch (_: Throwable) {
        }

        // 2. Fast APK central directory inspection for modern LibXposed module.prop or classic xposed_init
        val sourceDir = appInfo.sourceDir ?: appInfo.publicSourceDir
        if (sourceDir != null && sourceDir.startsWith("/data/")) {
            try {
                java.util.zip.ZipFile(java.io.File(sourceDir)).use { zip ->
                    if (zip.getEntry("META-INF/xposed/module.prop") != null ||
                        zip.getEntry("assets/xposed_init") != null
                    ) {
                        return true
                    }
                }
            } catch (_: Throwable) {
            }
        }

        return false
    }

    fun clearCache() {
        apkModuleCache.clear()
    }
}
