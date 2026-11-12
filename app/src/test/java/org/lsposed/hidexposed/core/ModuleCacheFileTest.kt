package org.lsposed.hidexposed.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleCacheFileTest {

    private val entries = linkedMapOf(
        "com.module|/data/app/~~a/com.module-1/base.apk" to true,
        "com.regular|/data/app/~~b/com.regular-2/base.apk" to false
    )

    @Test
    fun roundTripPreservesVerdicts() {
        val text = ModuleCacheFile.encode(entries, version = 104)
        assertEquals(entries, ModuleCacheFile.decode(text, version = 104, maxEntries = 100))
    }

    @Test
    fun versionMismatchDiscardsEverything() {
        val text = ModuleCacheFile.encode(entries, version = 104)
        assertTrue(ModuleCacheFile.decode(text, version = 105, maxEntries = 100).isEmpty())
    }

    @Test
    fun malformedLinesAndKeysAreSkipped() {
        val text = "xhide-modcache-v1:1\n" +
            "1\tcom.ok|/data/app/ok/base.apk\n" +
            "x\tcom.badflag|/data/app/x/base.apk\n" +
            "1\tno-separator\n" +
            "1\tcom.relative|data/app/rel.apk\n" +
            "garbage\n"
        assertEquals(
            mapOf("com.ok|/data/app/ok/base.apk" to true),
            ModuleCacheFile.decode(text, version = 1, maxEntries = 100)
        )
    }

    @Test
    fun entryLimitIsEnforced() {
        val text = ModuleCacheFile.encode(entries, version = 1)
        assertEquals(1, ModuleCacheFile.decode(text, version = 1, maxEntries = 1).size)
    }

    @Test
    fun sourceDirIsExtractedFromKey() {
        assertEquals("/data/app/x/base.apk", ModuleCacheFile.sourceDirOf("com.x|/data/app/x/base.apk"))
    }
}
