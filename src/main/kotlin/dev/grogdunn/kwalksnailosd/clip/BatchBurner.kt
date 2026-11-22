package dev.grogdunn.kwalksnailosd.clip

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.batch_job_no_osd
import dev.grogdunn.kwalksnailosd.generated.resources.batch_job_no_video
import dev.grogdunn.kwalksnailosd.generated.resources.burn_failed
import dev.grogdunn.kwalksnailosd.generated.resources.burn_starting
import dev.grogdunn.kwalksnailosd.osd.Osd
import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.srt.SrtFile
import dev.grogdunn.kwalksnailosd.srt.SrtOptions
import dev.grogdunn.kwalksnailosd.uiText
import dev.grogdunn.kwalksnailosd.video.BurnProgress
import dev.grogdunn.kwalksnailosd.video.BurnResult
import dev.grogdunn.kwalksnailosd.video.BurnSettings
import dev.grogdunn.kwalksnailosd.video.VideoBurner
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import dev.grogdunn.kwalksnailosd.video.outputNameFor
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.nameWithoutExtension

fun batchOutputPaths(
    videos: List<Path>,
    outputDir: Path,
    exists: (Path) -> Boolean = Files::exists,
): List<Path> {
    val assigned = mutableSetOf<Path>()
    return videos.map { video ->
        val stem = video.nameWithoutExtension
        var suffix = 2
        var candidate = outputDir.resolve(outputNameFor(video))
        while (exists(candidate) || candidate in assigned) {
            candidate = outputDir.resolve("${stem}_with_osd_$suffix.mp4")
            suffix += 1
        }
        assigned.add(candidate)
        candidate
    }
}

data class BatchJob(
    val id: String,
    val video: Path,
    val output: Path,
    val videoInfo: VideoInfo?,
    val osd: Osd?,
    val srt: SrtFile?,
    val preflightError: String? = null,
)

sealed interface BatchJobState {
    data object Pending : BatchJobState
    data class Running(val fraction: Float, val message: String) : BatchJobState
    data class Done(val result: BurnResult) : BatchJobState
    data class Failed(val message: String) : BatchJobState
    data object Cancelled : BatchJobState
}

data class BatchSummary(val states: Map<String, BatchJobState>) {
    val done: Int get() = states.values.count { it is BatchJobState.Done }
    val failed: Int get() = states.values.count { it is BatchJobState.Failed }
    val cancelled: Int get() = states.values.count { it is BatchJobState.Cancelled }
}

class BatchBurner(
    private val burnOne: (
        job: BatchJob,
        font: Font,
        settings: BurnSettings,
        srtOptions: SrtOptions,
        onProgress: (BurnProgress) -> Unit,
        onPreviewFrame: (BufferedImage) -> Unit,
        isCancelled: () -> Boolean,
    ) -> BurnResult,
) {
    fun run(
        jobs: List<BatchJob>,
        font: Font,
        settings: BurnSettings,
        srtOptions: SrtOptions = SrtOptions(),
        onJobState: (id: String, state: BatchJobState) -> Unit = { _, _ -> },
        onPreviewFrame: (BufferedImage) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): BatchSummary {
        val states = linkedMapOf<String, BatchJobState>()
        fun emit(id: String, state: BatchJobState) {
            states[id] = state
            onJobState(id, state)
        }
        jobs.forEach { job -> emit(job.id, BatchJobState.Pending) }
        for (index in jobs.indices) {
            if (isCancelled()) return cancelFrom(jobs, index, states, ::emit)
            val job = jobs[index]
            val blocked = preflightMessage(job)
            if (blocked != null) {
                emit(job.id, BatchJobState.Failed(blocked))
                continue
            }
            emit(job.id, BatchJobState.Running(0f, uiText(Res.string.burn_starting)))
            try {
                val result = burnOne(
                    job,
                    font,
                    settings,
                    srtOptions,
                    { progress -> emit(job.id, BatchJobState.Running(progress.fraction, progress.message)) },
                    onPreviewFrame,
                    isCancelled,
                )
                emit(job.id, BatchJobState.Done(result))
                if (isCancelled()) return cancelFrom(jobs, index + 1, states, ::emit)
            } catch (_: CancellationException) {
                return cancelFrom(jobs, index, states, ::emit)
            } catch (exception: Exception) {
                emit(job.id, BatchJobState.Failed(exception.message ?: uiText(Res.string.burn_failed)))
            }
        }
        return BatchSummary(states)
    }

    private fun preflightMessage(job: BatchJob): String? {
        job.preflightError?.let { return it }
        if (job.videoInfo == null) return uiText(Res.string.batch_job_no_video)
        if (job.osd == null) return uiText(Res.string.batch_job_no_osd)
        return null
    }

    companion object {
        fun forFfmpeg(ffmpeg: Path): BatchBurner = BatchBurner { job, font, settings, srtOptions, onProgress, onPreviewFrame, isCancelled ->
            VideoBurner(ffmpeg).burn(
                input = job.video,
                output = job.output,
                videoInfo = job.videoInfo!!,
                osd = job.osd!!,
                font = font,
                settings = settings,
                onProgress = onProgress,
                isCancelled = isCancelled,
                srt = job.srt,
                srtOptions = srtOptions,
                onPreviewFrame = onPreviewFrame,
            )
        }
    }
}

private fun cancelFrom(
    jobs: List<BatchJob>,
    fromIndex: Int,
    states: MutableMap<String, BatchJobState>,
    emit: (String, BatchJobState) -> Unit,
): BatchSummary {
    for (index in fromIndex until jobs.size) {
        emit(jobs[index].id, BatchJobState.Cancelled)
    }
    return BatchSummary(states)
}
