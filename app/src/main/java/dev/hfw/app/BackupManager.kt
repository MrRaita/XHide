package dev.hfw.app

import dev.hfw.state.Limits
import dev.hfw.state.Pkg
import dev.hfw.state.StateCodec
import dev.hfw.state.StateData
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Exports and imports `rules.json` + active icon PNGs as a bounded, zip-slip-safe `.zip` archive. */
object BackupManager {
    private const val RULES_ENTRY = "rules.json"
    private const val ICONS_PREFIX = "icons/"
    private const val MAX_TOTAL_BYTES = 16 * 1024 * 1024
    private const val MAX_ENTRIES = Limits.MAX_PACKAGES + 16

    fun exportZip(store: RuleStore, out: OutputStream): Boolean = try {
        val (state, icons) = store.exportSnapshot()
        val rules = StateCodec.encodeText(state, includeRuntime = false).toByteArray(Charsets.UTF_8)
        // Never write an archive that importZip would refuse: apply the same limits up front.
        if (rules.size > Limits.MAX_STATE_FILE_BYTES || icons.size + 1 > MAX_ENTRIES) return false
        var total = rules.size
        for ((_, bytes) in icons) {
            if (bytes.isEmpty() || bytes.size > Limits.MAX_ICON_BYTES) return false
            total += bytes.size
            if (total > MAX_TOTAL_BYTES) return false
        }
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry(RULES_ENTRY))
            zos.write(rules)
            zos.closeEntry()

            for ((name, bytes) in icons) {
                zos.putNextEntry(ZipEntry("$ICONS_PREFIX$name"))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        true
    } catch (_: Throwable) {
        false
    }

    fun importZip(
        store: RuleStore,
        input: InputStream,
        validatePng: (ByteArray) -> Boolean = { IconCodec.decodePng(it) != null },
    ): Boolean = try {
        var rulesBytes: ByteArray? = null
        val icons = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0
        var entries = 0

        ZipInputStream(input).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (++entries > MAX_ENTRIES) return false
                if (entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val name = entry.name
                if (name.contains("..") || name.startsWith("/") || name.contains("\\")) return false

                when {
                    name == RULES_ENTRY -> {
                        val bytes = readBounded(zis, Limits.MAX_STATE_FILE_BYTES) ?: return false
                        totalBytes += bytes.size
                        if (totalBytes > MAX_TOTAL_BYTES) return false
                        rulesBytes = bytes
                    }
                    name.startsWith(ICONS_PREFIX) -> {
                        val fileName = name.removePrefix(ICONS_PREFIX)
                        if (!isValidIconFileName(fileName)) {
                            zis.closeEntry()
                            continue
                        }
                        val bytes = readBounded(zis, Limits.MAX_ICON_BYTES) ?: return false
                        totalBytes += bytes.size
                        if (totalBytes > MAX_TOTAL_BYTES) return false
                        if (bytes.isNotEmpty() && validatePng(bytes)) icons[fileName] = bytes
                    }
                }
                zis.closeEntry()
            }
        }

        val rawRules = rulesBytes ?: return false
        val state = StateCodec.decodeText(rawRules.toString(Charsets.UTF_8), strict = true) ?: return false
        store.restoreBackup(state, icons)
    } catch (_: Throwable) {
        false
    }

    private fun isValidIconFileName(fileName: String): Boolean {
        if (!fileName.endsWith(".png") || fileName.contains('/')) return false
        val base = fileName.removeSuffix(".png")
        val sep = base.indexOf('_')
        val pkg = if (sep > 0 && base.substring(0, sep).toIntOrNull() != null) base.substring(sep + 1) else base
        return Pkg.isValid(pkg)
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
