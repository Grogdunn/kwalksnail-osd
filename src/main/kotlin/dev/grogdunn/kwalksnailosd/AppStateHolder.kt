package dev.grogdunn.kwalksnailosd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.grogdunn.kwalksnailosd.osd.Osd
import dev.grogdunn.kwalksnailosd.osd.OsdParser
import dev.grogdunn.kwalksnailosd.osd.OsdRenderer
import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.osd.font.FontParser
import dev.grogdunn.kwalksnailosd.osd.osdFrameAt
import dev.grogdunn.kwalksnailosd.osd.osdHorizontalOffset
import dev.grogdunn.kwalksnailosd.osd.osdPlaybackSpeedFactor
import dev.grogdunn.kwalksnailosd.osd.osdVerticalOffset
import dev.grogdunn.kwalksnailosd.srt.SrtFile
import dev.grogdunn.kwalksnailosd.srt.SrtOptions
import dev.grogdunn.kwalksnailosd.srt.SrtOverlay
import dev.grogdunn.kwalksnailosd.srt.SrtParser
import dev.grogdunn.kwalksnailosd.srt.srtCueAt
import dev.grogdunn.kwalksnailosd.video.BurnSettings
import dev.grogdunn.kwalksnailosd.video.FfmpegLocator
import dev.grogdunn.kwalksnailosd.video.VideoBurner
import dev.grogdunn.kwalksnailosd.video.VideoCodec
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import dev.grogdunn.kwalksnailosd.video.VideoParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.io.path.extension
import kotlin.math.roundToInt

data class AppUiState(
    val selectedMp4: Path? = null,
    val selectedSrt: Path? = null,

    val selectedOsd: Path? = null,
    val osd: Osd? = null,
    val isParsingOsd: Boolean = false,
    val osdError: String? = null,

    val selectedFont: Path? = null,
    val font: Font? = null,
    val fontError: String? = null,
    val videoInfo: VideoInfo? = null,
    val selectedFrame: Float = 0.0f,
    val bitmap: ImageBitmap? = null,
    val osdOffsetX: Int = 0,
    val osdOffsetY: Int = 0,
    val adjustOsdPlaybackSpeed: Boolean = true,
    val burnCodec: VideoCodec = VideoCodec.H264,
    val burnBitrateMbps: Int = 40,
    val preferHardwareEncoder: Boolean = true,
    val isBurning: Boolean = false,
    val burnProgress: Float = 0f,
    val burnMessage: String? = null,
    val burnError: String? = null,
    val srt: SrtFile? = null,
    val isParsingSrt: Boolean = false,
    val srtError: String? = null,
    val srtOptions: SrtOptions = SrtOptions(),
)

class AppStateHolder(
    private val osdParser: OsdParser = OsdParser(),
    private val srtParser: SrtParser = SrtParser(),
    private val fontParser: FontParser = FontParser(),
    private val videoParser: VideoParser = VideoParser()
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val osdRenderer = OsdRenderer()
    private var burnJob: Job? = null
    private var burnGeneration = 0
    private val videoBurners = mutableMapOf<VideoCodec, VideoBurner>()

    var state by mutableStateOf(AppUiState())
        private set

    fun onFilePickerClosed(path: Path?) {
        if (path == null) return

        val relatedFiles = lookupFiles(path)
        val selectedOsd = if (path.extension.equals("osd", ignoreCase = true)) path else state.selectedOsd ?: relatedFiles.osdPath
        val osdChanged = selectedOsd != state.selectedOsd
        val selectedMp4 = if (path.extension.equals("mp4", ignoreCase = true)) path else state.selectedMp4 ?: relatedFiles.mp4Path
        val mp4Changed = selectedMp4 != state.selectedMp4
        val selectedSrt = if (path.extension.equals("srt", ignoreCase = true)) path else state.selectedSrt ?: relatedFiles.srtPath
        val srtChanged = selectedSrt != state.selectedSrt
        state = state.copy(
            selectedMp4 = selectedMp4,
            selectedOsd = selectedOsd,
            selectedSrt = selectedSrt,
            osd = if (osdChanged) null else state.osd,
            isParsingOsd = !osdChanged && state.isParsingOsd,
            osdError = if (osdChanged) null else state.osdError,
            srt = if (srtChanged) null else state.srt,
            isParsingSrt = !srtChanged && state.isParsingSrt,
            srtError = if (srtChanged) null else state.srtError,
            videoInfo = if (mp4Changed) null else state.videoInfo,
            bitmap = if (mp4Changed) null else state.bitmap,
            selectedFrame = if (mp4Changed) 0.0f else state.selectedFrame
        )
    }

    fun onFontFilePickerClosed(path: Path?) {
        if (path == null) return

        state = state.copy(selectedFont = path, font = null, fontError = null)
    }

    suspend fun parseSelectedFont() {
        val path = state.selectedFont
        if (path == null) {
            state = state.copy(font = null, fontError = null)
            return
        }
        state = state.copy(font = null, fontError = null)
        try {
            val font = withContext(Dispatchers.IO) { fontParser.parse(path) }
            state = state.copy(font = font, fontError = null)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to read OSD font $path" }
            state = state.copy(font = null, fontError = exception.message ?: "Impossibile leggere il font OSD")
        }
    }

    suspend fun parseSelectedMp4() {
        val mp4 = state.selectedMp4
        val videoInfo = withContext(Dispatchers.IO) {
            if (mp4 != null) {
                videoParser.getVideoInfo(mp4)
            } else {
                videoParser.close()
                null
            }
        }
        state = state.copy(videoInfo = videoInfo)
    }

    suspend fun parseSelectedSrt() {
        val path = state.selectedSrt ?: return
        state = state.copy(srt = null, isParsingSrt = true, srtError = null)
        try {
            val parsed = withContext(Dispatchers.IO) { srtParser.parse(path) }
            val options = state.srtOptions.copy(showDistance = state.srtOptions.showDistance && parsed.hasDistance)
            state = state.copy(srt = parsed, srtOptions = options)
            rerenderCurrentFrame()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            state = state.copy(srt = null, srtError = exception.message ?: "Impossibile leggere il file SRT")
        } finally {
            state = state.copy(isParsingSrt = false)
        }
    }

    suspend fun parseSelectedOsd() {
        if (state.selectedOsd == null) return

        state = state.copy(osd = null, isParsingOsd = true, osdError = null)
        try {
            val parsedOsd = withContext(Dispatchers.IO) { osdParser.parse(state.selectedOsd!!) }
            state = state.copy(osd = parsedOsd)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            state = state.copy(osd = null, osdError = exception.message ?: "Unable to parse OSD file")
        } finally {
            state = state.copy(isParsingOsd = false)
        }
    }

    fun onFrameSelected(selectedFrame: Float) {
        val mp4 = state.selectedMp4 ?: return
        val videoInfo = state.videoInfo ?: return
        val osd = state.osd ?: return
        val font = state.font ?: return
        val osdFrame = currentOsdFrame(videoInfo, osd, selectedFrame) ?: return
        logger.info { "onFrameSelected: $selectedFrame" }
        val selectedFrameImage = videoParser.getVideoFrame(mp4, selectedFrame.toInt()) ?: return
        osdRenderer.renderInPlace(selectedFrameImage, osdFrame, font, state.osdOffsetX, state.osdOffsetY)
        drawSrt(selectedFrameImage, videoInfo, selectedFrame)
        state = state.copy(
            selectedFrame = selectedFrame,
            bitmap = selectedFrameImage.toComposeImageBitmap()
        )
    }

    fun setOsdOffset(x: Int, y: Int) {
        if (x == state.osdOffsetX && y == state.osdOffsetY) return
        state = state.copy(osdOffsetX = x, osdOffsetY = y)
        rerenderCurrentFrame()
    }

    fun centerOsd() {
        val video = state.videoInfo ?: return
        val osd = state.osd ?: return
        val frame = currentOsdFrame(video, osd) ?: return
        val tile = osdRenderer.characterSizeForFrameHeight(video.height)
        setOsdOffset(
            osdHorizontalOffset(video.width, frame, tile.width),
            osdVerticalOffset(video.height, frame, tile.height),
        )
    }

    fun resetOsdOffset() {
        setOsdOffset(0, 0)
    }

    fun setAdjustOsdPlaybackSpeed(enabled: Boolean) {
        state = state.copy(adjustOsdPlaybackSpeed = enabled)
        rerenderCurrentFrame()
    }

    fun setBurnCodec(codec: VideoCodec) {
        state = state.copy(burnCodec = codec)
    }

    fun setBurnBitrateMbps(bitrate: Int) {
        state = state.copy(burnBitrateMbps = bitrate.coerceIn(1, 80))
    }

    fun setPreferHardwareEncoder(enabled: Boolean) {
        state = state.copy(preferHardwareEncoder = enabled)
    }

    fun setSrtOptions(options: SrtOptions) {
        val normalized = options.copy(
            xPercent = ((options.xPercent * 10f).roundToInt() / 10f).coerceIn(0f, 100f),
            yPercent = ((options.yPercent * 10f).roundToInt() / 10f).coerceIn(0f, 100f),
            scale = options.scale.roundToInt().toFloat().coerceIn(10f, 60f),
        )
        if (normalized == state.srtOptions) return
        state = state.copy(srtOptions = normalized)
        rerenderCurrentFrame()
    }

    fun resetSrtPlacement() {
        setSrtOptions(state.srtOptions.copy(xPercent = 1.5f, yPercent = 95f, scale = 35f))
    }

    fun startBurn(output: Path) {
        if (state.isBurning) return
        val mp4 = state.selectedMp4 ?: return
        val videoInfo = state.videoInfo ?: return
        val osd = state.osd ?: return
        val font = state.font ?: return
        val settings = BurnSettings(
            codec = state.burnCodec,
            bitrateMbps = state.burnBitrateMbps,
            preferHardware = state.preferHardwareEncoder,
            offsetX = state.osdOffsetX,
            offsetY = state.osdOffsetY,
            adjustPlaybackSpeed = state.adjustOsdPlaybackSpeed,
        )
        val srt = state.srt
        val srtOptions = state.srtOptions
        val generation = ++burnGeneration
        state = state.copy(isBurning = true, burnProgress = 0f, burnMessage = "Avvio dell'export…", burnError = null)
        burnJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val burnScope = this
                    videoBurner(settings.codec).burn(
                        input = mp4,
                        output = output,
                        videoInfo = videoInfo,
                        osd = osd,
                        font = font,
                        settings = settings,
                        onProgress = { progress -> publishBurn(generation, progress.fraction, progress.message) },
                        isCancelled = { !burnScope.isActive },
                        srt = srt,
                        srtOptions = srtOptions,
                    )
                }
                SwingUtilities.invokeLater {
                    if (generation != burnGeneration) return@invokeLater
                    val fallback = if (result.usedSoftwareFallback) " (fallback software)" else ""
                    state = state.copy(
                        isBurning = false,
                        burnProgress = 1f,
                        burnMessage = "Salvato ${result.output.fileName} con ${result.encoderName}$fallback",
                        burnError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                SwingUtilities.invokeLater {
                    if (generation != burnGeneration) return@invokeLater
                    state = state.copy(isBurning = false, burnMessage = "Export annullato", burnError = null)
                }
            } catch (failure: Exception) {
                logger.error(failure) { "Burn failed" }
                SwingUtilities.invokeLater {
                    if (generation != burnGeneration) return@invokeLater
                    state = state.copy(
                        isBurning = false,
                        burnError = failure.message ?: "Export non riuscito",
                        burnMessage = null,
                    )
                }
            }
        }
    }

    fun cancelBurn() {
        burnGeneration += 1
        burnJob?.cancel()
        state = state.copy(isBurning = false, burnMessage = "Export annullato", burnError = null)
    }

    private fun videoBurner(codec: VideoCodec): VideoBurner =
        videoBurners.getOrPut(codec) { VideoBurner(FfmpegLocator().locateFor(codec)) }

    private fun publishBurn(generation: Int, fraction: Float, message: String) {
        SwingUtilities.invokeLater {
            if (generation != burnGeneration || !state.isBurning) return@invokeLater
            state = state.copy(burnProgress = fraction, burnMessage = message)
        }
    }

    private fun currentOsdFrame(videoInfo: VideoInfo, osd: Osd, selectedFrame: Float = state.selectedFrame) =
        if (videoInfo.frameRate <= 0.0) {
            null
        } else {
            osdFrameAt(
                osd.frames,
                selectedFrame / videoInfo.frameRate * 1000.0,
                osdPlaybackSpeedFactor(videoInfo.duration, osd.duration, state.adjustOsdPlaybackSpeed),
            )
        }

    private fun drawSrt(image: java.awt.image.BufferedImage, videoInfo: VideoInfo, selectedFrame: Float) {
        val srt = state.srt ?: return
        if (videoInfo.frameRate <= 0.0) return
        val cue = srtCueAt(srt.cues, selectedFrame / videoInfo.frameRate * 1000.0) ?: return
        val data = cue.data ?: return
        SrtOverlay.draw(image, data, state.srtOptions)
    }

    private fun rerenderCurrentFrame() {
        if (state.selectedMp4 == null || state.videoInfo == null || state.osd == null || state.font == null) return
        onFrameSelected(state.selectedFrame)
    }

    override fun close() {
        burnGeneration += 1
        burnJob?.cancel()
        scope.cancel()
        videoParser.close()
    }
}
