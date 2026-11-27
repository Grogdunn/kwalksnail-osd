package dev.grogdunn.kwalksnailosd.video

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.burn_bad_bitrate
import dev.grogdunn.kwalksnailosd.generated.resources.burn_bad_frame_rate
import dev.grogdunn.kwalksnailosd.generated.resources.burn_bad_resolution
import dev.grogdunn.kwalksnailosd.generated.resources.burn_encoder_stopped
import dev.grogdunn.kwalksnailosd.generated.resources.burn_failed
import dev.grogdunn.kwalksnailosd.generated.resources.burn_ffmpeg_no_frames
import dev.grogdunn.kwalksnailosd.generated.resources.burn_ffmpeg_start
import dev.grogdunn.kwalksnailosd.generated.resources.burn_ffmpeg_timeout
import dev.grogdunn.kwalksnailosd.generated.resources.burn_hardware_unusable
import dev.grogdunn.kwalksnailosd.generated.resources.burn_no_ffmpeg_detail
import dev.grogdunn.kwalksnailosd.generated.resources.burn_no_osd_frames
import dev.grogdunn.kwalksnailosd.generated.resources.burn_saved_progress
import dev.grogdunn.kwalksnailosd.generated.resources.osd_export_missing_encoder
import dev.grogdunn.kwalksnailosd.generated.resources.osd_export_png_no_space
import dev.grogdunn.kwalksnailosd.generated.resources.osd_export_progress
import dev.grogdunn.kwalksnailosd.osd.Osd
import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.osd.OsdRenderer
import dev.grogdunn.kwalksnailosd.osd.osdFrameAt
import dev.grogdunn.kwalksnailosd.osd.osdPlaybackSpeedFactor
import dev.grogdunn.kwalksnailosd.srt.SrtFile
import dev.grogdunn.kwalksnailosd.srt.SrtOptions
import dev.grogdunn.kwalksnailosd.srt.SrtOverlay
import dev.grogdunn.kwalksnailosd.srt.srtCueAt
import dev.grogdunn.kwalksnailosd.uiText
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class OsdExporter(
    private val ffmpeg: Path,
    private val osdRenderer: OsdRenderer = OsdRenderer(),
) {
    fun export(
        videoInfo: VideoInfo,
        osd: Osd,
        font: Font,
        settings: OsdExportSettings,
        output: Path,
        onProgress: (BurnProgress) -> Unit = {},
        onPreviewFrame: ((BufferedImage) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
        srt: SrtFile? = null,
        srtOptions: SrtOptions = SrtOptions(),
    ): BurnResult {
        validate(videoInfo, osd, settings)
        val expectedFrames = expectedFrameCount(videoInfo)
        throwIfCancelled(isCancelled)
        return when (settings.mode) {
            OsdExportMode.PngSequence -> exportPng(
                videoInfo, osd, font, settings, output, expectedFrames, onProgress, onPreviewFrame, isCancelled, srt, srtOptions,
            )
            OsdExportMode.ChromaKey -> exportChroma(
                videoInfo, osd, font, settings, output, expectedFrames, onProgress, onPreviewFrame, isCancelled, srt, srtOptions,
            )
            OsdExportMode.ProRes4444 -> exportAlphaVideo(
                videoInfo, osd, font, settings, output, expectedFrames, onProgress, onPreviewFrame, isCancelled, srt, srtOptions,
                encoderName = proresEncoder(),
                pixelFormat = "yuva444p10le",
                extraArgs = listOf("-profile:v", "4"),
            )
            OsdExportMode.Vp9Alpha -> exportAlphaVideo(
                videoInfo, osd, font, settings, output, expectedFrames, onProgress, onPreviewFrame, isCancelled, srt, srtOptions,
                encoderName = requireEncoder("libvpx-vp9"),
                pixelFormat = "yuva420p",
                extraArgs = listOf(
                    "-auto-alt-ref", "0",
                    "-lag-in-frames", "0",
                    "-b:v", "${settings.bitrateMbps.coerceAtLeast(1)}M",
                    "-cpu-used", "4",
                    "-metadata:s:v:0", "alpha_mode=1",
                ),
            )
        }
    }

    private fun exportChroma(
        videoInfo: VideoInfo,
        osd: Osd,
        font: Font,
        settings: OsdExportSettings,
        output: Path,
        expectedFrames: Int,
        onProgress: (BurnProgress) -> Unit,
        onPreviewFrame: ((BufferedImage) -> Unit)?,
        isCancelled: () -> Boolean,
        srt: SrtFile?,
        srtOptions: SrtOptions,
    ): BurnResult {
        val selection = HardwareEncoderCatalog(ffmpeg).resolve(settings.codec, settings.encoder)
        if (!FfmpegEncoders.supports(ffmpeg, selection.chosen.name)) {
            throw VideoBurnException(uiText(Res.string.osd_export_missing_encoder, selection.chosen.name))
        }
        var usedFallback = false
        var fallbackDetail = selection.fallbackNote
        val result = encodeWithFallback(
            primary = selection.chosen,
            fallback = selection.softwareFallback,
            encode = { encoder ->
                pipeFrames(
                    videoInfo = videoInfo,
                    osd = osd,
                    font = font,
                    settings = settings,
                    output = output,
                    expectedFrames = expectedFrames,
                    onProgress = onProgress,
                    onPreviewFrame = onPreviewFrame,
                    isCancelled = isCancelled,
                    srt = srt,
                    srtOptions = srtOptions,
                    transparent = false,
                    encoderName = encoder.name,
                    hardware = encoder.hardware,
                    start = { startChromaEncoder(videoInfo, encoder, settings.bitrateMbps, output) },
                )
            },
            onFallback = { failed, next, error ->
                deleteQuietly(output)
                usedFallback = true
                fallbackDetail = uiText(Res.string.burn_hardware_unusable, failed.name, next.name)
                logger.warn(error) { "OSD export encoder ${failed.name} failed, retrying ${next.name}" }
            },
        )
        return result.copy(usedSoftwareFallback = usedFallback, fallbackDetail = fallbackDetail)
    }

    private fun exportAlphaVideo(
        videoInfo: VideoInfo,
        osd: Osd,
        font: Font,
        settings: OsdExportSettings,
        output: Path,
        expectedFrames: Int,
        onProgress: (BurnProgress) -> Unit,
        onPreviewFrame: ((BufferedImage) -> Unit)?,
        isCancelled: () -> Boolean,
        srt: SrtFile?,
        srtOptions: SrtOptions,
        encoderName: String,
        pixelFormat: String,
        extraArgs: List<String>,
    ): BurnResult = pipeFrames(
        videoInfo = videoInfo,
        osd = osd,
        font = font,
        settings = settings,
        output = output,
        expectedFrames = expectedFrames,
        onProgress = onProgress,
        onPreviewFrame = onPreviewFrame,
        isCancelled = isCancelled,
        srt = srt,
        srtOptions = srtOptions,
        transparent = true,
        encoderName = encoderName,
        hardware = false,
        start = { startAlphaEncoder(videoInfo, encoderName, pixelFormat, extraArgs, output) },
    )

    private fun exportPng(
        videoInfo: VideoInfo,
        osd: Osd,
        font: Font,
        settings: OsdExportSettings,
        output: Path,
        expectedFrames: Int,
        onProgress: (BurnProgress) -> Unit,
        onPreviewFrame: ((BufferedImage) -> Unit)?,
        isCancelled: () -> Boolean,
        srt: SrtFile?,
        srtOptions: SrtOptions,
    ): BurnResult {
        val required = estimatePngSequenceBytes(videoInfo.width, videoInfo.height, expectedFrames)
        val space = checkPngDiskSpace(output, required)
        if (!space.enough) {
            throw VideoBurnException(
                uiText(Res.string.osd_export_png_no_space, space.requiredBytes, space.availableBytes),
            )
        }
        val createdDirectory = !Files.exists(output)
        val written = mutableListOf<Path>()
        var framesWritten = 0
        try {
            Files.createDirectories(output)
            val speedFactor = osdPlaybackSpeedFactor(videoInfo.duration, osd.duration, settings.adjustPlaybackSpeed)
            for (index in 0 until expectedFrames) {
                throwIfCancelled(isCancelled)
                val image = renderFrame(videoInfo, osd, font, settings, speedFactor, index, transparent = true, srt, srtOptions)
                onPreviewFrame?.invoke(image)
                val file = output.resolve(pngName(index))
                if (!ImageIO.write(image, "png", file.toFile())) {
                    throw VideoBurnException(uiText(Res.string.burn_failed))
                }
                written.add(file)
                framesWritten += 1
                report(onProgress, framesWritten, expectedFrames, "png")
            }
            onProgress(BurnProgress(1f, uiText(Res.string.burn_saved_progress, output.fileName, "png")))
            return BurnResult(output, encoderName = "png", hardware = false, framesWritten = framesWritten, usedSoftwareFallback = false)
        } catch (cancelled: CancellationException) {
            deletePngOutput(output, createdDirectory, written)
            throw cancelled
        } catch (interrupted: InterruptedException) {
            deletePngOutput(output, createdDirectory, written)
            throw CancellationException("OSD export cancelled", interrupted)
        } catch (interrupted: InterruptedIOException) {
            deletePngOutput(output, createdDirectory, written)
            throw CancellationException("OSD export cancelled", interrupted)
        } catch (failure: Exception) {
            deletePngOutput(output, createdDirectory, written)
            if (isCancelled()) throw CancellationException("OSD export cancelled", failure)
            throw failure
        }
    }

    private fun pipeFrames(
        videoInfo: VideoInfo,
        osd: Osd,
        font: Font,
        settings: OsdExportSettings,
        output: Path,
        expectedFrames: Int,
        onProgress: (BurnProgress) -> Unit,
        onPreviewFrame: ((BufferedImage) -> Unit)?,
        isCancelled: () -> Boolean,
        srt: SrtFile?,
        srtOptions: SrtOptions,
        transparent: Boolean,
        encoderName: String,
        hardware: Boolean,
        start: () -> Process,
    ): BurnResult {
        output.parent?.let { Files.createDirectories(it) }
        deleteQuietly(output)
        val process = start()
        val errors = collectStderr(process)
        discardStdout(process)
        var framesWritten = 0
        try {
            val speedFactor = osdPlaybackSpeedFactor(videoInfo.duration, osd.duration, settings.adjustPlaybackSpeed)
            process.outputStream.use { encoded ->
                for (index in 0 until expectedFrames) {
                    throwIfCancelled(isCancelled)
                    val image = renderFrame(
                        videoInfo, osd, font, settings, speedFactor, index, transparent, srt, srtOptions,
                    )
                    onPreviewFrame?.invoke(image)
                    writeRaw(encoded, image, withAlpha = transparent)
                    framesWritten += 1
                    report(onProgress, framesWritten, expectedFrames, encoderName)
                }
            }
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                throw VideoBurnException(uiText(Res.string.burn_ffmpeg_timeout, tail(errors)))
            }
            if (process.exitValue() != 0 || framesWritten == 0) {
                val message = if (framesWritten == 0) {
                    uiText(Res.string.burn_ffmpeg_no_frames, tail(errors).ifBlank { uiText(Res.string.burn_no_ffmpeg_detail) })
                } else {
                    encoderFailureMessage(encoderName, errors)
                }
                throw VideoBurnException(message)
            }
            onProgress(BurnProgress(1f, uiText(Res.string.burn_saved_progress, output.fileName, encoderName)))
            return BurnResult(output, encoderName, hardware, framesWritten, usedSoftwareFallback = false)
        } catch (cancelled: CancellationException) {
            destroyQuietly(process)
            deleteQuietly(output)
            throw cancelled
        } catch (interrupted: InterruptedException) {
            destroyQuietly(process)
            deleteQuietly(output)
            throw CancellationException("OSD export cancelled", interrupted)
        } catch (interrupted: InterruptedIOException) {
            destroyQuietly(process)
            deleteQuietly(output)
            throw CancellationException("OSD export cancelled", interrupted)
        } catch (failure: Exception) {
            destroyQuietly(process)
            deleteQuietly(output)
            if (isCancelled()) throw CancellationException("OSD export cancelled", failure)
            if (failure is VideoBurnException) throw failure
            throw VideoBurnException(encoderFailureMessage(encoderName, errors, failure.message), failure)
        }
    }

    private fun renderFrame(
        videoInfo: VideoInfo,
        osd: Osd,
        font: Font,
        settings: OsdExportSettings,
        speedFactor: Double,
        frameIndex: Int,
        transparent: Boolean,
        srt: SrtFile?,
        srtOptions: SrtOptions,
    ): BufferedImage {
        val image = if (transparent) {
            BufferedImage(videoInfo.width, videoInfo.height, BufferedImage.TYPE_INT_ARGB)
        } else {
            BufferedImage(videoInfo.width, videoInfo.height, BufferedImage.TYPE_3BYTE_BGR).also { filled ->
                val graphics = filled.createGraphics()
                try {
                    graphics.color = settings.chromaColor
                    graphics.fillRect(0, 0, filled.width, filled.height)
                } finally {
                    graphics.dispose()
                }
            }
        }
        val videoTimeMillis = frameIndex / videoInfo.frameRate * 1000.0
        val osdFrame = osdFrameAt(osd.frames, videoTimeMillis, speedFactor)
        if (osdFrame != null) {
            osdRenderer.renderInPlace(
                image,
                osdFrame,
                font,
                offsetX = settings.offsetX,
                offsetY = settings.offsetY,
                nearestNeighbor = true,
            )
        }
        val cue = srt?.let { srtCueAt(it.cues, videoTimeMillis)?.data }
        if (cue != null) SrtOverlay.draw(image, cue, srtOptions, antialias = transparent)
        return image
    }

    private fun startChromaEncoder(
        videoInfo: VideoInfo,
        encoder: EncoderRecipe,
        bitrateMbps: Int,
        output: Path,
    ): Process {
        val command = mutableListOf(
            ffmpeg.toString(),
            "-y",
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
        )
        command += encoder.globalArgs
        command += listOf(
            "-f", "rawvideo",
            "-pix_fmt", "bgr24",
            "-s", "${videoInfo.width}x${videoInfo.height}",
            "-r", videoInfo.frameRate.toString(),
            "-i", "pipe:0",
            "-an",
            "-map", "0:v:0",
        )
        command += encoderOutputArgs(encoder, bitrateMbps, includeFileArgs = true)
        command += output.toString()
        return startProcess(command)
    }

    private fun startAlphaEncoder(
        videoInfo: VideoInfo,
        encoderName: String,
        pixelFormat: String,
        extraArgs: List<String>,
        output: Path,
    ): Process {
        val command = mutableListOf(
            ffmpeg.toString(),
            "-y",
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-f", "rawvideo",
            "-pix_fmt", "bgra",
            "-s", "${videoInfo.width}x${videoInfo.height}",
            "-r", videoInfo.frameRate.toString(),
            "-i", "pipe:0",
            "-an",
            "-c:v", encoderName,
            "-pix_fmt", pixelFormat,
        )
        command += extraArgs
        command += output.toString()
        return startProcess(command)
    }

    private fun proresEncoder(): String = when {
        FfmpegEncoders.supports(ffmpeg, "prores_ks") -> "prores_ks"
        FfmpegEncoders.supports(ffmpeg, "prores") -> "prores"
        else -> throw VideoBurnException(uiText(Res.string.osd_export_missing_encoder, "prores_ks"))
    }

    private fun requireEncoder(name: String): String {
        if (!FfmpegEncoders.supports(ffmpeg, name)) {
            throw VideoBurnException(uiText(Res.string.osd_export_missing_encoder, name))
        }
        return name
    }

    private fun writeRaw(encoded: OutputStream, image: BufferedImage, withAlpha: Boolean) {
        if (!withAlpha && image.type == BufferedImage.TYPE_3BYTE_BGR) {
            val data = (image.raster.dataBuffer as DataBufferByte).data
            if (data.size == image.width * image.height * 3) {
                encoded.write(data)
                return
            }
        }
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)
        val bytes = ByteArray(pixels.size * if (withAlpha) 4 else 3)
        var offset = 0
        for (pixel in pixels) {
            bytes[offset++] = (pixel and 0xFF).toByte()
            bytes[offset++] = (pixel shr 8 and 0xFF).toByte()
            bytes[offset++] = (pixel shr 16 and 0xFF).toByte()
            if (withAlpha) bytes[offset++] = (pixel ushr 24 and 0xFF).toByte()
        }
        encoded.write(bytes)
    }

    private fun report(onProgress: (BurnProgress) -> Unit, framesWritten: Int, expectedFrames: Int, encoderName: String) {
        if (framesWritten == 1 || framesWritten == expectedFrames || framesWritten % 5 == 0) {
            onProgress(
                BurnProgress(
                    fraction = framesWritten.toFloat() / expectedFrames,
                    message = uiText(Res.string.osd_export_progress, framesWritten, expectedFrames, encoderName),
                ),
            )
        }
    }

    private fun validate(videoInfo: VideoInfo, osd: Osd, settings: OsdExportSettings) {
        if (videoInfo.width <= 0 || videoInfo.height <= 0) {
            throw VideoBurnException(uiText(Res.string.burn_bad_resolution))
        }
        if (videoInfo.frameRate <= 0.0) {
            throw VideoBurnException(uiText(Res.string.burn_bad_frame_rate))
        }
        if (settings.mode == OsdExportMode.ChromaKey && settings.bitrateMbps <= 0) {
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
        if (isCancelled()) throw CancellationException("OSD export cancelled")
    }

    private fun pngName(index: Int): String = String.format(Locale.US, "osd_%06d.png", index + 1)

    private fun deletePngOutput(output: Path, createdDirectory: Boolean, written: List<Path>) {
        if (createdDirectory) {
            deleteTree(output)
            return
        }
        written.forEach { deleteQuietly(it) }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        try {
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { deleteQuietly(it) }
            }
        } catch (failure: Exception) {
            logger.warn(failure) { "Could not delete $path" }
        }
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

    private fun discardStdout(process: Process) {
        thread(isDaemon = true, name = "ffmpeg-stdout") {
            try {
                process.inputStream.transferTo(OutputStream.nullOutputStream())
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

    private fun deleteQuietly(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (failure: Exception) {
            logger.warn(failure) { "Could not delete $path" }
        }
    }

    private fun encoderFailureMessage(encoderName: String, errors: StringBuilder, extra: String? = null): String {
        val detail = tail(errors).ifBlank { extra ?: uiText(Res.string.burn_no_ffmpeg_detail) }
        return uiText(Res.string.burn_encoder_stopped, encoderName, detail)
    }

    private fun tail(text: StringBuilder): String {
        val value = synchronized(text) { text.toString() }.trim()
        return if (value.length <= 500) value else value.takeLast(500)
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
