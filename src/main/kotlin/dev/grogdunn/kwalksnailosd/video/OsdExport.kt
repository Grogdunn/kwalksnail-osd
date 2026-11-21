package dev.grogdunn.kwalksnailosd.video

import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path

enum class OsdExportMode {
    ChromaKey,
    ProRes4444,
    Vp9Alpha,
    PngSequence,
}

data class OsdExportSettings(
    val mode: OsdExportMode,
    val chromaColor: Color = Color.MAGENTA,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val adjustPlaybackSpeed: Boolean = false,
    val codec: VideoCodec = VideoCodec.H264,
    val bitrateMbps: Int = 8,
    val encoder: BurnEncoderPreference = BurnEncoderPreference.Software,
)

data class DiskSpaceCheck(
    val requiredBytes: Long,
    val availableBytes: Long,
    val enough: Boolean,
)

fun estimatePngSequenceBytes(width: Int, height: Int, frames: Int): Long =
    width.toLong() * height.toLong() * 4L * frames.toLong()

fun checkPngDiskSpace(folder: Path, requiredBytes: Long): DiskSpaceCheck {
    val storePath = existingAncestor(folder)
    val available = Files.getFileStore(storePath).usableSpace
    return DiskSpaceCheck(
        requiredBytes = requiredBytes,
        availableBytes = available,
        enough = available >= requiredBytes,
    )
}

private fun existingAncestor(folder: Path): Path {
    var target = folder.toAbsolutePath().normalize()
    while (!Files.exists(target)) {
        target = target.parent ?: break
    }
    return if (Files.exists(target)) target else Path.of(".").toAbsolutePath().normalize()
}
