package org.lsposed.hidexposed.data

import java.io.File
import java.io.FileOutputStream

/**
 * Crash- and power-loss-safe file replacement for `system_server` state files.
 *
 * Writes to a temp file, fsyncs it, then renames it over the target. rename(2) is atomic only
 * once the temp file's data has reached disk; otherwise a power loss right after the rename can
 * leave an empty or truncated file. Files are restricted to owner read/write.
 */
internal object DurableFiles {
    fun writeAtomically(path: String, bytes: ByteArray) {
        val targetFile = File(path)
        val tmpFile = File("$path.tmp")
        writeDurably(tmpFile, bytes)
        restrictToOwner(tmpFile)
        if (!tmpFile.renameTo(targetFile)) {
            writeDurably(targetFile, bytes)
            restrictToOwner(targetFile)
            tmpFile.delete()
        }
    }

    private fun writeDurably(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
    }

    private fun restrictToOwner(file: File) {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
    }
}
