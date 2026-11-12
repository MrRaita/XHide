package org.lsposed.hidexposed.core

/**
 * On-disk format for persisted [ModuleCache] verdicts (`/data/system/hidexposed_modcache.txt`).
 *
 * Header line `xhide-modcache-v1:<versionCode>`, then one entry per line:
 * `1<TAB>packageName|sourceDir` (module) or `0<TAB>packageName|sourceDir` (not a module).
 *
 * Keys embed the APK path, which changes on every install or update, so a stale verdict can never
 * match a newer APK. The XHide version code in the header discards all verdicts whenever XHide
 * itself is updated, in case detection rules changed. Free of Xposed/Android dependencies so it
 * is unit-testable.
 */
internal object ModuleCacheFile {
    const val PATH = "/data/system/hidexposed_modcache.txt"
    const val MAX_FILE_BYTES = 512 * 1024
    private const val HEADER_PREFIX = "xhide-modcache-v1:"

    fun encode(entries: Map<String, Boolean>, version: Int): String = buildString {
        append(HEADER_PREFIX).append(version).append('\n')
        for ((key, isModule) in entries) {
            if (!isValidKey(key)) continue
            append(if (isModule) '1' else '0').append('\t').append(key).append('\n')
        }
    }

    fun decode(text: String, version: Int, maxEntries: Int): Map<String, Boolean> {
        val lines = text.split('\n')
        if (lines.firstOrNull() != "$HEADER_PREFIX$version") return emptyMap()
        val out = LinkedHashMap<String, Boolean>()
        for (i in 1 until lines.size) {
            if (out.size >= maxEntries) break
            val line = lines[i]
            if (line.length < 3) continue
            if (line[1] != '\t') continue
            val isModule = when (line[0]) {
                '1' -> true
                '0' -> false
                else -> continue
            }
            val key = line.substring(2)
            if (isValidKey(key)) out[key] = isModule
        }
        return out
    }

    /** Extracts the APK path from a `packageName|sourceDir` key. */
    fun sourceDirOf(key: String): String = key.substringAfter('|')

    private fun isValidKey(key: String): Boolean {
        if (key.indexOfAny(charArrayOf('\n', '\r', '\t')) >= 0) return false
        val sep = key.indexOf('|')
        if (sep <= 0) return false
        if (sep + 1 >= key.length) return false
        return key[sep + 1] == '/'
    }
}
