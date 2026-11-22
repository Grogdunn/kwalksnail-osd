package dev.grogdunn.kwalksnailosd.clip

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.burn_encoder_stopped
import dev.grogdunn.kwalksnailosd.generated.resources.burn_ffmpeg_start
import dev.grogdunn.kwalksnailosd.generated.resources.burn_multiclip_empty
import dev.grogdunn.kwalksnailosd.generated.resources.burn_multiclip_mismatch
import dev.grogdunn.kwalksnailosd.generated.resources.burn_multiclip_muxing
import dev.grogdunn.kwalksnailosd.generated.resources.burn_multiclip_no_osd
import dev.grogdunn.kwalksnailosd.generated.resources.burn_multiclip_progress
import dev.grogdunn.kwalksnailosd.generated.resources.burn_no_ffmpeg_detail
import dev.grogdunn.kwalksnailosd.osd.Osd
import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.srt.SrtFile
import dev.grogdunn.kwalksnailosd.srt.SrtOptions
import dev.grogdunn.kwalksnailosd.uiText
import dev.grogdunn.kwalksnailosd.video.BurnProgress
import dev.grogdunn.kwalksnailosd.video.BurnResult
import dev.grogdunn.kwalksnailosd.video.BurnSettings
import dev.grogdunn.kwalksnailosd.video.OsdOffset
import dev.grogdunn.kwalksnailosd.video.withOffset
import dev.grogdunn.kwalksnailosd.video.VideoBurnException
import dev.grogdunn.kwalksnailosd.video.VideoBurner
import dev.grogdunn.kwalksnailosd.video.VideoCodec
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import dev.grogdunn.kwalksnailosd.video.VideoSize
import dev.grogdunn.kwalksnailosd.video.forBundledFfmpeg
import kotlinx.coroutines.CancellationException
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class PreparedClip(
    val video: Path,
    val videoInfo: VideoInfo,
    val osd: Osd,
    val srt: SrtFile? = null,
    val offset: OsdOffset? = null,
)

class MultiClipBurner(
    private val ffmpeg: Path,
    private val videoBurner: VideoBurner = VideoBurner(ffmpeg),
) {
    fun burn(
        clips: List<PreparedClip>,
        font: Font,
        settings: BurnSettings,
        output: Path,
        onProgress: (BurnProgress) -> Unit = {},
        onPreviewFrame: (BufferedImage) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        srtOptions: SrtOptions = SrtOptions(),
        targetSize: VideoSize? = null,
    ): BurnResult {
        validate(clips, targetSize)
        throwIfCancelled(isCancelled)
        val workDir = createWorkDir(output)
        try {
            val started = System.nanoTime()
            val preview = ThrottledPreview(onPreviewFrame)
            val burned = ArrayList<BurnResult>(clips.size)
            val videos = ArrayList<Path>(clips.size)
            clips.forEachIndexed { index, clip ->
                throwIfCancelled(isCancelled)
                val tempVideo = workDir.resolve("clip-$index.mp4")
                val result = videoBurner.burn(
                    input = clip.video,
                    output = tempVideo,
                    videoInfo = clip.videoInfo,
                    osd = clip.osd,
                    font = font,
                    settings = settings.withOffset(clip.offset),
                    onProgress = { progress ->
                        val fraction = overallFraction(index, clips.size, progress.fraction)
                        onProgress(BurnProgress(fraction, progressMessage(index + 1, clips.size, fraction, started)))
                    },
                    isCancelled = isCancelled,
                    srt = clip.srt,
                    srtOptions = srtOptions,
                    onPreviewFrame = preview::offer,
                    copyAudio = false,
                    scaleTo = targetSize,
                )
                burned.add(result)
                videos.add(tempVideo)
            }
            throwIfCancelled(isCancelled)
            onProgress(BurnProgress(1f, uiText(Res.string.burn_multiclip_muxing)))
            val audios = clips.mapIndexed { index, clip ->
                throwIfCancelled(isCancelled)
                val audio = workDir.resolve("clip-$index.m4a")
                writeClipAudio(
                    clip,
                    audioSeconds(burned[index].framesWritten, clip.videoInfo.frameRate),
                    audio,
                    isCancelled,
                )
                audio
            }
            val joinedVideo = workDir.resolve("video.mp4")
            val joinedAudio = workDir.resolve("audio.m4a")
            concatVideos(videos, settings.codec, joinedVideo, isCancelled)
            concatAudio(audios, joinedAudio, isCancelled)
            mux(joinedVideo, joinedAudio, output, isCancelled)
            onProgress(BurnProgress(1f, uiText(Res.string.burn_multiclip_muxing)))
            val first = burned.first()
            return BurnResult(
                output = output,
                encoderName = first.encoderName,
                hardware = burned.all { it.hardware },
                framesWritten = burned.sumOf { it.framesWritten },
                usedSoftwareFallback = burned.any { it.usedSoftwareFallback },
                fallbackDetail = burned.firstNotNullOfOrNull { it.fallbackDetail },
            )
        } catch (cancelled: CancellationException) {
            deleteQuietly(output)
            throw cancelled
        } catch (failure: Exception) {
            deleteQuietly(output)
            throw failure
        } finally {
            deleteTree(workDir)
        }
    }

    private fun validate(clips: List<PreparedClip>, targetSize: VideoSize? = null) {
        if (clips.isEmpty()) {
            throw VideoBurnException(uiText(Res.string.burn_multiclip_empty))
        }
        if (clips.any { it.osd.frames.isEmpty() }) {
            throw VideoBurnException(uiText(Res.string.burn_multiclip_no_osd))
        }
        val reference = clips.first().videoInfo
        val mismatch = clips.any { clip ->
            val info = clip.videoInfo
            val resolutionDiffers = targetSize == null &&
                (info.width != reference.width || info.height != reference.height)
            resolutionDiffers || abs(info.frameRate - reference.frameRate) > FPS_TOLERANCE
        }
        if (mismatch) {
            throw VideoBurnException(uiText(Res.string.burn_multiclip_mismatch))
        }
    }

    private fun writeClipAudio(clip: PreparedClip, seconds: String, output: Path, isCancelled: () -> Boolean) {
        val command = mutableListOf(
            ffmpeg.toString(),
            "-y",
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
        )
        if (clip.videoInfo.hasAudio) {
            command += listOf("-i", clip.video.toString(), "-vn", "-t", seconds)
        } else {
            command += listOf("-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo", "-t", seconds)
        }
        command += listOf("-ar", "48000", "-ac", "2", "-c:a", "aac", "-b:a", "160k", output.toString())
        runFfmpeg(command, isCancelled)
    }

    private fun concatVideos(videos: List<Path>, codec: VideoCodec, joined: Path, isCancelled: () -> Boolean) {
        val bsf = when (codec) {
            VideoCodec.H264 -> "h264_mp4toannexb"
            VideoCodec.H265 -> "hevc_mp4toannexb"
        }
        val streams = videos.mapIndexed { index, video ->
            val transport = video.resolveSibling("clip-$index.ts")
            runFfmpeg(
                listOf(
                    ffmpeg.toString(),
                    "-y",
                    "-nostdin",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-i",
                    video.toString(),
                    "-c",
                    "copy",
                    "-bsf:v",
                    bsf,
                    "-f",
                    "mpegts",
                    transport.toString(),
                ),
                isCancelled,
            )
            transport
        }
        val list = joined.resolveSibling("videos.txt")
        writeConcatList(list, streams)
        runFfmpeg(
            listOf(
                ffmpeg.toString(),
                "-y",
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "concat",
                "-safe",
                "0",
                "-i",
                list.toString(),
                "-c",
                "copy",
                joined.toString(),
            ),
            isCancelled,
        )
    }

    private fun concatAudio(audios: List<Path>, joined: Path, isCancelled: () -> Boolean) {
        val list = joined.resolveSibling("audios.txt")
        writeConcatList(list, audios)
        runFfmpeg(
            listOf(
                ffmpeg.toString(),
                "-y",
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "concat",
                "-safe",
                "0",
                "-i",
                list.toString(),
                "-c",
                "copy",
                joined.toString(),
            ),
            isCancelled,
        )
    }

    private fun mux(video: Path, audio: Path, output: Path, isCancelled: () -> Boolean) {
        output.parent?.let { parent ->
            if (!Files.isDirectory(parent)) Files.createDirectories(parent)
        }
        runFfmpeg(
            listOf(
                ffmpeg.toString(),
                "-y",
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                video.toString(),
                "-i",
                audio.toString(),
                "-map",
                "0:v:0",
                "-map",
                "1:a:0",
                "-c:v",
                "copy",
                "-c:a",
                "copy",
                "-shortest",
                output.toString(),
            ),
            isCancelled,
        )
    }

    private fun runFfmpeg(command: List<String>, isCancelled: () -> Boolean) {
        throwIfCancelled(isCancelled)
        val process = try {
            ProcessBuilder(command).forBundledFfmpeg(ffmpeg).redirectErrorStream(true).start()
        } catch (failure: Exception) {
            throw VideoBurnException(
                uiText(Res.string.burn_ffmpeg_start, ffmpeg, failure.message ?: uiText(Res.string.burn_no_ffmpeg_detail)),
                failure,
            )
        }
        val log = StringBuilder()
        val reader = thread(isDaemon = true, name = "ffmpeg-multiclip") {
            try {
                process.inputStream.bufferedReader().use { stream ->
                    val chunk = CharArray(1024)
                    while (true) {
                        val read = stream.read(chunk)
                        if (read < 0) break
                        synchronized(log) {
                            log.append(chunk, 0, read)
                            if (log.length > 8_000) log.delete(0, log.length - 4_000)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                if (isCancelled()) {
                    process.destroyForcibly()
                    throw CancellationException("Burn cancelled")
                }
            }
            if (process.exitValue() != 0) {
                val detail = synchronized(log) { log.toString() }.trim().takeLast(500)
                    .ifBlank { uiText(Res.string.burn_no_ffmpeg_detail) }
                throw VideoBurnException(uiText(Res.string.burn_encoder_stopped, "ffmpeg", detail))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            process.destroyForcibly()
            throw CancellationException("Burn cancelled", interrupted)
        } finally {
            if (process.isAlive) process.destroyForcibly()
            reader.join(500)
        }
    }

    private fun progressMessage(clipNumber: Int, clipCount: Int, fraction: Float, startedNanos: Long): String {
        val percent = (fraction * 100f).roundToInt().coerceIn(0, 100)
        val eta = if (fraction > 0f) formatEta(remainingSeconds(fraction, startedNanos)) else "--:--"
        return uiText(Res.string.burn_multiclip_progress, clipNumber, clipCount, percent, eta)
    }

    private class ThrottledPreview(
        private val callback: (BufferedImage) -> Unit,
    ) {
        private var lastSentNanos = Long.MIN_VALUE

        fun offer(image: BufferedImage) {
            val now = System.nanoTime()
            if (lastSentNanos != Long.MIN_VALUE && now - lastSentNanos < PREVIEW_INTERVAL_NANOS) return
            lastSentNanos = now
            callback(downscale(image))
        }
    }

    private companion object {
        const val FPS_TOLERANCE = 0.05
        const val PREVIEW_MAX_EDGE = 480
        const val PREVIEW_INTERVAL_NANOS = 500_000_000L

        fun overallFraction(index: Int, count: Int, clipFraction: Float): Float {
            if (count <= 0) return 0f
            return ((index + clipFraction.coerceIn(0f, 1f)) / count).coerceIn(0f, 1f)
        }

        fun audioSeconds(framesWritten: Int, frameRate: Double): String {
            val seconds = if (frameRate > 0.0) framesWritten / frameRate else framesWritten.toDouble()
            return String.format(Locale.US, "%.3f", seconds.coerceAtLeast(0.001))
        }

        fun remainingSeconds(fraction: Float, startedNanos: Long): Long {
            val elapsedNanos = (System.nanoTime() - startedNanos).coerceAtLeast(0L)
            val remainingNanos = elapsedNanos.toDouble() * (1.0 - fraction) / fraction
            return (remainingNanos / 1_000_000_000.0).roundToLong().coerceAtLeast(0L)
        }

        fun formatEta(totalSeconds: Long): String {
            val seconds = totalSeconds.coerceAtLeast(0L)
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, secs)
            }
        }

        fun downscale(image: BufferedImage): BufferedImage {
            val longest = maxOf(image.width, image.height)
            if (longest <= PREVIEW_MAX_EDGE) return image
            val scale = PREVIEW_MAX_EDGE.toDouble() / longest
            val width = (image.width * scale).roundToInt().coerceAtLeast(1)
            val height = (image.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val graphics = scaled.createGraphics()
            try {
                graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                )
                graphics.drawImage(image, 0, 0, width, height, null)
            } finally {
                graphics.dispose()
            }
            return scaled
        }

        fun createWorkDir(output: Path): Path {
            val parent = output.toAbsolutePath().parent
            return if (parent != null && Files.isDirectory(parent)) {
                Files.createTempDirectory(parent, "multiclip-burn-")
            } else {
                Files.createTempDirectory("multiclip-burn-")
            }
        }

        fun writeConcatList(path: Path, files: List<Path>) {
            val text = files.joinToString("\n") { file ->
                val absolute = file.toAbsolutePath().toString().replace("'", "'\\''")
                "file '$absolute'"
            } + "\n"
            Files.writeString(path, text)
        }

        fun throwIfCancelled(isCancelled: () -> Boolean) {
            if (isCancelled()) throw CancellationException("Burn cancelled")
        }

        fun deleteQuietly(path: Path) {
            try {
                Files.deleteIfExists(path)
            } catch (_: Exception) {
            }
        }

        fun deleteTree(path: Path) {
            if (!Files.exists(path)) return
            try {
                Files.walk(path).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { candidate ->
                        try {
                            Files.deleteIfExists(candidate)
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
