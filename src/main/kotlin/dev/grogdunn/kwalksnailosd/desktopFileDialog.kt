package dev.grogdunn.kwalksnailosd

import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import java.nio.file.Path
import kotlin.io.path.extension

fun desktopFileDialog(
    allowedExtensions: List<String> = emptyList(),
    onCloseRequest: (result: Path?) -> Unit
) {
    val fileDialog = FileDialog(null as Frame?, "Seleziona un file", FileDialog.LOAD).apply {
        if (allowedExtensions.isNotEmpty()) {
            filenameFilter = FilenameFilter { _, name -> allowedExtensions.any { name.endsWith(it, ignoreCase = true) } }
        }
        isVisible = true
    }

    val file = fileDialog.file
    val directory = fileDialog.directory

    if (file != null && directory != null) {
        onCloseRequest(Path.of(directory, file))
    } else {
        onCloseRequest(null) // L'utente ha annullato
    }

}

fun desktopSaveDialog(
    suggestedName: String,
    onCloseRequest: (result: Path?) -> Unit
) {
    val fileDialog = FileDialog(null as Frame?, "Salva video con OSD", FileDialog.SAVE).apply {
        file = suggestedName
        isVisible = true
    }
    val selected = fileDialog.file
    val directory = fileDialog.directory
    if (selected == null || directory == null) {
        onCloseRequest(null)
        return
    }
    val path = Path.of(directory, selected)
    onCloseRequest(if (path.extension.equals("mp4", ignoreCase = true)) path else Path.of("$path.mp4"))
}