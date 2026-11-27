package dev.fadalias.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleCacheFileTest {
    @Test fun roundTripAndSourceDir() {
        val entries = linkedMapOf(
            "com.mod.one|/data/app/one/base.apk" to true,
            "com.plain.two|/data/app/two/base.apk" to false,
        )
        val encoded = ModuleCacheFile.encode(entries, ModuleCacheFile.DETECTION_RULES_VERSION)
        val decoded = ModuleCacheFile.decode(encoded, ModuleCacheFile.DETECTION_RULES_VERSION, 100)
        assertEquals(entries, decoded)
        assertEquals("/data/app/one/base.apk", ModuleCacheFile.sourceDirOf("com.mod.one|/data/app/one/base.apk"))
    }

    @Test fun discardsOlderVersionAndInvalidLines() {
        val encoded = ModuleCacheFile.encode(mapOf("com.mod.one|/data/app/one/base.apk" to true), 1)
        assertTrue(ModuleCacheFile.decode(encoded, 2, 100).isEmpty())

        val bad = "hfw-modcache-v1:1\n1\tinvalid_no_sep\n1\tno_slash|relative.apk\n2\tbad_flag|/data/a.apk\n1\tok.pkg|/data/ok.apk\n"
        val decoded = ModuleCacheFile.decode(bad, 1, 100)
        assertEquals(mapOf("ok.pkg|/data/ok.apk" to true), decoded)
    }

    @Test fun respectsMaxEntries() {
        val entries = (1..10).associate { "pkg.$it|/data/app/$it/base.apk" to (it % 2 == 0) }
        val encoded = ModuleCacheFile.encode(entries, 1)
        val decoded = ModuleCacheFile.decode(encoded, 1, 4)
        assertEquals(4, decoded.size)
    }
}
