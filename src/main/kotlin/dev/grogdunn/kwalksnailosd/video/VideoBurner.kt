package dev.grogdunn.kwalksnailosd.video

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.burn_bad_bitrate
import dev.grogdunn.kwalksnailosd.generated.resources.burn_bad_frame_rate
import dev.grogdunn.kwalksnailosd.generated.resources.burn_bad_resolution
import dev.grogdunn.kwalksnailosd.generated.resources.burn_encoder_stopped
import dev.grogdunn.kwalksnailosd.generated.resources.burn_ffmpeg_no_frames
import dev.grogdunn.kwalksnailosd.generated.resources.burn_ffmpeg_short_frame
import dev.grogdunn.kwalksnailosd.generated.resources.burn_ffmpeg_start
import dev.grogdunn.kwalksnailosd.generated.resources.burn_ffmpeg_timeout
import dev.grogdunn.kwalksnailosd.generated.resources.burn_frame_buffer
import dev.grogdunn.kwalksnailosd.generated.resources.burn_hardware_unusable
import dev.grogdunn.kwalksnailosd.generated.resources.burn_no_ffmpeg_detail
import dev.grogdunn.kwalksnailosd.generated.resources.burn_no_osd_frames
import dev.grogdunn.kwalksnailosd.generated.resources.burn_odd_resolution
import dev.grogdunn.kwalksnailosd.generated.resources.burn_progress
import dev.grogdunn.kwalksnailosd.generated.resources.burn_same_file
import dev.grogdunn.kwalksnailosd.generated.resources.burn_saved_progress
import dev.grogdunn.kwalksnailosd.osd.Frame
import dev.grogdunn.kwalksnailosd.uiText
import dev.grogdunn.kwalksnailosd.osd.Osd
import dev.grogdunn.kwalksnailosd.osd.OsdRenderer
import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.osd.osdFrameAt
import dev.grogdunn.kwalksnailosd.osd.osdPlaybackSpeedFactor
import dev.grogdunn.kwalksnailosd.srt.SrtFile
import dev.grogdunn.kwalksnailosd.srt.SrtOptions
import dev.grogdunn.kwalksnailosd.srt.SrtOverlay
import dev.grogdunn.kwalksnailosd.srt.srtCueAt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class VideoBurnException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class VideoBurner(
    private val ffmpeg: Path,
    private val osdRenderer: OsdRenderer = OsdRenderer(),
) {
    private val logger = KotlinLogging.logger {}
    private val catalog = HardwareEncoderCatalog(ffmpeg)

    fun burn(
        input: Path,
        output: Path,
        videoInfo: VideoInfo,
        osd: Osd,
        font: Font,
        settings: BurnSettings,
        onProgress: (BurnProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        srt: SrtFile? = null,
        srtOptions: SrtOptions = SrtOptions(),
        onPreviewFrame: ((BufferedImage) -> Unit)? = null,
        copyAudio: Boolean? = null,
        scaleTo: VideoSize? = null,
    ): BurnResult {
        val effective = if (
            scaleTo != null && (scaleTo.width != videoInfo.width || scaleTo.height != videoInfo.height)
        ) {
            videoInfo.copy(width = scaleTo.width, height = scaleTo.height)
        } else {
            videoInfo
        }
        validate(input, output, effective, osd, settings)
        val speedFactor = osdPlaybackSpeedFactor(videoInfo.duration, osd.duration, settings.adjustPlaybackSpeed)
        val selection = catalog.resolve(settings.codec, settings.encoder)
        val initialNote = selection.fallbackNote
        var fallbackDetail = initialNote
        if (initialNote != null) {
            logger.info { initialNote }
            onProgress(BurnProgress(0f, initialNote))
        }
        logger.info {
            "Burn with ${selection.chosen.name} (hardware=${selection.chosen.hardware}), OSD factor $speedFactor, ffmpeg=$ffmpeg"
        }
        return try {
            val result = encodeWithFallback(
                primary = selection.chosen,
                fallback = selection.softwareFallback,
                encode = { recipe ->
                    encode(
                        input, output, effective, osd, font, settings, recipe, speedFactor,
                        usedSoftwareFallback = fallbackDetail != null,
                        onProgress, isCancelled, srt, srtOptions,
                        onPreviewFrame, copyAudio ?: videoInfo.hasAudio,
                        scaleTo = if (effective === videoInfo) null else scaleTo,
                    )
                },
                onFallback = { failed, next, error ->
                    deleteQuietly(output)
                    val message = uiText(Res.string.burn_hardware_unusable, failed.name, next.name)
                    fallbackDetail = message
                    logger.warn(error) { message }
                    onProgress(BurnProgress(0f, message))
                },
            )
            result.copy(usedSoftwareFallback = fallbackDetail != null, fallbackDetail = fallbackDetail)
        } catch (cancelled: CancellationException) {
            deleteQuietly(output)
            throw cancelled
        } catch (failure: Exception) {
            deleteQuietly(output)
            throw failure
        }
    }

    private fun encode(
        input: Path,
        output: Path,
        videoInfo: VideoInfo,
        osd: Osd,
        font: Font,
        settings: BurnSettings,
        encoder: EncoderRecipe,
        speedFactor: Double,
        usedSoftwareFallback: Boolean,
        onProgress: (BurnProgress) -> Unit,
        isCancelled: () -> Boolean,
        srt: SrtFile?,
        srtOptions: SrtOptions,
        onPreviewFrame: ((BufferedImage) -> Unit)?,
        copyAudio: Boolean,
        scaleTo: VideoSize? = null,
    ): BurnResult {
        throwIfCancelled(isCancelled)
        val width = videoInfo.width
        val height = videoInfo.height
        val frameBytes = width * height * 3
        val expectedFrames = expectedFrameCount(videoInfo)
        val decoder = startDecoder(input, expectedFrames, scaleTo)
        val decoderErrors = collectStderr(decoder)
        val encoderProcess = startEncoder(input, output, videoInfo, encoder, settings.bitrateMbps, copyAudio)
        discardStdout(encoderProcess)
        val encoderErrors = collectStderr(encoderProcess)
        var framesWritten = 0
        try {
            val raw = ByteArray(frameBytes)
            val encoderOutput = BufferedOutputStream(encoderProcess.outputStream, frameBytes.coerceAtMost(1 shl 20))
            decoder.inputStream.use { decoderOutput ->
                encoderOutput.use { encoded ->
                    while (readFully(decoderOutput, raw)) {
                        throwIfCancelled(isCancelled)
                        if (!encoderProcess.isAlive) {
                            throw VideoBurnException(encoderFailureMessage(encoder, encoderErrors))
                        }
                        val videoTimeMillis = framesWritten / videoInfo.frameRate * 1000.0
                        val osdFrame = osdFrameAt(osd.frames, videoTimeMillis, speedFactor)
                            ?: throw VideoBurnException(uiText(Res.string.burn_no_osd_frames))
                        val preview = drawOsd(
                            raw, width, height, osdFrame, font, settings.offsetX, settings.offsetY,
                            srt, srtOptions, videoTimeMillis,
                        )
                        if (onPreviewFrame != null) onPreviewFrame(preview)
                        encoded.write(raw)
                        framesWritten += 1
                        if (framesWritten == 1 || framesWritten == expectedFrames || framesWritten % 5 == 0) {
                            val fraction = if (expectedFrames > 0) framesWritten.toFloat() / expectedFrames else 0f
                            onProgress(
                                BurnProgress(
                                    fraction.coerceIn(0f, 1f),
                                    uiText(Res.string.burn_progress, framesWritten, expectedFrames, encoder.name),
                                )
                            )
                        }
                    }
                }
            }
            destroyQuietly(decoder)
            if (!encoderProcess.waitFor(30, TimeUnit.MINUTES)) {
                destroyQuietly(encoderProcess)
                throw VideoBurnException(uiText(Res.string.burn_ffmpeg_timeout, tail(encoderErrors)))
            }
            if (encoderProcess.exitValue() != 0) {
                throw VideoBurnException(encoderFailureMessage(encoder, encoderErrors))
            }
            if (framesWritten == 0) {
                throw VideoBurnException(uiText(Res.string.burn_ffmpeg_no_frames, tail(decoderErrors)))
            }
            onProgress(BurnProgress(1f, uiText(Res.string.burn_saved_progress, output.fileName, encoder.name)))
            return BurnResult(output, encoder.name, encoder.hardware, framesWritten, usedSoftwareFallback)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            throw CancellationException("Burn cancelled", interrupted)
        } catch (interrupted: InterruptedIOException) {
            throw CancellationException("Burn cancelled", interrupted)
        } catch (burn: VideoBurnException) {
            throw burn
        } catch (failure: Exception) {
            throw VideoBurnException(encoderFailureMessage(encoder, encoderErrors, failure.message), failure)
        } finally {
            destroyQuietly(decoder)
            if (encoderProcess.isAlive) destroyQuietly(encoderProcess)
        }
    }

    private fun drawOsd(
        bgr: ByteArray,
        width: Int,
        height: Int,
        frame: Frame,
        font: Font,
        offsetX: Int,
        offsetY: Int,
        srt: SrtFile?,
        srtOptions: SrtOptions,
        videoTimeMillis: Double,
    ): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        val raster = (image.raster.dataBuffer as DataBufferByte).data
        if (raster.size != bgr.size) {
            throw VideoBurnException(uiText(Res.string.burn_frame_buffer, raster.size, bgr.size))
        }
        System.arraycopy(bgr, 0, raster, 0, bgr.size)
        osdRenderer.renderInPlace(image, frame, font, offsetX, offsetY)
        val srtData = srt?.let { srtCueAt(it.cues, videoTimeMillis)?.data }
        if (srtData != null) SrtOverlay.draw(image, srtData, srtOptions)
        System.arraycopy(raster, 0, bgr, 0, bgr.size)
        return image
    }

    private fun startDecoder(input: Path, frames: Int, scaleTo: VideoSize? = null): Process {
        val command = mutableListOf(
            ffmpeg.toString(),
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            input.toString(),
            "-an",
            "-fps_mode",
            "cfr",
        )
        if (frames > 0) {
            command += listOf("-frames:v", frames.toString())
        }
        if (scaleTo != null) {
            val w = scaleTo.width
            val h = scaleTo.height
            command += listOf(
                "-vf",
                "scale=$w:$h:force_original_aspect_ratio=decrease,pad=$w:$h:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1",
            )
        }
        command += listOf("-f", "rawvideo", "-pix_fmt", "bgr24", "-")
        return startProcess(command)
    }

    private fun startEncoder(
        input: Path,
        output: Path,
        videoInfo: VideoInfo,
        encoder: EncoderRecipe,
        bitrateMbps: Int,
        copyAudio: Boolean,
    ): Process {
        val fps = String.format(Locale.US, "%.6f", videoInfo.frameRate)
        val command = mutableListOf(
            ffmpeg.toString(),
            "-y",
            "-hide_banner",
            "-loglevel",
            "error",
        )
        command += encoder.globalArgs
        command += listOf(
            "-f",
            "rawvideo",
            "-pix_fmt",
            "bgr24",
            "-s",
            "${videoInfo.width}x${videoInfo.height}",
            "-r",
            fps,
            "-i",
            "pipe:0",
            "-i",
            input.toString(),
            "-map",
            "0:v:0",
        )
        if (copyAudio) {
            command += listOf("-map", "1:a:0", "-c:a", "copy")
        }
        command += encoderOutputArgs(encoder, bitrateMbps, includeFileArgs = true)
        command += output.toString()
        return startProcess(command)
    }

    private fun startProcess(command: List<String>): Process =
        try {
            ProcessBuilder(command).forBundledFfmpeg(ffmpeg).start()
        } catch (failure: Exception) {
            throw VideoBurnException(
                uiText(Res.string.burn_ffmpeg_start, ffmpeg, failure.message ?: uiText(Res.string.burn_no_ffmpeg_detail)),
                failure,
            )
        }

    private fun collectStderr(process: Process): StringBuilder {
        val text = StringBuilder()
        thread(isDaemon = true, name = "ffmpeg-stderr") {
            try {
                process.errorStream.bufferedReader().use { reader ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        synchronized(text) {
                            text.append(buffer, 0, read)
                            if (text.length > 8_000) text.delete(0, text.length - 4_000)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return text
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) {
                if (offset == 0) return false
                throw VideoBurnException(uiText(Res.string.burn_ffmpeg_short_frame, offset, buffer.size))
            }
            offset += read
        }
        return true
    }

    private fun validate(input: Path, output: Path, videoInfo: VideoInfo, osd: Osd, settings: BurnSettings) {
        if (input.toAbsolutePath().normalize() == output.toAbsolutePath().normalize()) {
            throw VideoBurnException(uiText(Res.string.burn_same_file))
        }
        if (videoInfo.width <= 0 || videoInfo.height <= 0) {
            throw VideoBurnException(uiText(Res.string.burn_bad_resolution))
        }
        if (videoInfo.width % 2 != 0 || videoInfo.height % 2 != 0) {
            throw VideoBurnException(uiText(Res.string.burn_odd_resolution, videoInfo.width, videoInfo.height))
        }
        if (videoInfo.frameRate <= 0.0) {
            throw VideoBurnException(uiText(Res.string.burn_bad_frame_rate))
        }
        if (settings.bitrateMbps <= 0) {
            throw VideoBurnException(uiText(Res.string.burn_bad_bitrate))
        }
        if (osd.frames.isEmpty()) {
            throw VideoBurnException(uiText(Res.string.burn_no_osd_frames))
        }
    }

    private fun expectedFrameCount(videoInfo: VideoInfo): Int {
        if (videoInfo.frameNumber > 0) return videoInfo.frameNumber
        return (videoInfo.duration.toMillis() / 1000.0 * videoInfo.frameRate).roundToInt().coerceAtLeast(1)
    }

    private fun throwIfCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw CancellationException("Burn cancelled")
    }

    private fun encoderFailureMessage(encoder: EncoderRecipe, errors: StringBuilder, extra: String? = null): String {
        val detail = tail(errors).ifBlank { extra ?: uiText(Res.string.burn_no_ffmpeg_detail) }
        return uiText(Res.string.burn_encoder_stopped, encoder.name, detail)
    }

    private fun tail(text: StringBuilder): String {
        val value = synchronized(text) { text.toString() }.trim()
        return if (value.length <= 500) value else value.takeLast(500)
    }

    private fun discardStdout(process: Process) {
        thread(isDaemon = true, name = "ffmpeg-stdout") {
            try {
                process.inputStream.transferTo(java.io.OutputStream.nullOutputStream())
            } catch (_: Exception) {
            }
        }
    }

    private fun destroyQuietly(process: Process) {
        try {
            if (!process.isAlive) return
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        } catch (_: Exception) {
            process.destroyForcibly()
        }
    }

    private fun deleteQuietly(output: Path) {
        try {
            Files.deleteIfExists(output)
        } catch (failure: Exception) {
            logger.warn(failure) { "Could not delete $output" }
        }
    }

}
