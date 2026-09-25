package dev.grogdunn.kwalksnailosd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Path

data class AppUiState(
    val selectedMp4: Path? = null,
    val selectedOsd: Path? = null,
    val selectedSrt: Path? = null,
)

class AppStateHolder {
    var state by mutableStateOf(AppUiState())
        private set

    fun onFilePickerClosed(path: Path?) {
        if (path == null) return

        val relatedFiles = lookupFiles(path)
        state = state.copy(
            selectedMp4 = state.selectedMp4 ?: relatedFiles.mp4Path,
            selectedOsd = state.selectedOsd ?: relatedFiles.osdPath,
            selectedSrt = state.selectedSrt ?: relatedFiles.srtPath,
        )
    }
}
