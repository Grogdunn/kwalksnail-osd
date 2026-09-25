package dev.grogdunn.kwalksnailosd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grogdunn.kwalksnailosd.osd.OsdParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.extension

data class AppUiState(
    val selectedMp4: Path? = null,
    val selectedSrt: Path? = null,

    val selectedOsd: Path? = null,
    val osd: OsdParser.Osd? = null,
    val isParsingOsd: Boolean = false,
    val osdError: String? = null,
)

class AppStateHolder(private val osdParser: OsdParser = OsdParser()) {
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

    suspend fun parseSelectedOsd(path: Path) {
        if (state.selectedOsd != path) return

        state = state.copy(osd = null, isParsingOsd = true, osdError = null)
        try {
            val parsedOsd = withContext(Dispatchers.IO) { osdParser.parse(path) }
            if (state.selectedOsd == path) {
                state = state.copy(osd = parsedOsd)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (state.selectedOsd == path) {
                state = state.copy(osd = null, osdError = exception.message ?: "Unable to parse OSD file")
            }
        } finally {
            if (state.selectedOsd == path) {
                state = state.copy(isParsingOsd = false)
            }
        }
    }
}
