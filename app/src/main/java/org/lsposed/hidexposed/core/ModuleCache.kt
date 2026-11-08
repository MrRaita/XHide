package org.lsposed.hidexposed.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.UserHandle
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Universal O(1) Xposed Module Detector.
 *
 * Detects all 3 generations of Xposed modules:
 * 1. Manifest Meta-Data (`xposedmodule`, `xposedminversion`, `xposeddescription`, `xposedscope`)
 * 2. Classic Xposed API (`assets/xposed_init` inside APK)
 * 3. Modern LibXposed API 100 (`META-INF/xposed/module.prop` or `META-INF/xposed/java_init.list` inside APK)
 *
 * Keyed by `packageName|sourceDir` so package updates automatically invalidate stale entries
 * without requiring broadcast receivers or locking PackageManager.
 */
object ModuleCache {
    private const val TAG = "XHide"

    // Key: "packageName|sourceDir" -> Value: true if Xposed module, false otherwise
    private val apkModuleCache = ConcurrentHashMap<String, Boolean>()

    // Managers and core system packages that should never be treated as regular hidden modules by default
    val KNOWN_MANAGERS = setOf(
        "org.lsposed.manager",
        "io.github.lsposed.manager",
        "org.lsposed.vector",
        "io.github.vector",
        "de.robv.android.xposed.installer",
        "org.meowcat.edxposed.manager",
        "me.weishu.kernelsu",
        "com.rifsxd.ksunext"
    )

    fun isXposedModule(
        packageName: String,
        appInfo: ApplicationInfo?,
        context: Context?,
        userHandle: UserHandle?
    ): Boolean {
        if (packageName == "android" || packageName.startsWith("com.android.") || KNOWN_MANAGERS.contains(packageName)) {
            return false
        }

        val resolvedAppInfo = appInfo ?: resolveAppInfoSafely(packageName, context, userHandle)
        val sourceDir = resolvedAppInfo?.sourceDir ?: return false
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
                val userId = XposedHelpers.callMethod(userHandle, "getIdentifier") as Int
                XposedHelpers.callMethod(
                    pm,
                    "getApplicationInfoAsUser",
                    packageName,
                    PackageManager.GET_META_DATA,
                    userId
                ) as? ApplicationInfo
            } else {
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            }
        } catch (_: Throwable) {
            null
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun detectModuleFromAppInfo(
        packageName: String,
        appInfo: ApplicationInfo,
        context: Context?,
        userHandle: UserHandle?
    ): Boolean {
        // Tier 1: Check AndroidManifest meta-data
        try {
            val meta = appInfo.metaData ?: resolveAppInfoSafely(packageName, context, userHandle)?.metaData
            if (meta != null) {
                if (meta.containsKey("xposedmodule") ||
                    meta.containsKey("xposedminversion") ||
                    meta.containsKey("xposeddescription") ||
                    meta.containsKey("xposedscope")
                ) {
                    return true
                }
            }
        } catch (_: Throwable) {
        }

        // Tier 2 & 3: Inspect APK zip entries for classic (assets/xposed_init) and modern (META-INF/xposed/*)
        val sourcePath = appInfo.sourceDir
        if (!sourcePath.isNullOrEmpty()) {
            try {
                val apkFile = File(sourcePath)
                if (apkFile.exists() && apkFile.canRead()) {
                    ZipFile(apkFile).use { zip ->
                        if (zip.getEntry("assets/xposed_init") != null ||
                            zip.getEntry("META-INF/xposed/module.prop") != null ||
                            zip.getEntry("META-INF/xposed/java_init.list") != null ||
                            zip.getEntry("META-INF/xposed/scope.list") != null
                        ) {
                            return true
                        }
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
