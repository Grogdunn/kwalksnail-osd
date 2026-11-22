package dev.grogdunn.kwalksnailosd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.grogdunn.kwalksnailosd.clip.BatchBurner
import dev.grogdunn.kwalksnailosd.clip.BatchJob
import dev.grogdunn.kwalksnailosd.clip.BatchJobState
import dev.grogdunn.kwalksnailosd.clip.BatchSummary
import dev.grogdunn.kwalksnailosd.clip.ClipEntry
import dev.grogdunn.kwalksnailosd.clip.batchOutputPaths
import dev.grogdunn.kwalksnailosd.clip.ClipFolderScanner
import dev.grogdunn.kwalksnailosd.clip.ClipInfo
import dev.grogdunn.kwalksnailosd.clip.ClipList
import dev.grogdunn.kwalksnailosd.clip.MultiClipBurner
import dev.grogdunn.kwalksnailosd.clip.PreparedClip
import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.batch_progress
import dev.grogdunn.kwalksnailosd.generated.resources.batch_some_failed
import dev.grogdunn.kwalksnailosd.generated.resources.batch_summary
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
import dev.grogdunn.kwalksnailosd.generated.resources.osd_export_png_no_space
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
import dev.grogdunn.kwalksnailosd.video.DiskSpaceCheck
import dev.grogdunn.kwalksnailosd.video.OsdExportMode
import dev.grogdunn.kwalksnailosd.video.OsdExportSettings
import dev.grogdunn.kwalksnailosd.video.OsdExporter
import dev.grogdunn.kwalksnailosd.video.OsdOffset
import dev.grogdunn.kwalksnailosd.video.VideoCodec
import dev.grogdunn.kwalksnailosd.video.checkPngDiskSpace
import dev.grogdunn.kwalksnailosd.video.estimatePngSequenceBytes
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import dev.grogdunn.kwalksnailosd.video.VideoParser
import dev.grogdunn.kwalksnailosd.video.VideoSize
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
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
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
    val previewClipId: String? = null,
    val clipFolderError: String? = null,
    val osdExportMode: OsdExportMode? = null,
    val osdChromaColor: Int = 0xFFFF00FF.toInt(),
    val batchMode: Boolean = false,
    val batchJobs: Map<String, BatchJobState> = emptyMap(),
    val adaptResolution: Boolean = true,
)

class AppStateHolder(
    private val osdParser: OsdParser = OsdParser(),
    private val srtParser: SrtParser = SrtParser(),
    private val fontParser: FontParser = FontParser(),
    private val videoParser: VideoParser = VideoParser(),
    private val clipScanner: ClipFolderScanner = ClipFolderScanner(),
    private val probeVideo: (Path) -> VideoInfo = { videoParser.getVideoInfo(it) },
    private val pngRequiredBytes: (VideoInfo) -> Long = ::defaultPngRequiredBytes,
    private val pngDiskSpace: (Path, Long) -> DiskSpaceCheck = ::checkPngDiskSpace,
    private val batchBurnerFactory: (VideoCodec) -> BatchBurner = { codec ->
        BatchBurner.forFfmpeg(FfmpegLocator().locateFor(codec))
    },
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
            previewClipId = null,
            clipFolderError = null,
            batchMode = false,
            batchJobs = emptyMap(),
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
            applyScannedClipList(clipScanner.scan(path, probeVideo), batchMode = false)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to scan clip folder $path" }
            state = state.copy(
                clipList = null,
                previewClipId = null,
                clipFolderError = exception.message ?: uiText(Res.string.error_video_unreadable),
                batchMode = false,
                batchJobs = emptyMap(),
            )
        }
    }

    fun onFilesPicked(paths: List<Path>) {
        if (paths.isEmpty()) return
        try {
            applyScannedClipList(clipScanner.scanFiles(paths, probeVideo), batchMode = true)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to scan selected clips" }
            state = state.copy(
                clipList = null,
                previewClipId = null,
                clipFolderError = exception.message ?: uiText(Res.string.error_video_unreadable),
                batchMode = false,
                batchJobs = emptyMap(),
            )
        }
    }

    private fun applyScannedClipList(scanned: ClipList, batchMode: Boolean) {
        val enriched = scanned.copy(entries = scanned.entries.map { entry ->
            entry.copy(info = parseCompanions(entry.info))
        })
        val representative = representativeSrt(enriched)
        state = state.copy(
            clipList = enriched,
            previewClipId = null,
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
            srt = representative,
            srtOptions = if (representative != null) srtOptionsFor(representative, state.srtOptions) else state.srtOptions,
            isParsingSrt = false,
            srtError = null,
            batchMode = batchMode,
            batchJobs = emptyMap(),
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
        val mp4 = previewVideoPath() ?: return
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
        val offset = previewOffset()
        osdRenderer.renderInPlace(selectedFrameImage, osdFrame, font, offset.x, offset.y)
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

    fun setOsdExportMode(mode: OsdExportMode?) {
        if (state.isBurning) return
        state = state.copy(osdExportMode = mode)
    }

    fun setOsdChromaColor(argb: Int) {
        if (state.isBurning) return
        state = state.copy(osdChromaColor = argb)
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

    fun canStartOsdExport(): Boolean {
        if (state.isBurning || state.osdExportMode == null || state.font == null) return false
        if (state.clipList != null && state.batchMode) return false
        return osdExportSource() != null
    }

    fun estimatedPngSequenceBytes(): Long? {
        val info = osdExportVideoInfo() ?: return null
        return pngRequiredBytes(info)
    }

    fun pngExportSpace(output: Path): DiskSpaceCheck? {
        val required = estimatedPngSequenceBytes() ?: return null
        return pngDiskSpace(output, required)
    }

    fun osdExportVideoInfo(): VideoInfo? = osdExportSource()?.videoInfo

    fun osdExportDefaultExtension(): String? = when (state.osdExportMode) {
        OsdExportMode.ChromaKey -> "mp4"
        OsdExportMode.ProRes4444 -> "mov"
        OsdExportMode.Vp9Alpha -> "webm"
        else -> null
    }

    fun setBatchMode(enabled: Boolean) {
        if (state.isBurning || state.clipList == null) return
        state = state.copy(batchMode = enabled, batchJobs = emptyMap(), burnMessage = null, burnError = null)
    }

    fun setAdaptResolution(enabled: Boolean) {
        if (state.isBurning) return
        state = state.copy(adaptResolution = enabled)
    }

    internal fun joinTargetSize(): VideoSize? {
        val list = state.clipList ?: return null
        return if (state.adaptResolution && list.mismatch().resolution) list.targetSize() else null
    }

    fun canStartBatch(): Boolean {
        if (state.isBurning || state.clipList == null || state.font == null) return false
        return state.clipList!!.included().any { it.info.videoInfo != null && it.info.osd != null }
    }

    fun canStartBurn(): Boolean {
        if (state.isBurning) return false
        val list = state.clipList
        if (list != null && state.batchMode) return canStartBatch()
        if (list != null) {
            return list.canBurn(adaptResolution = state.adaptResolution) &&
                state.font != null &&
                list.included().all { it.info.osd != null }
        }
        return state.osd != null && state.videoInfo != null && state.font != null && state.selectedMp4 != null
    }

    fun suggestedOutputName(): String {
        val list = state.clipList
        if (list != null) return "${list.outputStem() ?: "joined"}.mp4"
        val mp4 = state.selectedMp4 ?: return ""
        return outputNameFor(mp4)
    }

    fun suggestedBatchOutputDir(): Path? {
        val folder = state.clipList?.folder ?: return null
        return folder.takeIf { it.toString().isNotEmpty() }
    }

    fun suggestedOsdExportName(): String {
        val stem = osdExportStem() ?: return ""
        return when (state.osdExportMode) {
            OsdExportMode.ChromaKey -> "${stem}_osd_chroma.mp4"
            OsdExportMode.ProRes4444 -> "${stem}_osd.mov"
            OsdExportMode.Vp9Alpha -> "${stem}_osd.webm"
            OsdExportMode.PngSequence -> "${stem}_osd_png"
            null -> ""
        }
    }

    private fun osdExportStem(): String? {
        val list = state.clipList
        if (list != null) return list.included().firstOrNull()?.info?.files?.baseName
        return state.selectedMp4?.nameWithoutExtension
    }

    private fun osdExportSource(): OsdExportSource? {
        val list = state.clipList
        if (list != null) {
            val info = list.included().firstOrNull()?.info ?: return null
            val videoInfo = info.videoInfo ?: return null
            val osd = info.osd ?: return null
            return OsdExportSource(videoInfo, osd, info.srt)
        }
        val videoInfo = state.videoInfo ?: return null
        val osd = state.osd ?: return null
        return OsdExportSource(videoInfo, osd, state.srt)
    }

    private fun ffmpegForOsdExport(settings: OsdExportSettings): Path {
        if (settings.mode == OsdExportMode.PngSequence) return Path.of("png")
        val codec = if (settings.mode == OsdExportMode.ChromaKey) settings.codec else VideoCodec.H264
        return FfmpegLocator().locateFor(codec)
    }

    private fun updateClipList(transform: (ClipList) -> ClipList) {
        if (state.isBurning) return
        val list = state.clipList ?: return
        state = state.copy(clipList = transform(list))
    }

    fun setClipOffset(id: String, offset: OsdOffset?) {
        if (state.isBurning || state.clipList == null) return
        updateClipList { it.withOffset(id, offset) }
        if (id == state.previewClipId) rerenderCurrentFrame()
    }

    fun enableClipOffset(id: String, enabled: Boolean) {
        if (state.isBurning) return
        val entry = state.clipList?.entries?.find { it.id == id } ?: return
        val offset = if (enabled) entry.offset ?: OsdOffset(state.osdOffsetX, state.osdOffsetY) else null
        if (entry.offset == offset) return
        updateClipList { it.withOffset(id, offset) }
        if (id == state.previewClipId) rerenderCurrentFrame()
    }

    fun previewClip(id: String) {
        if (state.isBurning) return
        val entry = state.clipList?.entries?.find { it.id == id } ?: return
        val videoInfo = entry.info.videoInfo ?: return
        val osd = entry.info.osd ?: return
        state = state.copy(
            previewClipId = id,
            videoInfo = videoInfo,
            osd = osd,
            selectedFrame = 0f,
            videoError = null,
        )
        onFrameSelected(0f)
    }

    fun canEditBurnOptions(): Boolean {
        if (state.isBurning || state.font == null) return false
        val list = state.clipList
        return if (list != null) {
            list.included().any { it.info.videoInfo != null && it.info.osd != null }
        } else {
            state.videoInfo != null && state.osd != null
        }
    }

    fun startBurn(output: Path) {
        if (state.clipList != null && state.batchMode) {
            startBatchBurn(output)
            return
        }
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

    fun startBatchBurn(outputDir: Path) {
        if (!canStartBatch()) return
        val list = state.clipList ?: return
        val font = state.font ?: return
        val included = list.included()
        val outputs = batchOutputPaths(included.map { it.info.files.video }, outputDir)
        val jobs = included.mapIndexed { index, entry -> batchJob(entry, outputs[index]) }
        val settings = BurnSettings(
            codec = state.burnCodec,
            bitrateMbps = state.burnBitrateMbps,
            encoder = state.burnEncoder,
            offsetX = state.osdOffsetX,
            offsetY = state.osdOffsetY,
            adjustPlaybackSpeed = state.adjustOsdPlaybackSpeed,
        )
        val srtOptions = state.srtOptions
        val generation = ++burnGeneration
        state = state.copy(
            isBurning = true,
            burnProgress = 0f,
            burnMessage = uiText(Res.string.burn_starting),
            burnError = null,
            batchJobs = emptyMap(),
        )
        val burner = batchBurnerFactory(settings.codec)
        burnJob = scope.launch {
            executeBatchBurn(burner, jobs, font, settings, srtOptions, generation)
        }
    }

    private fun batchJob(entry: ClipEntry, output: Path): BatchJob {
        val info = entry.info
        val missing = info.videoInfo == null || info.osd == null
        return BatchJob(
            id = entry.id,
            video = info.files.video,
            output = output,
            videoInfo = info.videoInfo,
            osd = info.osd,
            srt = info.srt,
            preflightError = if (missing) info.probeError ?: info.osdError else null,
            offset = entry.offset,
        )
    }

    private suspend fun executeBatchBurn(
        burner: BatchBurner,
        jobs: List<BatchJob>,
        font: Font,
        settings: BurnSettings,
        srtOptions: SrtOptions,
        generation: Int,
    ) {
        var summary: BatchSummary? = null
        try {
            withContext(Dispatchers.IO) {
                val burnScope = this
                val result = burner.run(
                    jobs = jobs,
                    font = font,
                    settings = settings,
                    srtOptions = srtOptions,
                    onJobState = { id, jobState -> publishBatchJob(generation, jobs, id, jobState) },
                    onPreviewFrame = { frame -> publishPreview(generation, frame) },
                    isCancelled = { !burnScope.isActive },
                )
                summary = result
                result
            }
            publishBatchSummary(generation, summary!!)
        } catch (_: CancellationException) {
            publishBatchSummary(
                generation,
                summary ?: BatchSummary(jobs.associate { it.id to BatchJobState.Cancelled }),
            )
        } catch (failure: Exception) {
            logger.error(failure) { "Batch burn failed" }
            SwingUtilities.invokeLater {
                if (!canApplyBatchResult(generation)) return@invokeLater
                state = state.copy(
                    isBurning = false,
                    burnError = failure.message ?: uiText(Res.string.burn_failed),
                    burnMessage = null,
                )
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
        val targetSize = joinTargetSize()
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
                            offset = entry.offset,
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
                        targetSize = targetSize,
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

    fun startOsdExport(output: Path) {
        if (state.isBurning) return
        val mode = state.osdExportMode ?: return
        val source = osdExportSource() ?: return
        val font = state.font ?: return
        if (mode == OsdExportMode.PngSequence) {
            val space = pngDiskSpace(output, pngRequiredBytes(source.videoInfo))
            if (!space.enough) {
                state = state.copy(
                    isBurning = false,
                    burnProgress = 0f,
                    burnMessage = null,
                    burnError = uiText(
                        Res.string.osd_export_png_no_space,
                        space.requiredBytes.toString(),
                        space.availableBytes.toString(),
                    ),
                )
                return
            }
        }
        val settings = OsdExportSettings(
            mode = mode,
            chromaColor = Color(state.osdChromaColor, true),
            offsetX = state.osdOffsetX,
            offsetY = state.osdOffsetY,
            adjustPlaybackSpeed = state.adjustOsdPlaybackSpeed,
            codec = state.burnCodec,
            bitrateMbps = state.burnBitrateMbps,
            encoder = state.burnEncoder,
        )
        val srt = source.srt
        val srtOptions = state.srtOptions
        val videoInfo = source.videoInfo
        val osd = source.osd
        val generation = ++burnGeneration
        state = state.copy(isBurning = true, burnProgress = 0f, burnMessage = uiText(Res.string.burn_starting), burnError = null)
        burnJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val exportScope = this
                    OsdExporter(ffmpegForOsdExport(settings)).export(
                        videoInfo = videoInfo,
                        osd = osd,
                        font = font,
                        settings = settings,
                        output = output,
                        onProgress = { progress -> publishBurn(generation, progress.fraction, progress.message) },
                        onPreviewFrame = { frame -> publishPreview(generation, frame) },
                        isCancelled = { !exportScope.isActive },
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
                logger.error(failure) { "OSD export failed" }
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

    private fun canApplyBatchResult(generation: Int): Boolean {
        if (burnGeneration == generation) return true
        return burnGeneration == generation + 1 && !state.isBurning
    }

    private fun publishBatchJob(generation: Int, jobs: List<BatchJob>, id: String, jobState: BatchJobState) {
        SwingUtilities.invokeLater {
            if (!canApplyBatchResult(generation)) return@invokeLater
            val index = jobs.indexOfFirst { it.id == id }
            if (index < 0) return@invokeLater
            val updated = state.batchJobs + (id to jobState)
            val finished = updated.values.count {
                it is BatchJobState.Done || it is BatchJobState.Failed || it is BatchJobState.Cancelled
            }
            val running = (jobState as? BatchJobState.Running)?.fraction ?: 0f
            val percent = when (jobState) {
                is BatchJobState.Running -> (jobState.fraction * 100f).roundToInt()
                is BatchJobState.Done, is BatchJobState.Failed -> 100
                BatchJobState.Pending, BatchJobState.Cancelled -> 0
            }
            state = state.copy(
                batchJobs = updated,
                burnProgress = (finished + running) / jobs.size.coerceAtLeast(1),
                burnMessage = uiText(
                    Res.string.batch_progress,
                    index + 1,
                    jobs.size,
                    jobs[index].video.fileName.toString(),
                    percent,
                ),
            )
        }
    }

    private fun publishBatchSummary(generation: Int, summary: BatchSummary) {
        SwingUtilities.invokeLater {
            if (!canApplyBatchResult(generation)) return@invokeLater
            state = state.copy(
                isBurning = false,
                burnProgress = 1f,
                burnMessage = uiText(Res.string.batch_summary, summary.done, summary.failed, summary.cancelled),
                burnError = if (summary.failed > 0) uiText(Res.string.batch_some_failed, summary.failed) else null,
                batchJobs = summary.states,
            )
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

    private fun previewVideoPath(): Path? {
        val list = state.clipList ?: return state.selectedMp4
        val id = state.previewClipId ?: return null
        return list.entries.find { it.id == id }?.info?.files?.video
    }

    private fun previewOffset(): OsdOffset {
        val global = OsdOffset(state.osdOffsetX, state.osdOffsetY)
        val list = state.clipList ?: return global
        val id = state.previewClipId ?: return global
        return list.effectiveOffset(id, global)
    }

    private fun previewSrt(): SrtFile? {
        val list = state.clipList ?: return state.srt
        val id = state.previewClipId ?: return null
        return list.entries.find { it.id == id }?.info?.srt
    }

    private fun drawSrt(image: java.awt.image.BufferedImage, videoInfo: VideoInfo, selectedFrame: Float) {
        val srt = previewSrt() ?: return
        if (videoInfo.frameRate <= 0.0) return
        val cue = srtCueAt(srt.cues, selectedFrame / videoInfo.frameRate * 1000.0) ?: return
        val data = cue.data ?: return
        SrtOverlay.draw(image, data, state.srtOptions)
    }

    private fun rerenderCurrentFrame() {
        val hasSource = state.selectedMp4 != null || state.previewClipId != null
        if (!hasSource || state.videoInfo == null || state.osd == null || state.font == null) return
        onFrameSelected(state.selectedFrame)
    }

    private fun representativeSrt(list: ClipList): SrtFile? {
        val parsed = list.entries.mapNotNull { it.info.srt }
        val first = parsed.firstOrNull() ?: return null
        return first.copy(
            hasDistance = parsed.any { it.hasDistance },
            hasDebugFields = parsed.any { it.hasDebugFields },
        )
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

private data class OsdExportSource(
    val videoInfo: VideoInfo,
    val osd: Osd,
    val srt: SrtFile?,
)

private fun defaultPngRequiredBytes(info: VideoInfo): Long {
    val frames = if (info.frameNumber > 0) {
        info.frameNumber
    } else {
        (info.duration.toMillis() / 1000.0 * info.frameRate).roundToInt().coerceAtLeast(1)
    }
    return estimatePngSequenceBytes(info.width, info.height, frames)
}
