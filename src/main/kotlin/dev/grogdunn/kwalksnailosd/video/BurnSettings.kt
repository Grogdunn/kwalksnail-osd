package dev.grogdunn.kwalksnailosd.video

import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

enum class VideoCodec(val label: String) {
    H264("H.264"),
    H265("H.265"),
}

data class BurnSettings(
    val codec: VideoCodec,
    val bitrateMbps: Int,
    val encoder: BurnEncoderPreference,
    val offsetX: Int,
    val offsetY: Int,
    val adjustPlaybackSpeed: Boolean,
)

data class BurnProgress(val fraction: Float, val message: String)

data class BurnResult(
    val output: Path,
    val encoderName: String,
    val hardware: Boolean,
    val framesWritten: Int,
    val usedSoftwareFallback: Boolean,
    val fallbackDetail: String? = null,
)

fun outputNameFor(input: Path): String = "${input.nameWithoutExtension}_with_osd.mp4"
