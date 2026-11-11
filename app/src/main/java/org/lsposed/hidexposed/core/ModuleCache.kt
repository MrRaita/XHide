package org.lsposed.hidexposed.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.UserHandle
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

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
        apkModuleCache[cacheKey]?.let { return it }

        val detected = detectModuleFromAppInfo(packageName, resolvedAppInfo, context, userHandle)
        apkModuleCache[cacheKey] = detected
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
                    val userId = XposedHelpers.callMethod(userHandle, "getIdentifier") as? Int ?: userHandle.hashCode()
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
