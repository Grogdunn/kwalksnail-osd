package dev.grogdunn.kwalksnailosd.srt

import java.util.Locale

data class SrtData(
    val signal: Int?,
    val channel: Int?,
    val flightTimeSeconds: Int?,
    val skyBatteryVolts: Double?,
    val groundBatteryVolts: Double?,
    val latencyMillis: Int?,
    val bitrateMbps: Double?,
    val distanceMeters: Int?,
    val mcs: Int? = null,
    val groundSnr: Double? = null,
    val skySnr: Double? = null,
    val groundTempC: Int? = null,
    val skyTempC: Int? = null,
)

data class SrtCue(
    val startMillis: Long,
    val endMillis: Long,
    val data: SrtData?,
)

data class SrtFile(
    val cues: List<SrtCue>,
    val hasDistance: Boolean,
    val hasDebugFields: Boolean,
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
    val showMcs: Boolean = false,
    val showSnr: Boolean = false,
    val showTemp: Boolean = false,
)

/**
 * Stessa scelta dei campi e stessa formattazione di avsaase/walksnail-osd-tool
 * (GPL-3.0), backend/src/overlay/srt.rs. Il testo è riscritto.
 */
fun srtLabel(data: SrtData, options: SrtOptions): String = buildString {
    if (options.showTime) {
        data.flightTimeSeconds?.let { flightTime ->
            val minutes = flightTime / 60
            val seconds = flightTime % 60
            append("Time:$minutes:${seconds.toString().padStart(2, '0')}  ")
        }
    }
    if (options.showSkyBattery) data.skyBatteryVolts?.let { append("SBat:${volts(it)}V  ") }
    if (options.showGroundBattery) data.groundBatteryVolts?.let { append("GBat:${volts(it)}V  ") }
    if (options.showSignal) data.signal?.let { append("Signal:$it  ") }
    if (options.showLatency) data.latencyMillis?.let { append("Latency:${latency(it)}ms  ") }
    if (options.showBitrate) data.bitrateMbps?.let { append("Bitrate:${bitrate(it)}Mbps  ") }
    if (options.showMcs) data.mcs?.let { append("MCS:$it  ") }
    if (options.showSnr && data.groundSnr != null && data.skySnr != null) {
        append("SNR:${oneDecimal(data.groundSnr)}/${oneDecimal(data.skySnr)}  ")
    }
    if (options.showTemp && data.groundTempC != null && data.skyTempC != null) {
        append("Temp:${data.groundTempC}/${data.skyTempC}C  ")
    }
    if (options.showDistance) data.distanceMeters?.let { append(distance(it)) }
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

private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun volts(value: Double): String = String.format(Locale.US, "%4.1f", value)

private fun latency(value: Int): String = String.format(Locale.US, "%3d", value)

private fun bitrate(value: Double): String = String.format(Locale.US, "%4.1f", value)

private fun distance(meters: Int): String = if (meters > 999) {
    "Distance:${String.format(Locale.US, "%.2f", meters / 1000.0)}km"
} else {
    "Distance:${String.format(Locale.US, "%3d", meters)}m"
}
