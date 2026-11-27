package dev.hfw.app

import dev.hfw.state.Op
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManagerTest {
    @Test fun exportAndImportRoundTrip() {
        val srcDir = Files.createTempDirectory("hfw-src").toFile()
        val dstDir = Files.createTempDirectory("hfw-dst").toFile()
        try {
            val src = RuleStore(srcDir)
            val png = byteArrayOf(1, 2, 3, 4)
            assertTrue(src.apply(listOf(
                Op.Hide(10, "com.hidden", true),
                Op.Name("com.app", "Custom", user = 10),
                Op.Icon("com.app", 42L, user = 10),
            ), "10:com.app" to png))

            val zipOut = ByteArrayOutputStream()
            assertTrue(BackupManager.exportZip(src, zipOut))

            val dst = RuleStore(dstDir)
            assertTrue(BackupManager.importZip(dst, ByteArrayInputStream(zipOut.toByteArray())) { true })
            assertEquals(src.hash(), dst.hash())
            assertEquals("Custom", dst.state.labelOf("com.app", 10))
            assertArrayEquals(png, dst.iconBytes("com.app", 10)?.first)
        } finally {
            srcDir.deleteRecursively()
            dstDir.deleteRecursively()
        }
    }

    @Test fun rejectsZipSlipAndMissingRules() {
        val dir = Files.createTempDirectory("hfw-bad").toFile()
        try {
            val store = RuleStore(dir)
            val badZip = ByteArrayOutputStream()
            ZipOutputStream(badZip).use { zos ->
                zos.putNextEntry(ZipEntry("../evil.txt"))
                zos.write("nope".toByteArray())
                zos.closeEntry()
            }
            assertFalse(BackupManager.importZip(store, ByteArrayInputStream(badZip.toByteArray())) { true })

            val noRulesZip = ByteArrayOutputStream()
            ZipOutputStream(noRulesZip).use { zos ->
                zos.putNextEntry(ZipEntry("icons/0_com.app.png"))
                zos.write(byteArrayOf(1, 2))
                zos.closeEntry()
            }
            assertFalse(BackupManager.importZip(store, ByteArrayInputStream(noRulesZip.toByteArray())) { true })
        } finally {
            dir.deleteRecursively()
        }
    }
}
