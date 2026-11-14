package dev.grogdunn.kwalksnailosd.osd

import java.time.Duration

/**
 * Allineamento dell'OSD al video, con lo stesso comportamento di
 * avsaase/walksnail-osd-tool (GPL-3.0): il fattore di velocità è
 * `durata video / durata OSD` (ui/src/bottom_panel.rs) e un frame OSD
 * entra quando il tempo del video supera `timestamp * fattore`
 * (backend/src/overlay/iter.rs). Il centraggio usa il riquadro dei glifi
 * (ui/src/osd_preview.rs). Il codice è riscritto, non copiato dal Rust.
 */
fun osdPlaybackSpeedFactor(videoDuration: Duration, osdDuration: Duration, adjust: Boolean): Double {
    if (!adjust) return 1.0
    val osdMillis = osdDuration.toMillis()
    if (osdMillis <= 0L) return 1.0
    return videoDuration.toMillis().toDouble() / osdMillis.toDouble()
}

fun osdFrameAt(frames: List<Frame>, videoTimeMillis: Double, speedFactor: Double): Frame? {
    if (frames.isEmpty()) return null
    val factor = if (speedFactor <= 0.0) 1.0 else speedFactor
    val scaledVideoTime = videoTimeMillis / factor
    return frames.findLast { it.timestampMillis < scaledVideoTime } ?: frames.first()
}

fun osdHorizontalOffset(videoWidth: Int, frame: Frame, tileWidth: Int): Int =
    osdAxisOffset(videoWidth, frame.glyphs.map { it.position.x }, tileWidth)

fun osdVerticalOffset(videoHeight: Int, frame: Frame, tileHeight: Int): Int =
    osdAxisOffset(videoHeight, frame.glyphs.map { it.position.y }, tileHeight)

private fun osdAxisOffset(videoExtent: Int, gridCoordinates: List<Int>, tileExtent: Int): Int {
    if (gridCoordinates.isEmpty() || tileExtent <= 0) return 0
    val min = gridCoordinates.min()
    val max = gridCoordinates.max()
    val pixelRange = (max - min + 1) * tileExtent
    return (videoExtent - pixelRange) / 2 - min * tileExtent
}
