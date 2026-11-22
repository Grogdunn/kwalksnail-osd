package dev.grogdunn.kwalksnailosd.clip

import dev.grogdunn.kwalksnailosd.osd.Osd
import dev.grogdunn.kwalksnailosd.srt.SrtFile
import dev.grogdunn.kwalksnailosd.video.OsdOffset
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import dev.grogdunn.kwalksnailosd.video.VideoSize
import java.nio.file.Path
import java.time.Duration
import kotlin.math.abs

private const val FPS_MISMATCH_DELTA = 0.05

data class ClipFiles(
    val baseName: String,
    val video: Path,
    val osd: Path?,
    val srt: Path?,
)

data class ClipInfo(
    val files: ClipFiles,
    val videoInfo: VideoInfo?,
    val probeError: String? = null,
    val osd: Osd? = null,
    val srt: SrtFile? = null,
    val osdError: String? = null,
    val srtError: String? = null,
)

data class ClipEntry(
    val id: String,
    val info: ClipInfo,
    val excluded: Boolean = false,
    val offset: OsdOffset? = null,
)

data class ClipMismatch(
    val resolution: Boolean,
    val fps: Boolean,
)

data class ClipList(
    val folder: Path,
    val entries: List<ClipEntry>,
    val naturalOrderIds: List<String>,
) {
    fun included(): List<ClipEntry> = entries.filter { !it.excluded }

    fun totalDuration(): Duration = included()
        .mapNotNull { it.info.videoInfo?.duration }
        .fold(Duration.ZERO) { total, duration -> total + duration }

    fun mismatch(): ClipMismatch {
        val probed = included().mapNotNull { it.info.videoInfo }
        if (probed.size < 2) return ClipMismatch(resolution = false, fps = false)
        val reference = probed.first()
        return ClipMismatch(
            resolution = probed.any { resolutionDiffers(it, reference) },
            fps = probed.any { fpsDiffers(it.frameRate, reference.frameRate) },
        )
    }

    fun targetSize(): VideoSize? {
        val info = included().firstNotNullOfOrNull { it.info.videoInfo } ?: return null
        return VideoSize(info.width, info.height)
    }

    fun highlighted(id: String, adaptResolution: Boolean = false): Boolean {
        val info = included().find { it.id == id }?.info?.videoInfo ?: return false
        val reference = included().firstNotNullOfOrNull { it.info.videoInfo } ?: return false
        val resolution = !adaptResolution && resolutionDiffers(info, reference)
        return resolution || fpsDiffers(info.frameRate, reference.frameRate)
    }

    fun movedUp(id: String): ClipList = swapWithNeighbour(id, -1)

    fun movedDown(id: String): ClipList = swapWithNeighbour(id, 1)

    fun withExcluded(id: String, excluded: Boolean): ClipList = copy(
        entries = entries.map { entry ->
            if (entry.id == id) entry.copy(excluded = excluded) else entry
        },
    )

    fun withOffset(id: String, offset: OsdOffset?): ClipList = copy(
        entries = entries.map { entry ->
            if (entry.id == id) entry.copy(offset = offset) else entry
        },
    )

    fun effectiveOffset(id: String, global: OsdOffset): OsdOffset =
        entries.find { it.id == id }?.offset ?: global

    fun restoredAz(): ClipList {
        val byId = entries.associateBy { it.id }
        val known = naturalOrderIds.toSet()
        val restored = naturalOrderIds.mapNotNull { byId[it] }
        val extras = entries.filter { it.id !in known }
        return copy(entries = restored + extras)
    }

    fun outputStem(): String? = included().firstOrNull()?.info?.files?.baseName?.plus("_joined")

    fun canBurn(adaptResolution: Boolean = false): Boolean {
        val included = included()
        if (included.isEmpty()) return false
        if (included.any { it.info.videoInfo == null || it.info.files.osd == null || it.info.osd == null }) return false
        val mismatch = mismatch()
        return (adaptResolution || !mismatch.resolution) && !mismatch.fps
    }

    private fun swapWithNeighbour(id: String, offset: Int): ClipList {
        val index = entries.indexOfFirst { it.id == id }
        val neighbour = index + offset
        if (index < 0 || neighbour !in entries.indices) return this
        val reordered = entries.toMutableList()
        val current = reordered[index]
        reordered[index] = reordered[neighbour]
        reordered[neighbour] = current
        return copy(entries = reordered)
    }
}

private fun resolutionDiffers(left: VideoInfo, right: VideoInfo): Boolean =
    left.width != right.width || left.height != right.height

private fun fpsDiffers(left: Double, right: Double): Boolean = abs(left - right) > FPS_MISMATCH_DELTA
