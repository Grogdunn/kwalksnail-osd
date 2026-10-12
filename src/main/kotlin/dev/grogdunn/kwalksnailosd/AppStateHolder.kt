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
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import dev.grogdunn.kwalksnailosd.video.VideoParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.extension

data class AppUiState(
    val selectedMp4: Path? = null,
    val selectedSrt: Path? = null,

    val selectedOsd: Path? = null,
    val osd: Osd? = null,
    val isParsingOsd: Boolean = false,
    val osdError: String? = null,

    val selectedFont: Path? = null,
    val font: Font? = null,
    val videoInfo: VideoInfo? = null,
    val selectedFrame: Float = 0.0f,
    val bitmap: ImageBitmap? = null
)

class AppStateHolder(
    private val osdParser: OsdParser = OsdParser(),
    private val fontParser: FontParser = FontParser(),
    private val videoParser: VideoParser = VideoParser()
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    var state by mutableStateOf(AppUiState())
        private set

    fun onFilePickerClosed(path: Path?) {
        if (path == null) return

        val relatedFiles = lookupFiles(path)
        val selectedOsd = if (path.extension.equals("osd", ignoreCase = true)) path else state.selectedOsd ?: relatedFiles.osdPath
        val osdChanged = selectedOsd != state.selectedOsd
        val selectedMp4 = if (path.extension.equals("mp4", ignoreCase = true)) path else state.selectedMp4 ?: relatedFiles.mp4Path
        val mp4Changed = selectedMp4 != state.selectedMp4
        state = state.copy(
            selectedMp4 = selectedMp4,
            selectedOsd = selectedOsd,
            selectedSrt = state.selectedSrt ?: relatedFiles.srtPath,
            osd = if (osdChanged) null else state.osd,
            isParsingOsd = !osdChanged && state.isParsingOsd,
            osdError = if (osdChanged) null else state.osdError,
            videoInfo = if (mp4Changed) null else state.videoInfo,
            bitmap = if (mp4Changed) null else state.bitmap,
            selectedFrame = if (mp4Changed) 0.0f else state.selectedFrame
        )
    }

    fun onFontFilePickerClosed(path: Path?) {
        if (path == null) return

        state = state.copy(selectedFont = path)
    }

    suspend fun parseSelectedFont() {
        val font = withContext(Dispatchers.IO) { state.selectedFont?.let { fontParser.parse(it) } }
        state = state.copy(font = font)
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
        logger.info { "onFrameSelected: $selectedFrame" }
        val selectedFrameImage = videoParser.getVideoFrame(mp4, selectedFrame.toInt()) ?: return
        val frameTimestamp = selectedFrame / videoInfo.frameRate * 1000
        val frame = osd.frames.findLast { it.timestampMillis <= frameTimestamp.toLong() }
        frame ?: return

        OsdRenderer().renderInPlace(selectedFrameImage, frame, font)
        state = state.copy(
            selectedFrame = selectedFrame,
            bitmap = selectedFrameImage.toComposeImageBitmap()
        )
    }

    override fun close() {
        videoParser.close()
    }
}
