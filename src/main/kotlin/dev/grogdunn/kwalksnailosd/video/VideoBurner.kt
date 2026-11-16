package dev.grogdunn.kwalksnailosd.video

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.burn_bad_bitrate
import dev.grogdunn.kwalksnailosd.generated.resources.burn_bad_frame_rate
import dev.grogdunn.kwalksnailosd.generated.resources.burn_bad_resolution
import dev.grogdunn.kwalksnailosd.generated.resources.burn_both_encoders_failed
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
    private val probeResults = mutableMapOf<String, Boolean>()

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
    ): BurnResult {
        validate(input, output, videoInfo, osd, settings)
        val speedFactor = osdPlaybackSpeedFactor(videoInfo.duration, osd.duration, settings.adjustPlaybackSpeed)
        val choice = selectEncoder(settings)
        val softwareBecauseHardwareIsMissing = settings.preferHardware && !choice.hardware
        logger.info { "Burn with ${choice.name} (hardware=${choice.hardware}), OSD factor $speedFactor, ffmpeg=$ffmpeg" }
        return try {
            encode(
                input, output, videoInfo, osd, font, settings, choice, speedFactor,
                usedSoftwareFallback = softwareBecauseHardwareIsMissing,
                onProgress, isCancelled, srt, srtOptions,
            )
        } catch (cancelled: CancellationException) {
            deleteQuietly(output)
            throw cancelled
        } catch (failure: Exception) {
            deleteQuietly(output)
            if (!choice.hardware) throw failure
            val software = softwareEncoder(settings.codec)
            logger.warn(failure) { "Encoder ${choice.name} unusable, falling back to ${software.name}" }
            onProgress(BurnProgress(0f, uiText(Res.string.burn_hardware_unusable, choice.name, software.name)))
            try {
                encode(
                    input, output, videoInfo, osd, font, settings, software, speedFactor,
                    usedSoftwareFallback = true, onProgress, isCancelled, srt, srtOptions,
                )
            } catch (cancelled: CancellationException) {
                deleteQuietly(output)
                throw cancelled
            } catch (softwareFailure: Exception) {
                deleteQuietly(output)
                throw VideoBurnException(
                    uiText(
                        Res.string.burn_both_encoders_failed,
                        choice.name,
                        failure.message ?: uiText(Res.string.burn_no_ffmpeg_detail),
                        software.name,
                        softwareFailure.message ?: uiText(Res.string.burn_no_ffmpeg_detail),
                    ),
                    softwareFailure,
                )
            }
        }
    }

    private fun encode(
        input: Path,
        output: Path,
        videoInfo: VideoInfo,
        osd: Osd,
        font: Font,
        settings: BurnSettings,
        encoder: EncoderChoice,
        speedFactor: Double,
        usedSoftwareFallback: Boolean,
        onProgress: (BurnProgress) -> Unit,
        isCancelled: () -> Boolean,
        srt: SrtFile?,
        srtOptions: SrtOptions,
    ): BurnResult {
        throwIfCancelled(isCancelled)
        val width = videoInfo.width
        val height = videoInfo.height
        val frameBytes = width * height * 3
        val expectedFrames = expectedFrameCount(videoInfo)
        val decoder = startDecoder(input, expectedFrames)
        val decoderErrors = collectStderr(decoder)
        val encoderProcess = startEncoder(input, output, videoInfo, encoder, settings.bitrateMbps, videoInfo.hasAudio)
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
                        drawOsd(raw, width, height, osdFrame, font, settings.offsetX, settings.offsetY, srt, srtOptions, videoTimeMillis)
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
    ) {
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
    }

    private fun startDecoder(input: Path, frames: Int): Process {
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
        command += listOf("-f", "rawvideo", "-pix_fmt", "bgr24", "-")
        return startProcess(command)
    }

    private fun startEncoder(
        input: Path,
        output: Path,
        videoInfo: VideoInfo,
        encoder: EncoderChoice,
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
        command += listOf(
            "-c:v",
            encoder.name,
            "-b:v",
            "${bitrateMbps}M",
            "-pix_fmt",
            "yuv420p",
        )
        command += encoder.extraArgs
        command += output.toString()
        return startProcess(command)
    }

    private fun softwareEncoder(codec: VideoCodec): EncoderChoice {
        val name = FfmpegEncoders.firstSoftware(ffmpeg, codec)
            ?: throw VideoBurnException(noSoftwareEncoderMessage(codec))
        return EncoderChoice(name, hardware = false)
    }

    private fun selectEncoder(settings: BurnSettings): EncoderChoice {
        val software = softwareEncoder(settings.codec)
        if (!settings.preferHardware) return software
        for (candidate in hardwareEncoders(settings.codec)) {
            if (probe(candidate)) return candidate
        }
        return software
    }

    private fun probe(encoder: EncoderChoice): Boolean {
        probeResults[encoder.name]?.let { return it }
        val command = mutableListOf(
            ffmpeg.toString(),
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-f",
            "lavfi",
            "-i",
            "nullsrc=s=64x64:d=0.1",
            "-frames:v",
            "1",
            "-c:v",
            encoder.name,
            "-f",
            "null",
            "-",
        )
        val works = try {
            val process = startProcess(command)
            discardStdout(process)
            val errors = collectStderr(process)
            val finished = process.waitFor(4, TimeUnit.SECONDS)
            if (!finished) {
                destroyQuietly(process)
                false
            } else if (process.exitValue() == 0) {
                true
            } else {
                logger.info { "Encoder ${encoder.name} unavailable: ${tail(errors)}" }
                false
            }
        } catch (failure: Exception) {
            logger.info(failure) { "Probe of ${encoder.name} failed" }
            false
        }
        probeResults[encoder.name] = works
        return works
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

    private fun encoderFailureMessage(encoder: EncoderChoice, errors: StringBuilder, extra: String? = null): String {
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

    private data class EncoderChoice(val name: String, val hardware: Boolean, val extraArgs: List<String> = emptyList())

    companion object {
        private fun hardwareEncoders(codec: VideoCodec): List<EncoderChoice> {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val h264 = codec == VideoCodec.H264
            return when {
                os.contains("win") -> listOf(
                    EncoderChoice(if (h264) "h264_amf" else "hevc_amf", hardware = true),
                    EncoderChoice(if (h264) "h264_nvenc" else "hevc_nvenc", hardware = true),
                    EncoderChoice(if (h264) "h264_qsv" else "hevc_qsv", hardware = true),
                )
                os.contains("mac") -> listOf(
                    if (h264) {
                        EncoderChoice("h264_videotoolbox", hardware = true)
                    } else {
                        EncoderChoice("hevc_videotoolbox", hardware = true, extraArgs = listOf("-tag:v", "hvc1"))
                    }
                )
                else -> listOf(
                    EncoderChoice(if (h264) "h264_nvenc" else "hevc_nvenc", hardware = true),
                    EncoderChoice(if (h264) "h264_qsv" else "hevc_qsv", hardware = true),
                    EncoderChoice(if (h264) "h264_vaapi" else "hevc_vaapi", hardware = true),
                    EncoderChoice(if (h264) "h264_v4l2m2m" else "hevc_v4l2m2m", hardware = true),
                )
            }
        }
    }
}
