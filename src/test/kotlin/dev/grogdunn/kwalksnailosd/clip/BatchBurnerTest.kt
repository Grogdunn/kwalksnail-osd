package dev.grogdunn.kwalksnailosd.clip

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.batch_job_no_osd
import dev.grogdunn.kwalksnailosd.osd.FcFirmware
import dev.grogdunn.kwalksnailosd.osd.Osd
import dev.grogdunn.kwalksnailosd.uiText
import dev.grogdunn.kwalksnailosd.video.BurnProgress
import dev.grogdunn.kwalksnailosd.video.BurnResult
import dev.grogdunn.kwalksnailosd.video.FfmpegLocator
import dev.grogdunn.kwalksnailosd.video.VideoCodec
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CancellationException
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BatchBurnerTest {
    @Test
    fun failureOfOneJobDoesNotStopTheOthers() {
        val called = mutableListOf<String>()
        val burner = BatchBurner { job, _, _, _, _, _, _ ->
            called += job.id
            if (job.id == "2") throw IllegalStateException("encoder died")
            doneResult(job.output)
        }

        val summary = burner.run(listOf(job("1"), job("2"), job("3")), magentaFont(), softwareSettings())

        assertEquals(listOf("1", "2", "3"), called)
        assertTrue(summary.states.getValue("1") is BatchJobState.Done)
        assertEquals("encoder died", (summary.states.getValue("2") as BatchJobState.Failed).message)
        assertTrue(summary.states.getValue("3") is BatchJobState.Done)
        assertEquals(2, summary.done)
        assertEquals(1, summary.failed)
        assertEquals(0, summary.cancelled)
    }

    @Test
    fun nullOsdFailsWithoutCallingBurner() {
        var called = false
        val burner = BatchBurner { _, _, _, _, _, _, _ ->
            called = true
            error("should not burn")
        }

        val summary = burner.run(listOf(job("1", osd = null)), magentaFont(), softwareSettings())

        assertFalse(called)
        assertEquals(uiText(Res.string.batch_job_no_osd), (summary.states.getValue("1") as BatchJobState.Failed).message)
        assertEquals(0, summary.done)
        assertEquals(1, summary.failed)
    }

    @Test
    fun cancellationStopsTheCurrentJobAndTheOnesNotStarted() {
        var cancelled = false
        val called = mutableListOf<String>()
        val burner = BatchBurner { job, _, _, _, _, _, _ ->
            called += job.id
            if (job.id == "2") {
                cancelled = true
                throw CancellationException("stop")
            }
            doneResult(job.output)
        }

        val summary = burner.run(
            jobs = listOf(job("1"), job("2"), job("3")),
            font = magentaFont(),
            settings = softwareSettings(),
            isCancelled = { cancelled },
        )

        assertEquals(listOf("1", "2"), called)
        assertTrue(summary.states.getValue("1") is BatchJobState.Done)
        assertTrue(summary.states.getValue("2") is BatchJobState.Cancelled)
        assertTrue(summary.states.getValue("3") is BatchJobState.Cancelled)
        assertEquals(1, summary.done)
        assertEquals(0, summary.failed)
        assertEquals(2, summary.cancelled)
    }

    @Test
    fun statesAreEmittedPendingThenRunningThenDonePerJob() {
        val events = mutableListOf<Pair<String, BatchJobState>>()
        val burner = BatchBurner { job, _, _, _, onProgress, _, _ ->
            onProgress(BurnProgress(0.4f, "burning ${job.id}"))
            onProgress(BurnProgress(1f, "done ${job.id}"))
            doneResult(job.output)
        }

        burner.run(
            jobs = listOf(job("1"), job("2")),
            font = magentaFont(),
            settings = softwareSettings(),
            onJobState = { id, state -> events += id to state },
        )

        assertEquals(listOf("1" to BatchJobState.Pending, "2" to BatchJobState.Pending), events.take(2))
        for (id in listOf("1", "2")) {
            val states = events.filter { it.first == id }.map { it.second }
            assertTrue(states.first() is BatchJobState.Pending)
            val running = states.filterIsInstance<BatchJobState.Running>()
            assertEquals(0f, running.first().fraction)
            assertEquals(0.4f, running[1].fraction)
            assertEquals("burning $id", running[1].message)
            assertEquals(1f, running[2].fraction)
            assertEquals("done $id", running[2].message)
            assertTrue(states.last() is BatchJobState.Done)
            assertEquals(
                listOf("Pending", "Running", "Running", "Running", "Done"),
                states.map { it::class.simpleName },
            )
        }
        val afterPending = events.drop(2).map { it.first }
        assertEquals(listOf("1", "1", "1", "1", "2", "2", "2", "2"), afterPending)
    }

    @Test
    fun outputPathsAvoidDiskAndBatchCollisions() {
        val outputDir = createTempDirectory("batch-names")
        try {
            outputDir.resolve("clip_with_osd.mp4").writeText("taken")
            outputDir.resolve("other_with_osd_2.mp4").writeText("taken")
            val videos = listOf(
                Path.of("/a/clip.mp4"),
                Path.of("/b/clip.mp4"),
                Path.of("/c/other.mp4"),
                Path.of("/d/fresh.mp4"),
            )

            val paths = batchOutputPaths(videos, outputDir)

            assertEquals(
                listOf(
                    outputDir.resolve("clip_with_osd_2.mp4"),
                    outputDir.resolve("clip_with_osd_3.mp4"),
                    outputDir.resolve("other_with_osd.mp4"),
                    outputDir.resolve("fresh_with_osd.mp4"),
                ),
                paths,
            )
            assertEquals(paths.toSet().size, paths.size)
        } finally {
            outputDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun twoClipsBurnToSeparateOutputsAndLeaveInputsUnchanged() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val directory = createTempDirectory("batch-burn")
        val first = preparedClip(ffmpeg, directory, "one", withAudio = true, gridX = 1)
        val second = preparedClip(ffmpeg, directory, "two", withAudio = false, gridX = 3)
        val firstBytes = Files.readAllBytes(first.video)
        val secondBytes = Files.readAllBytes(second.video)
        val outputDir = directory.resolve("out").also { Files.createDirectories(it) }
        val outputs = batchOutputPaths(listOf(first.video, second.video), outputDir)
        val jobs = listOf(first, second).zip(outputs).mapIndexed { index, (clip, output) ->
            BatchJob(
                id = index.toString(),
                video = clip.video,
                output = output,
                videoInfo = clip.videoInfo,
                osd = clip.osd,
                srt = clip.srt,
            )
        }

        val summary = BatchBurner.forFfmpeg(ffmpeg).run(jobs, magentaFont(), softwareSettings())

        assertEquals(2, summary.done)
        assertEquals(0, summary.failed)
        assertEquals(0, summary.cancelled)
        assertNotEquals(outputs[0], outputs[1])
        assertTrue(outputs[0].exists())
        assertTrue(outputs[1].exists())
        assertTrue(Files.size(outputs[0]) > 0)
        assertTrue(Files.size(outputs[1]) > 0)
        assertTrue(firstBytes.contentEquals(Files.readAllBytes(first.video)))
        assertTrue(secondBytes.contentEquals(Files.readAllBytes(second.video)))
    }

    @Test
    fun cancelAfterAJobFinishedKeepsThatJobDone() {
        var cancelled = false
        val called = mutableListOf<String>()
        val burner = BatchBurner { job, _, _, _, _, _, _ ->
            called += job.id
            cancelled = true
            doneResult(job.output)
        }

        val summary = burner.run(
            jobs = listOf(job("1"), job("2")),
            font = magentaFont(),
            settings = softwareSettings(),
            isCancelled = { cancelled },
        )

        assertEquals(listOf("1"), called)
        assertTrue(summary.states.getValue("1") is BatchJobState.Done)
        assertTrue(summary.states.getValue("2") is BatchJobState.Cancelled)
    }

    private fun job(
        id: String,
        osd: Osd? = Osd(FcFirmware.BETAFLIGHT, emptyList(), Duration.ZERO),
        videoInfo: VideoInfo? = VideoInfo(
            height = 1080,
            width = 1920,
            codec = "h264",
            duration = Duration.ofSeconds(1),
            frameRate = 30.0,
            frameNumber = 30,
            hasAudio = false,
        ),
    ) = BatchJob(
        id = id,
        video = Path.of("$id.mp4"),
        output = Path.of("$id-out.mp4"),
        videoInfo = videoInfo,
        osd = osd,
        srt = null,
    )

    private fun doneResult(output: Path) = BurnResult(
        output = output,
        encoderName = "fake",
        hardware = false,
        framesWritten = 3,
        usedSoftwareFallback = false,
    )
}
