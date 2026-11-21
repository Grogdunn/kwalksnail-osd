package dev.grogdunn.kwalksnailosd.clip

import dev.grogdunn.kwalksnailosd.video.VideoInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class ClipFolderScanner {
    fun scan(folder: Path, probe: (Path) -> VideoInfo): ClipList {
        val grouped = listGroups(folder)
        val entries = grouped.map { files -> probeEntry(files, probe) }
        return ClipList(
            folder = folder,
            entries = entries,
            naturalOrderIds = entries.map { it.id },
        )
    }

    private fun listGroups(folder: Path): List<ClipFiles> {
        if (!Files.isDirectory(folder)) return emptyList()
        val files = try {
            Files.list(folder).use { stream ->
                stream.filter { Files.isRegularFile(it) }.toList()
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return files
            .groupBy { it.nameWithoutExtension }
            .mapNotNull { (baseName, paths) -> clipFiles(baseName, paths) }
            .sortedBy { it.baseName }
    }

    private fun clipFiles(baseName: String, paths: List<Path>): ClipFiles? {
        val video = paths.firstWithExtension("mp4") ?: return null
        return ClipFiles(
            baseName = baseName,
            video = video,
            osd = paths.firstWithExtension("osd"),
            srt = paths.firstWithExtension("srt"),
        )
    }

    private fun probeEntry(files: ClipFiles, probe: (Path) -> VideoInfo): ClipEntry {
        val (videoInfo, probeError) = try {
            probe(files.video) to null
        } catch (exception: Exception) {
            null to (exception.message ?: "unreadable")
        }
        return ClipEntry(
            id = files.video.toString(),
            info = ClipInfo(files = files, videoInfo = videoInfo, probeError = probeError),
        )
    }
}

private fun List<Path>.firstWithExtension(extension: String): Path? =
    filter { it.extension.equals(extension, ignoreCase = true) }
        .minWithOrNull(compareBy { it.fileName.toString() })
