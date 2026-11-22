package dev.grogdunn.kwalksnailosd.clip

import dev.grogdunn.kwalksnailosd.osd.FcFirmware
import dev.grogdunn.kwalksnailosd.osd.Frame
import dev.grogdunn.kwalksnailosd.osd.Osd
import dev.grogdunn.kwalksnailosd.video.OsdOffset
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipListTest {
    @Test
    fun movesSwapNeighboursAndNoOpAtEndsOrUnknownId() {
        val alpha = entry("Alpha")
        val beta = entry("Beta")
        val gamma = entry("Gamma")
        val list = clipList(alpha, beta, gamma)

        assertEquals(listOf(alpha.id, beta.id, gamma.id), list.movedUp(alpha.id).entries.map { it.id })
        assertEquals(listOf(beta.id, alpha.id, gamma.id), list.movedUp(beta.id).entries.map { it.id })
        assertEquals(listOf(alpha.id, gamma.id, beta.id), list.movedDown(beta.id).entries.map { it.id })
        assertEquals(listOf(alpha.id, beta.id, gamma.id), list.movedDown(gamma.id).entries.map { it.id })
        assertEquals(list, list.movedUp("missing"))
        assertEquals(list, list.movedDown("missing"))
    }

    @Test
    fun restoreAzReordersAndKeepsCurrentExcludedFlags() {
        val alpha = entry("Alpha")
        val beta = entry("Beta")
        val gamma = entry("Gamma")
        val natural = listOf(alpha.id, beta.id, gamma.id)
        val shuffled = clipList(gamma, alpha, beta, natural = natural)
            .withExcluded(beta.id, true)
            .movedDown(gamma.id)

        val restored = shuffled.restoredAz()

        assertEquals(listOf("Alpha", "Gamma", "Beta"), shuffled.entries.map { it.info.files.baseName })
        assertEquals(listOf("Alpha", "Beta", "Gamma"), restored.entries.map { it.info.files.baseName })
        assertEquals(natural, restored.naturalOrderIds)
        assertFalse(restored.entries[0].excluded)
        assertTrue(restored.entries[1].excluded)
        assertFalse(restored.entries[2].excluded)
        assertEquals(beta.info, restored.entries[1].info)
    }

    @Test
    fun totalDurationSumsOnlyIncludedProbedClips() {
        val included = entry("Alpha", videoInfo = info(duration = Duration.ofSeconds(10)))
        val alsoIncluded = entry("Beta", videoInfo = info(duration = Duration.ofSeconds(5)))
        val excluded = entry("Gamma", videoInfo = info(duration = Duration.ofSeconds(100)), excluded = true)
        val failed = entry("Delta", videoInfo = null, probeError = "unreadable")
        val list = clipList(included, alsoIncluded, excluded, failed)

        assertEquals(Duration.ofSeconds(15), list.totalDuration())
        assertEquals(Duration.ZERO, list.withExcluded(included.id, true).withExcluded(alsoIncluded.id, true).totalDuration())
    }

    @Test
    fun sameResolutionAndFpsAreNotAMismatch() {
        val alpha = entry("Alpha", videoInfo = info(width = 1920, height = 1080, frameRate = 30.0))
        val beta = entry("Beta", videoInfo = info(width = 1920, height = 1080, frameRate = 30.04))
        val list = clipList(alpha, beta)

        assertEquals(ClipMismatch(resolution = false, fps = false), list.mismatch())
        assertFalse(list.highlighted(alpha.id))
        assertFalse(list.highlighted(beta.id))
        assertFalse(list.highlighted("missing"))
    }

    @Test
    fun differentResolutionIsHighlightedOnTheDisagreeingClip() {
        val alpha = entry("Alpha", videoInfo = info(width = 1920, height = 1080, frameRate = 30.0))
        val beta = entry("Beta", videoInfo = info(width = 1280, height = 720, frameRate = 30.0))
        val list = clipList(alpha, beta)

        assertEquals(ClipMismatch(resolution = true, fps = false), list.mismatch())
        assertFalse(list.highlighted(alpha.id))
        assertTrue(list.highlighted(beta.id))
        assertFalse(list.canBurn())
    }

    @Test
    fun differentFpsIsHighlightedOnTheDisagreeingClip() {
        val alpha = entry("Alpha", videoInfo = info(frameRate = 30.0))
        val beta = entry("Beta", videoInfo = info(frameRate = 30.06))
        val list = clipList(alpha, beta)

        assertEquals(ClipMismatch(resolution = false, fps = true), list.mismatch())
        assertFalse(list.highlighted(alpha.id))
        assertTrue(list.highlighted(beta.id))
        assertFalse(list.canBurn())
    }

    @Test
    fun excludedClipIsNotCountedInMismatch() {
        val alpha = entry("Alpha", videoInfo = info(width = 1920, height = 1080, frameRate = 30.0))
        val excluded = entry(
            "Beta",
            videoInfo = info(width = 640, height = 480, frameRate = 60.0),
            excluded = true,
        )
        val gamma = entry("Gamma", videoInfo = info(width = 1920, height = 1080, frameRate = 30.0))
        val list = clipList(alpha, excluded, gamma)

        assertEquals(ClipMismatch(resolution = false, fps = false), list.mismatch())
        assertFalse(list.highlighted(excluded.id))
        assertTrue(list.canBurn())
    }

    @Test
    fun zeroOrOneIncludedProbedClipHasNoMismatch() {
        val only = entry("Alpha")
        assertEquals(ClipMismatch(resolution = false, fps = false), clipList(only).mismatch())
        assertEquals(ClipMismatch(resolution = false, fps = false), clipList(only).withExcluded(only.id, true).mismatch())
    }

    @Test
    fun canBurnRequiresIncludedProbedClipsWithOsdAndNoMismatch() {
        val alpha = entry("Alpha", srt = false)
        val beta = entry("Beta", srt = true)
        assertTrue(clipList(alpha, beta).canBurn())

        assertFalse(clipList(alpha, beta).withExcluded(alpha.id, true).withExcluded(beta.id, true).canBurn())
        assertFalse(clipList(entry("Alpha", osd = false), beta).canBurn())
        assertFalse(clipList(entry("Alpha", videoInfo = null, probeError = "bad"), beta).canBurn())
        assertFalse(clipList().canBurn())
        val unparsed = entry("Beta").let { clip -> clip.copy(info = clip.info.copy(osd = null, osdError = "bad")) }
        assertFalse(clipList(alpha, unparsed).canBurn())
    }

    private fun parsedOsd(): Osd = Osd(FcFirmware.BETAFLIGHT, listOf(Frame(0, emptyList())), Duration.ofMillis(1))

    @Test
    fun offsetCanBeSetClearedAndSurvivesReorderExcludeAndRestore() {
        val alpha = entry("Alpha")
        val beta = entry("Beta")
        val list = clipList(alpha, beta)
        val shifted = OsdOffset(5, 7)
        val global = OsdOffset(1, 2)

        val withOffset = list.withOffset(beta.id, shifted)
        assertEquals(shifted, withOffset.entries[1].offset)
        assertNull(withOffset.entries[0].offset)
        assertEquals(shifted, withOffset.effectiveOffset(beta.id, global))
        assertEquals(global, withOffset.effectiveOffset(alpha.id, global))
        assertEquals(global, list.effectiveOffset("missing", global))
        assertEquals(list, list.withOffset("missing", shifted))

        assertNull(withOffset.withOffset(beta.id, null).entries[1].offset)
        val kept = withOffset.movedUp(beta.id).movedDown(beta.id).withExcluded(beta.id, true).restoredAz()
        assertEquals(shifted, kept.entries.first { it.id == beta.id }.offset)
        assertTrue(kept.entries.first { it.id == beta.id }.excluded)
    }

    @Test
    fun outputStemUsesFirstIncludedBaseNameAfterReorder() {
        val alpha = entry("Alpha")
        val beta = entry("Beta")
        val gamma = entry("Gamma")
        val list = clipList(alpha, beta, gamma)

        assertEquals("Alpha_joined", list.outputStem())
        val gammaFirst = list.movedUp(gamma.id).movedUp(gamma.id).withExcluded(alpha.id, true)
        assertEquals(listOf("Gamma", "Alpha", "Beta"), gammaFirst.entries.map { it.info.files.baseName })
        assertEquals("Gamma_joined", gammaFirst.outputStem())
        assertNull(list.withExcluded(alpha.id, true).withExcluded(beta.id, true).withExcluded(gamma.id, true).outputStem())
    }

    private fun clipList(vararg entries: ClipEntry, natural: List<String>? = null): ClipList {
        return ClipList(
            folder = Path.of("folder"),
            entries = entries.toList(),
            naturalOrderIds = natural ?: entries.map { it.id },
        )
    }

    private fun entry(
        baseName: String,
        videoInfo: VideoInfo? = info(),
        osd: Boolean = true,
        srt: Boolean = false,
        probeError: String? = null,
        excluded: Boolean = false,
    ): ClipEntry {
        val video = Path.of("$baseName.mp4")
        return ClipEntry(
            id = video.toString(),
            info = ClipInfo(
                files = ClipFiles(
                    baseName = baseName,
                    video = video,
                    osd = if (osd) Path.of("$baseName.osd") else null,
                    srt = if (srt) Path.of("$baseName.srt") else null,
                ),
                videoInfo = videoInfo,
                probeError = probeError,
                osd = if (osd) parsedOsd() else null,
            ),
            excluded = excluded,
        )
    }

    private fun info(
        width: Int = 1920,
        height: Int = 1080,
        frameRate: Double = 30.0,
        duration: Duration = Duration.ofSeconds(1),
    ): VideoInfo = VideoInfo(
        height = height,
        width = width,
        codec = "h264",
        duration = duration,
        frameRate = frameRate,
        frameNumber = 30,
        hasAudio = false,
    )
}
