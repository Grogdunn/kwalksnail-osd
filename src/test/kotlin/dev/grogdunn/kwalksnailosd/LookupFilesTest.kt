package dev.grogdunn.kwalksnailosd

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LookupFilesTest {
    @Test
    fun findsCompanionsRegardlessOfExtensionCaseAndIgnoresOtherBaseNames() {
        val directory = createTempDirectory("lookup-case")
        try {
            val mp4 = directory.resolve("flight.MP4").also { it.writeText("video") }
            val osd = directory.resolve("flight.Osd").also { it.writeText("osd") }
            val srt = directory.resolve("flight.SRT").also { it.writeText("srt") }
            val other = directory.resolve("other.mp4").also { it.writeText("other") }
            directory.resolve("flight.txt").writeText("ignored")

            for (base in listOf(mp4, osd, srt)) {
                val result = lookupFiles(base)
                assertEquals(mp4, result.mp4Path, "mp4 from $base")
                assertEquals(osd, result.osdPath, "osd from $base")
                assertEquals(srt, result.srtPath, "srt from $base")
            }

            val unrelated = lookupFiles(other)
            assertEquals(other, unrelated.mp4Path)
            assertNull(unrelated.osdPath)
            assertNull(unrelated.srtPath)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun worksWhenOnlySomeCompanionsExist() {
        val directory = createTempDirectory("lookup-partial")
        try {
            val mp4 = directory.resolve("clip.mp4").also { it.writeText("video") }
            val srt = directory.resolve("clip.srt").also { it.writeText("srt") }

            val fromMp4 = lookupFiles(mp4)
            assertEquals(mp4, fromMp4.mp4Path)
            assertNull(fromMp4.osdPath)
            assertEquals(srt, fromMp4.srtPath)

            val fromSrt = lookupFiles(srt)
            assertEquals(mp4, fromSrt.mp4Path)
            assertNull(fromSrt.osdPath)
            assertEquals(srt, fromSrt.srtPath)

            val onlyOsd = directory.resolve("solo.OSD").also { it.writeText("osd") }
            val osdOnly = lookupFiles(onlyOsd)
            assertNull(osdOnly.mp4Path)
            assertEquals(onlyOsd, osdOnly.osdPath)
            assertNull(osdOnly.srtPath)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun missingFolderReturnsEmptyResult() {
        val directory = createTempDirectory("lookup-missing")
        try {
            val missing = directory.resolve("gone").resolve("flight.mp4")
            assertEquals(LookupFilesResult(null, null, null), lookupFiles(missing))
        } finally {
            directory.toFile().deleteRecursively()
        }

        val noParent = lookupFiles(Path.of("no-such-kwalksnail-${System.nanoTime()}.MP4"))
        assertNull(noParent.mp4Path)
        assertNull(noParent.osdPath)
        assertNull(noParent.srtPath)
    }
}
