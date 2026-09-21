package dev.grogdunn.kwalksnailosd

import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import java.nio.file.Path

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