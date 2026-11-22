package dev.grogdunn.kwalksnailosd.clip

import dev.grogdunn.kwalksnailosd.video.VideoInfo
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipFolderScannerTest {
    private val scanner = ClipFolderScanner()

    @Test
    fun groupsByBaseNameWithCaseInsensitiveExtensionsAndLexOrder() {
        val directory = createTempDirectory("clips-group")
        try {
            val upper = directory.resolve("B.mp4").also { it.writeText("x") }
            val lower = directory.resolve("a.MP4").also { it.writeText("x") }
            val lowerOsd = directory.resolve("a.Osd").also { it.writeText("x") }
            val lowerSrt = directory.resolve("a.SRT").also { it.writeText("x") }
            val srtOnly = directory.resolve("m.mp4").also { it.writeText("x") }
            val srt = directory.resolve("m.srt").also { it.writeText("x") }
            directory.resolve("a.txt").writeText("x")
            directory.resolve("notes.md").writeText("x")
            directory.resolve("other.osd").writeText("x")
            directory.resolve("solo.mov").writeText("x")

            val infos = mapOf(
                upper.name to info(duration = Duration.ofSeconds(2)),
                lower.name to info(duration = Duration.ofSeconds(3)),
                srtOnly.name to info(duration = Duration.ofSeconds(4)),
            )
            val probed = mutableListOf<String>()
            val list = scanner.scan(directory) { video ->
                probed += video.name
                infos.getValue(video.name)
            }

            assertEquals(listOf("B", "a", "m"), list.entries.map { it.info.files.baseName })
            assertEquals(listOf(upper, lower, srtOnly), list.entries.map { it.info.files.video })
            assertEquals(list.entries.map { it.id }, list.naturalOrderIds)
            assertEquals(upper.toString(), list.entries[0].id)
            assertNull(list.entries[0].info.files.osd)
            assertNull(list.entries[0].info.files.srt)
            assertEquals(lowerOsd, list.entries[1].info.files.osd)
            assertEquals(lowerSrt, list.entries[1].info.files.srt)
            assertNull(list.entries[2].info.files.osd)
            assertEquals(srt, list.entries[2].info.files.srt)
            assertEquals(listOf("B.mp4", "a.MP4", "m.mp4"), probed)
            assertEquals(directory, list.folder)
            assertNull(list.entries[0].info.probeError)
            assertEquals(Duration.ofSeconds(2), list.entries[0].info.videoInfo?.duration)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun missingOrUnreadableFolderReturnsEmptyList() {
        val directory = createTempDirectory("clips-missing")
        try {
            val missing = directory.resolve("gone")
            val list = scanner.scan(missing) { error("probe should not run") }
            assertTrue(list.entries.isEmpty())
            assertTrue(list.naturalOrderIds.isEmpty())
            assertEquals(missing, list.folder)

            val file = directory.resolve("not-a-folder").also { it.writeText("x") }
            val fromFile = scanner.scan(file) { error("probe should not run") }
            assertTrue(fromFile.entries.isEmpty())
            assertEquals(file, fromFile.folder)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun probeExceptionSetsErrorAndScanContinues() {
        val directory = createTempDirectory("clips-probe")
        try {
            directory.resolve("Alpha.mp4").writeText("x")
            directory.resolve("Beta.mp4").writeText("x")
            val infos = mapOf("Beta.mp4" to info(frameRate = 59.94))

            val list = scanner.scan(directory) { video ->
                if (video.name.startsWith("Alpha")) throw IllegalStateException("cannot read")
                infos.getValue(video.name)
            }

            assertEquals(listOf("Alpha", "Beta"), list.entries.map { it.info.files.baseName })
            val alpha = list.entries[0]
            val beta = list.entries[1]
            assertNull(alpha.info.videoInfo)
            assertEquals("cannot read", alpha.info.probeError)
            assertEquals(59.94, beta.info.videoInfo?.frameRate)
            assertNull(beta.info.probeError)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun nullProbeMessageBecomesUnreadable() {
        val directory = createTempDirectory("clips-unreadable")
        try {
            directory.resolve("Alpha.mp4").writeText("x")
            val list = scanner.scan(directory) { throw IllegalStateException() }
            val entry = list.entries.single()
            assertNull(entry.info.videoInfo)
            assertEquals("unreadable", entry.info.probeError)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun scanFilesPicksCompanionsIgnoresNonMp4SortsAndHandlesEmpty() {
        val directory = createTempDirectory("clips-files")
        val other = createTempDirectory("clips-files-other")
        try {
            val upper = directory.resolve("B.mp4").also { it.writeText("x") }
            val lower = directory.resolve("a.MP4").also { it.writeText("x") }
            val lowerOsd = directory.resolve("a.Osd").also { it.writeText("x") }
            val lowerSrt = directory.resolve("a.SRT").also { it.writeText("x") }
            val srtOnly = directory.resolve("m.mp4").also { it.writeText("x") }
            val srt = directory.resolve("m.srt").also { it.writeText("x") }
            val sameName = other.resolve("m.mp4").also { it.writeText("x") }
            val notes = directory.resolve("notes.md").also { it.writeText("x") }
            val movie = directory.resolve("solo.mov").also { it.writeText("x") }

            val list = scanner.scanFiles(
                listOf(sameName, upper, lower, srtOnly, upper, movie, notes),
            ) { video ->
                if (video == upper) throw IllegalStateException("cannot read")
                info()
            }

            val expected = listOf(upper, lower, srtOnly, sameName)
                .sortedWith(compareBy({ it.fileName.toString() }, { it.toString() }))
            assertEquals(expected, list.entries.map { it.info.files.video })
            assertEquals(list.entries.map { it.id }, list.naturalOrderIds)
            assertEquals(expected.first().parent, list.folder)
            val byVideo = list.entries.associateBy { it.info.files.video }
            assertEquals(lowerOsd, byVideo.getValue(lower).info.files.osd)
            assertEquals(lowerSrt, byVideo.getValue(lower).info.files.srt)
            assertNull(byVideo.getValue(upper).info.files.osd)
            assertEquals(srt, byVideo.getValue(srtOnly).info.files.srt)
            assertNull(byVideo.getValue(sameName).info.files.srt)
            assertNull(byVideo.getValue(upper).info.videoInfo)
            assertEquals("cannot read", byVideo.getValue(upper).info.probeError)
            assertEquals(info().frameRate, byVideo.getValue(lower).info.videoInfo?.frameRate)

            val empty = scanner.scanFiles(emptyList()) { error("probe should not run") }
            assertTrue(empty.entries.isEmpty())
            assertTrue(empty.naturalOrderIds.isEmpty())
            assertEquals(Path.of(""), empty.folder)
            val onlyOther = scanner.scanFiles(listOf(movie, notes)) { error("probe should not run") }
            assertTrue(onlyOther.entries.isEmpty())
            assertEquals(Path.of(""), onlyOther.folder)
        } finally {
            directory.toFile().deleteRecursively()
            other.toFile().deleteRecursively()
        }
    }

    private fun info(
        frameRate: Double = 30.0,
        duration: Duration = Duration.ofSeconds(1),
    ): VideoInfo = VideoInfo(
        height = 1080,
        width = 1920,
        codec = "h264",
        duration = duration,
        frameRate = frameRate,
        frameNumber = 30,
        hasAudio = false,
    )
}
