package dev.grogdunn.kwalksnailosd

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

private val logger = KotlinLogging.logger {}

data class LookupFilesResult(val mp4Path: Path?, val osdPath: Path?, val srtPath: Path?)

fun lookupFiles(base: Path): LookupFilesResult {
    val folder = base.parent
    val fileName = base.nameWithoutExtension
    val fileWithSameName = Files.list(folder).use { stream ->
        stream.filter { it.nameWithoutExtension.equals(fileName, ignoreCase = true) }.toList()
    }
    val mp4Path = fileWithSameName.find { it.extension.equals("mp4", ignoreCase = true) }
    val osdPath = fileWithSameName.find { it.extension.equals("osd", ignoreCase = true) }
    val srtPath = fileWithSameName.find { it.extension.equals("srt", ignoreCase = true) }
    logger.info { "lookupFiles called with: $base, mp4Path: $mp4Path, osdPath: $osdPath, srtPath: $srtPath" }
    return LookupFilesResult(mp4Path, osdPath, srtPath)
}