package dev.grogdunn.kwalksnailosd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.grogdunn.kwalksnailosd.clip.ClipFolderScanner
import dev.grogdunn.kwalksnailosd.clip.ClipInfo
import dev.grogdunn.kwalksnailosd.clip.ClipList
import dev.grogdunn.kwalksnailosd.clip.MultiClipBurner
import dev.grogdunn.kwalksnailosd.clip.PreparedClip
import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.burn_cancelled
import dev.grogdunn.kwalksnailosd.generated.resources.burn_failed
import dev.grogdunn.kwalksnailosd.generated.resources.burn_multiclip_no_osd
import dev.grogdunn.kwalksnailosd.generated.resources.burn_saved
import dev.grogdunn.kwalksnailosd.generated.resources.burn_saved_software_fallback
import dev.grogdunn.kwalksnailosd.generated.resources.burn_starting
import dev.grogdunn.kwalksnailosd.generated.resources.error_font_unreadable
import dev.grogdunn.kwalksnailosd.generated.resources.error_osd_unreadable
import dev.grogdunn.kwalksnailosd.generated.resources.error_srt_unreadable
import dev.grogdunn.kwalksnailosd.generated.resources.error_video_unreadable
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
import dev.grogdunn.kwalksnailosd.generated.resources.export_encoder_none
import dev.grogdunn.kwalksnailosd.video.BurnEncoderPreference
import dev.grogdunn.kwalksnailosd.video.BurnSettings
import dev.grogdunn.kwalksnailosd.video.FfmpegLocator
import dev.grogdunn.kwalksnailosd.video.HardwareEncoderCatalog
import dev.grogdunn.kwalksnailosd.video.HardwareKind
import dev.grogdunn.kwalksnailosd.video.VideoBurnException
import dev.grogdunn.kwalksnailosd.video.VideoBurner
import dev.grogdunn.kwalksnailosd.video.VideoCodec
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import dev.grogdunn.kwalksnailosd.video.VideoParser
import dev.grogdunn.kwalksnailosd.video.outputNameFor
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
import java.awt.image.BufferedImage
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
    val videoError: String? = null,
    val selectedFrame: Float = 0.0f,
    val bitmap: ImageBitmap? = null,
    val osdOffsetX: Int = 0,
    val osdOffsetY: Int = 0,
    val adjustOsdPlaybackSpeed: Boolean = true,
    val burnCodec: VideoCodec = VideoCodec.H264,
    val burnBitrateMbps: Int = 40,
    val burnEncoder: BurnEncoderPreference = BurnEncoderPreference.Auto,
    val burnHardwareOptions: List<HardwareKind> = emptyList(),
    val burnHardwareProbePending: Boolean = false,
    val burnHardwareNote: String? = null,
    val isBurning: Boolean = false,
    val burnProgress: Float = 0f,
    val burnMessage: String? = null,
    val burnError: String? = null,
    val srt: SrtFile? = null,
    val isParsingSrt: Boolean = false,
    val srtError: String? = null,
    val srtOptions: SrtOptions = SrtOptions(),
    val clipList: ClipList? = null,
    val clipFolderError: String? = null,
)

class AppStateHolder(
    private val osdParser: OsdParser = OsdParser(),
    private val srtParser: SrtParser = SrtParser(),
    private val fontParser: FontParser = FontParser(),
    private val videoParser: VideoParser = VideoParser(),
    private val clipScanner: ClipFolderScanner = ClipFolderScanner(),
    private val probeVideo: (Path) -> VideoInfo = { videoParser.getVideoInfo(it) },
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val osdRenderer = OsdRenderer()
    private var burnJob: Job? = null
    private var burnGeneration = 0
    private var encoderProbeGeneration = 0
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
            clipList = null,
            clipFolderError = null,
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
            videoError = if (mp4Changed) null else state.videoError,
            bitmap = if (mp4Changed) null else state.bitmap,
            selectedFrame = if (mp4Changed) 0.0f else state.selectedFrame
        )
    }

    fun onFolderPicked(path: Path?) {
        if (path == null) return
        try {
            val scanned = clipScanner.scan(path, probeVideo)
            val enriched = scanned.copy(entries = scanned.entries.map { entry ->
                entry.copy(info = parseCompanions(entry.info))
            })
            state = state.copy(
                clipList = enriched,
                clipFolderError = null,
                selectedMp4 = null,
                selectedOsd = null,
                selectedSrt = null,
                osd = null,
                isParsingOsd = false,
                osdError = null,
                videoInfo = null,
                videoError = null,
                bitmap = null,
                selectedFrame = 0.0f,
                srt = null,
                isParsingSrt = false,
                srtError = null,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to scan clip folder $path" }
            state = state.copy(
                clipList = null,
                clipFolderError = exception.message ?: uiText(Res.string.error_video_unreadable),
            )
        }
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
            state = state.copy(font = null, fontError = exception.message ?: uiText(Res.string.error_font_unreadable))
        }
    }

    suspend fun parseSelectedMp4() {
        val mp4 = state.selectedMp4
        try {
            val videoInfo = withContext(Dispatchers.IO) {
                if (mp4 != null) {
                    videoParser.getVideoInfo(mp4)
                } else {
                    videoParser.close()
                    null
                }
            }
            state = state.copy(videoInfo = videoInfo, videoError = null)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to read video $mp4" }
            state = state.copy(videoInfo = null, videoError = exception.message ?: uiText(Res.string.error_video_unreadable))
        }
    }

    suspend fun parseSelectedSrt() {
        val path = state.selectedSrt ?: return
        state = state.copy(srt = null, isParsingSrt = true, srtError = null)
        try {
            val parsed = withContext(Dispatchers.IO) { srtParser.parse(path) }
            val options = srtOptionsFor(parsed, state.srtOptions)
            state = state.copy(srt = parsed, srtOptions = options)
            rerenderCurrentFrame()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            state = state.copy(srt = null, srtError = exception.message ?: uiText(Res.string.error_srt_unreadable))
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
            state = state.copy(osd = null, osdError = exception.message ?: uiText(Res.string.error_osd_unreadable))
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
        val selectedFrameImage = try {
            videoParser.getVideoFrame(mp4, selectedFrame.toInt())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to read frame ${selectedFrame.toInt()} from $mp4" }
            state = state.copy(videoError = exception.message ?: uiText(Res.string.error_video_unreadable))
            return
        } ?: return
        osdRenderer.renderInPlace(selectedFrameImage, osdFrame, font, state.osdOffsetX, state.osdOffsetY)
        drawSrt(selectedFrameImage, videoInfo, selectedFrame)
        state = state.copy(
            selectedFrame = selectedFrame,
            bitmap = selectedFrameImage.toComposeImageBitmap(),
            videoError = null,
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

    fun setBurnEncoder(preference: BurnEncoderPreference) {
        state = state.copy(burnEncoder = preference)
    }

    fun refreshBurnEncoders() {
        val codec = state.burnCodec
        val generation = ++encoderProbeGeneration
        state = state.copy(burnHardwareProbePending = true, burnHardwareOptions = emptyList(), burnHardwareNote = null)
        scope.launch {
            val probed = withContext(Dispatchers.IO) {
                runCatching {
                    val ffmpeg = FfmpegLocator().locateFor(codec)
                    HardwareEncoderCatalog(ffmpeg).working(codec).mapNotNull { it.kind }
                }
            }
            SwingUtilities.invokeLater {
                if (generation != encoderProbeGeneration || state.burnCodec != codec) return@invokeLater
                val options = probed.getOrElse { emptyList() }
                val kept = when (val preference = state.burnEncoder) {
                    is BurnEncoderPreference.Hardware ->
                        if (preference.kind in options) preference else BurnEncoderPreference.Auto
                    else -> preference
                }
                val note = when {
                    probed.isFailure -> probed.exceptionOrNull()?.message
                    options.isEmpty() -> uiText(Res.string.export_encoder_none)
                    else -> null
                }
                state = state.copy(
                    burnEncoder = kept,
                    burnHardwareOptions = options,
                    burnHardwareProbePending = false,
                    burnHardwareNote = note,
                )
            }
        }
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

    fun moveClipUp(id: String) = updateClipList { it.movedUp(id) }

    fun moveClipDown(id: String) = updateClipList { it.movedDown(id) }

    fun setClipExcluded(id: String, excluded: Boolean) = updateClipList { it.withExcluded(id, excluded) }

    fun restoreClipOrder() = updateClipList { it.restoredAz() }

    fun canStartBurn(): Boolean {
        if (state.isBurning) return false
        val list = state.clipList
        if (list != null) {
            return list.canBurn() && state.font != null && list.included().all { it.info.osd != null }
        }
        return state.osd != null && state.videoInfo != null && state.font != null && state.selectedMp4 != null
    }

    fun suggestedOutputName(): String {
        val list = state.clipList
        if (list != null) return "${list.outputStem() ?: "joined"}.mp4"
        val mp4 = state.selectedMp4 ?: return ""
        return outputNameFor(mp4)
    }

    private fun updateClipList(transform: (ClipList) -> ClipList) {
        if (state.isBurning) return
        val list = state.clipList ?: return
        state = state.copy(clipList = transform(list))
    }

    fun startBurn(output: Path) {
        if (state.clipList != null) {
            startFolderBurn(output)
            return
        }
        if (state.isBurning) return
        val mp4 = state.selectedMp4 ?: return
        val videoInfo = state.videoInfo ?: return
        val osd = state.osd ?: return
        val font = state.font ?: return
        val settings = BurnSettings(
            codec = state.burnCodec,
            bitrateMbps = state.burnBitrateMbps,
            encoder = state.burnEncoder,
            offsetX = state.osdOffsetX,
            offsetY = state.osdOffsetY,
            adjustPlaybackSpeed = state.adjustOsdPlaybackSpeed,
        )
        val srt = state.srt
        val srtOptions = state.srtOptions
        val generation = ++burnGeneration
        state = state.copy(isBurning = true, burnProgress = 0f, burnMessage = uiText(Res.string.burn_starting), burnError = null)
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
                    val saved = if (result.usedSoftwareFallback) {
                        uiText(Res.string.burn_saved_software_fallback, result.output.fileName, result.encoderName)
                    } else {
                        uiText(Res.string.burn_saved, result.output.fileName, result.encoderName)
                    }
                    state = state.copy(
                        isBurning = false,
                        burnProgress = 1f,
                        burnMessage = saved,
                        burnError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                SwingUtilities.invokeLater {
                    if (generation != burnGeneration) return@invokeLater
                    state = state.copy(isBurning = false, burnMessage = uiText(Res.string.burn_cancelled), burnError = null)
                }
            } catch (failure: Exception) {
                logger.error(failure) { "Burn failed" }
                SwingUtilities.invokeLater {
                    if (generation != burnGeneration) return@invokeLater
                    state = state.copy(
                        isBurning = false,
                        burnError = failure.message ?: uiText(Res.string.burn_failed),
                        burnMessage = null,
                    )
                }
            }
        }
    }

    private fun startFolderBurn(output: Path) {
        if (state.isBurning) return
        val list = state.clipList ?: return
        val font = state.font ?: return
        val settings = BurnSettings(
            codec = state.burnCodec,
            bitrateMbps = state.burnBitrateMbps,
            encoder = state.burnEncoder,
            offsetX = state.osdOffsetX,
            offsetY = state.osdOffsetY,
            adjustPlaybackSpeed = state.adjustOsdPlaybackSpeed,
        )
        val generation = ++burnGeneration
        state = state.copy(isBurning = true, burnProgress = 0f, burnMessage = uiText(Res.string.burn_starting), burnError = null)
        burnJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val burnScope = this
                    val prepared = list.included().map { entry ->
                        val info = entry.info
                        val videoInfo = info.videoInfo
                            ?: throw VideoBurnException(info.probeError ?: uiText(Res.string.error_video_unreadable))
                        val osd = info.osd
                            ?: throw VideoBurnException(info.osdError ?: uiText(Res.string.burn_multiclip_no_osd))
                        PreparedClip(
                            video = info.files.video,
                            videoInfo = videoInfo,
                            osd = osd,
                            srt = info.srt,
                        )
                    }
                    MultiClipBurner(FfmpegLocator().locateFor(settings.codec)).burn(
                        clips = prepared,
                        font = font,
                        settings = settings,
                        output = output,
                        onProgress = { progress -> publishBurn(generation, progress.fraction, progress.message) },
                        onPreviewFrame = { frame -> publishPreview(generation, frame) },
                        isCancelled = { !burnScope.isActive },
                        srtOptions = state.srtOptions,
                    )
                }
                SwingUtilities.invokeLater {
                    if (generation != burnGeneration) return@invokeLater
                    val saved = if (result.usedSoftwareFallback) {
                        uiText(Res.string.burn_saved_software_fallback, result.output.fileName, result.encoderName)
                    } else {
                        uiText(Res.string.burn_saved, result.output.fileName, result.encoderName)
                    }
                    state = state.copy(
                        isBurning = false,
                        burnProgress = 1f,
                        burnMessage = saved,
                        burnError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                SwingUtilities.invokeLater {
                    if (generation != burnGeneration) return@invokeLater
                    state = state.copy(isBurning = false, burnMessage = uiText(Res.string.burn_cancelled), burnError = null)
                }
            } catch (failure: Exception) {
                logger.error(failure) { "Burn failed" }
                SwingUtilities.invokeLater {
                    if (generation != burnGeneration) return@invokeLater
                    state = state.copy(
                        isBurning = false,
                        burnError = failure.message ?: uiText(Res.string.burn_failed),
                        burnMessage = null,
                    )
                }
            }
        }
    }

    fun cancelBurn() {
        burnGeneration += 1
        burnJob?.cancel()
        state = state.copy(isBurning = false, burnMessage = uiText(Res.string.burn_cancelled), burnError = null)
    }

    private fun videoBurner(codec: VideoCodec): VideoBurner =
        videoBurners.getOrPut(codec) { VideoBurner(FfmpegLocator().locateFor(codec)) }

    private fun publishBurn(generation: Int, fraction: Float, message: String) {
        SwingUtilities.invokeLater {
            if (generation != burnGeneration || !state.isBurning) return@invokeLater
            state = state.copy(burnProgress = fraction, burnMessage = message)
        }
    }

    private fun publishPreview(generation: Int, image: BufferedImage) {
        SwingUtilities.invokeLater {
            if (generation != burnGeneration || !state.isBurning) return@invokeLater
            state = state.copy(bitmap = image.toComposeImageBitmap())
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

    private fun srtOptionsFor(parsed: SrtFile, current: SrtOptions): SrtOptions {
        val lacksLegacyOverlay = parsed.cues.none { cue ->
            val data = cue.data ?: return@none false
            data.signal != null || data.bitrateMbps != null || data.distanceMeters != null
        }
        return if (parsed.hasDebugFields && lacksLegacyOverlay) {
            current.copy(
                showMcs = true,
                showSnr = true,
                showSignal = false,
                showBitrate = false,
                showDistance = false,
            )
        } else {
            current.copy(showDistance = current.showDistance && parsed.hasDistance)
        }
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

    private fun parseCompanions(info: ClipInfo): ClipInfo {
        val withOsd = info.files.osd?.let { path ->
            try {
                info.copy(osd = osdParser.parse(path), osdError = null)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                info.copy(osd = null, osdError = exception.message ?: uiText(Res.string.error_osd_unreadable))
            }
        } ?: info
        val srtPath = withOsd.files.srt ?: return withOsd
        return try {
            withOsd.copy(srt = srtParser.parse(srtPath), srtError = null)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            withOsd.copy(srt = null, srtError = exception.message ?: uiText(Res.string.error_srt_unreadable))
        }
    }

    override fun close() {
        burnGeneration += 1
        burnJob?.cancel()
        scope.cancel()
        videoParser.close()
    }
}
