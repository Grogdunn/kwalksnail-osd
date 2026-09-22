package dev.grogdunn.kwalksnailosd

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.streams.toList

data class LookupFilesResult(val mp4Path: Path?, val osdPath: Path?, val srtPath: Path?)

fun lookupFiles(base: Path): LookupFilesResult {
    val folder = base.parent
    val fileName = base.nameWithoutExtension
    val fileWithSameName = Files.list(folder).filter { it.nameWithoutExtension == fileName }.toList()
    val mp4Path = fileWithSameName.find { it.extension == "mp4" }
    val osdPath = fileWithSameName.find { it.extension == "osd" }
    val srtPath = fileWithSameName.find { it.extension == "srt" }
    println("lookupFiles called with: $base, mp4Path: $mp4Path, osdPath: $osdPath, srtPath: $srtPath")
    return LookupFilesResult(mp4Path, osdPath, srtPath)
}