package dev.grogdunn.kwalksnailosd.srt

import java.util.Locale

data class SrtData(
    val signal: Int,
    val channel: Int,
    val flightTimeSeconds: Int,
    val skyBatteryVolts: Double,
    val groundBatteryVolts: Double,
    val latencyMillis: Int,
    val bitrateMbps: Double,
    val distanceMeters: Int,
)

data class SrtCue(
    val startMillis: Long,
    val endMillis: Long,
    val data: SrtData?,
)

data class SrtFile(
    val cues: List<SrtCue>,
    val hasDistance: Boolean,
)

data class SrtOptions(
    val xPercent: Float = 1.5f,
    val yPercent: Float = 95f,
    val scale: Float = 35f,
    val showTime: Boolean = false,
    val showSkyBattery: Boolean = false,
    val showGroundBattery: Boolean = false,
    val showSignal: Boolean = true,
    val showLatency: Boolean = true,
    val showBitrate: Boolean = true,
    val showDistance: Boolean = true,
)

/**
 * Stessa scelta dei campi e stessa formattazione di avsaase/walksnail-osd-tool
 * (GPL-3.0), backend/src/overlay/srt.rs. Il testo è riscritto.
 */
fun srtLabel(data: SrtData, options: SrtOptions): String = buildString {
    if (options.showTime) {
        val minutes = data.flightTimeSeconds / 60
        val seconds = data.flightTimeSeconds % 60
        append("Time:$minutes:${seconds.toString().padStart(2, '0')}  ")
    }
    if (options.showSkyBattery) append("SBat:${volts(data.skyBatteryVolts)}V  ")
    if (options.showGroundBattery) append("GBat:${volts(data.groundBatteryVolts)}V  ")
    if (options.showSignal) append("Signal:${data.signal}  ")
    if (options.showLatency) append("Latency:${latency(data.latencyMillis)}ms  ")
    if (options.showBitrate) append("Bitrate:${bitrate(data.bitrateMbps)}Mbps  ")
    if (options.showDistance) append(distance(data.distanceMeters))
}

/** Assumes [cues] is sorted by non-decreasing [SrtCue.startMillis]. */
fun srtCueAt(cues: List<SrtCue>, videoTimeMillis: Double): SrtCue? {
    if (cues.isEmpty()) return null
    var low = 0
    var high = cues.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (cues[mid].startMillis < videoTimeMillis) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    return cues[if (low == 0) 0 else low - 1]
}

private fun volts(value: Double): String = String.format(Locale.US, "%4.1f", value)

private fun latency(value: Int): String = String.format(Locale.US, "%3d", value)

private fun bitrate(value: Double): String = String.format(Locale.US, "%4.1f", value)

private fun distance(meters: Int): String = if (meters > 999) {
    "Distance:${String.format(Locale.US, "%.2f", meters / 1000.0)}km"
} else {
    "Distance:${String.format(Locale.US, "%3d", meters)}m"
}
