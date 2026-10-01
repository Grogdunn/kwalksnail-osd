package dev.grogdunn.kwalksnailosd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grogdunn.kwalksnailosd.font.FontFile
import dev.grogdunn.kwalksnailosd.font.FontParser
import dev.grogdunn.kwalksnailosd.osd.Osd
import dev.grogdunn.kwalksnailosd.osd.OsdParser
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import dev.grogdunn.kwalksnailosd.video.VideoParser
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
    val font: FontFile? = null,
    val videoInfo: VideoInfo? = null,
)

class AppStateHolder(
    private val osdParser: OsdParser = OsdParser(),
    private val fontParser: FontParser = FontParser(),
    private val videoParser: VideoParser = VideoParser()
) {
    var state by mutableStateOf(AppUiState())
        private set

    fun onFilePickerClosed(path: Path?) {
        if (path == null) return

        val relatedFiles = lookupFiles(path)
        val selectedOsd = if (path.extension.equals("osd", ignoreCase = true)) path else state.selectedOsd ?: relatedFiles.osdPath
        val osdChanged = selectedOsd != state.selectedOsd
        state = state.copy(
            selectedMp4 = if (path.extension.equals("mp4", ignoreCase = true)) path else state.selectedMp4 ?: relatedFiles.mp4Path,
            selectedOsd = selectedOsd,
            selectedSrt = state.selectedSrt ?: relatedFiles.srtPath,
            osd = if (osdChanged) null else state.osd,
            isParsingOsd = !osdChanged && state.isParsingOsd,
            osdError = if (osdChanged) null else state.osdError,
        )
    }

    fun onFontFilePickerClosed(path: Path?) {
        if (path == null) return

        state = state.copy(selectedFont = path)
    }

    suspend fun parseSelectedFont() {
        if (state.selectedFont == null) return
        val font = withContext(Dispatchers.IO) { state.selectedFont?.let { fontParser.parse(it) } }
        state = state.copy(font = font)
    }

    suspend fun parseSelectedMp4() {
        withContext(Dispatchers.IO) {
            val videoInfo = state.selectedMp4?.let { videoParser.getVideoInfo(it) }
            state = state.copy(videoInfo = videoInfo)
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

}
