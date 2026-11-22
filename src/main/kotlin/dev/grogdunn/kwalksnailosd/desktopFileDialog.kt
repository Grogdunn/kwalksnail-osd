package dev.grogdunn.kwalksnailosd

import java.awt.Component
import java.awt.Container
import java.awt.FileDialog
import java.awt.Frame
import java.awt.event.HierarchyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JList
import javax.swing.SwingUtilities
import kotlin.io.path.extension

fun desktopFileDialog(
    title: String,
    allowedExtensions: List<String> = emptyList(),
    onCloseRequest: (result: Path?) -> Unit
) {
    val fileDialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
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

fun desktopMultiFileDialog(
    title: String,
    allowedExtensions: List<String>,
    onCloseRequest: (List<Path>) -> Unit,
) {
    val fileDialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        isMultipleMode = true
        if (allowedExtensions.isNotEmpty()) {
            filenameFilter = FilenameFilter { _, name -> allowedExtensions.any { name.endsWith(it, ignoreCase = true) } }
        }
        isVisible = true
    }
    onCloseRequest(fileDialog.files.map { it.toPath() })
}

fun desktopSaveDialog(
    suggestedName: String,
    title: String,
    defaultExtension: String? = "mp4",
    onCloseRequest: (result: Path?) -> Unit
) {
    val fileDialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
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
    val resolved = when {
        defaultExtension == null -> path
        path.extension.equals(defaultExtension, ignoreCase = true) -> path
        else -> Path.of("$path.$defaultExtension")
    }
    onCloseRequest(resolved)
}

fun desktopPngSequenceDialog(
    suggestedName: String,
    title: String,
    onCloseRequest: (result: Path?) -> Unit,
) {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogType = JFileChooser.SAVE_DIALOG
        selectedFile = File(suggestedName)
    }
    val selected = if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    if (selected == null) {
        onCloseRequest(null)
        return
    }
    val path = selected.toPath()
    val name = path.fileName?.toString().orEmpty()
    val output = when {
        name.equals(suggestedName, ignoreCase = true) -> path
        Files.exists(path) -> path.resolve(suggestedName)
        else -> path
    }
    onCloseRequest(output)
}

fun desktopFolderDialog(
    title: String,
    initialDirectory: Path? = null,
    onCloseRequest: (result: Path?) -> Unit,
) {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
            currentDirectory = initialDirectory.toFile()
        }
    }
    approveDirectoryOnSingleClick(chooser)
    val selected = if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    if (selected != null) {
        onCloseRequest(selected.toPath())
    } else {
        onCloseRequest(null)
    }
}

private fun approveDirectoryOnSingleClick(chooser: JFileChooser) {
    chooser.addHierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() == 0L || !chooser.isShowing) return@addHierarchyListener
        val list = findFileList(chooser) ?: return@addHierarchyListener
        if (list.getClientProperty(FOLDER_ONE_CLICK) == true) return@addHierarchyListener
        list.putClientProperty(FOLDER_ONE_CLICK, true)
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount != 1 || !SwingUtilities.isLeftMouseButton(event)) return
                val index = list.locationToIndex(event.point)
                if (index < 0) return
                val bounds = list.getCellBounds(index, index) ?: return
                if (!bounds.contains(event.point)) return
                val file = list.model.getElementAt(index) as? File ?: return
                if (!file.isDirectory) return
                chooser.selectedFile = file
                chooser.approveSelection()
            }
        })
    }
}

private fun findFileList(component: Component): JList<*>? {
    if (component is JList<*>) return component
    if (component is Container) {
        for (child in component.components) {
            findFileList(child)?.let { return it }
        }
    }
    return null
}

private const val FOLDER_ONE_CLICK = "folderOneClick"