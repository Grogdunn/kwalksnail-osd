package dev.grogdunn.kwalksnailosd.srt

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.abs

/**
 * One second (or one cue) of Walksnail link telemetry.
 * A field is null when that firmware did not write it or the value was unreadable.
 */
data class TelemetrySample(
    val signal: Int? = null,
    val channel: Int? = null,
    val latencyMillis: Int? = null,
    val bitrateMbps: Double? = null,
    val goggleTemperatureCelsius: Double? = null,
    val vtxTemperatureCelsius: Double? = null,
    val goggleVoltage: Double? = null,
    val vtxVoltage: Double? = null,
    val flightTimeSeconds: Int? = null,
    val distanceMeters: Int? = null,
) {
    fun isEmpty(): Boolean =
        signal == null &&
            channel == null &&
            latencyMillis == null &&
            bitrateMbps == null &&
            goggleTemperatureCelsius == null &&
            vtxTemperatureCelsius == null &&
            goggleVoltage == null &&
            vtxVoltage == null &&
            flightTimeSeconds == null &&
            distanceMeters == null
}

data class TelemetryCue(
    val startMillis: Long,
    val endMillis: Long,
    val sample: TelemetrySample,
)

data class TelemetryTrack(
    val cues: List<TelemetryCue>,
    val skippedCues: Int = 0,
) {
    val hasSignal: Boolean get() = cues.any { it.sample.signal != null }
    val hasChannel: Boolean get() = cues.any { it.sample.channel != null }
    val hasLatency: Boolean get() = cues.any { it.sample.latencyMillis != null }
    val hasBitrate: Boolean get() = cues.any { it.sample.bitrateMbps != null }
    val hasGoggleTemperature: Boolean get() = cues.any { it.sample.goggleTemperatureCelsius != null }
    val hasVtxTemperature: Boolean get() = cues.any { it.sample.vtxTemperatureCelsius != null }
    val hasGoggleVoltage: Boolean get() = cues.any { it.sample.goggleVoltage != null }
    val hasVtxVoltage: Boolean get() = cues.any { it.sample.vtxVoltage != null }
    val hasFlightTime: Boolean get() = cues.any { it.sample.flightTimeSeconds != null }
    val hasDistance: Boolean get() = cues.any { it.sample.distanceMeters != null }

    /**
     * Sample for a video frame at [timestampMillis].
     * The latest cue that has already started is kept across gaps and after the
     * last cue ends. A frame earlier than the first cue still uses that first cue,
     * so a small clock mismatch does not leave the overlay blank.
     */
    fun sampleAt(timestampMillis: Long): TelemetrySample? {
        if (cues.isEmpty()) return null
        var low = 0
        var high = cues.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMillis <= timestampMillis) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return cues[if (found >= 0) found else 0].sample
    }
}

/**
 * Parser for the SRT file Walksnail writes next to a DVR recording.
 * Known layouts are the standard link line (with or without units and distance),
 * the decimal-bitrate line from firmware 32.37.10, the Caddx Ascent `Hz:` line,
 * and the debug line (`MCS`, `Gtemp`, `Stemp`) including the longer 39.44.15 text.
 * Unreadable cues are skipped. The rest of the file is kept.
 */
object WalksnailSrt {
    fun parse(path: Path): TelemetryTrack {
        val bytes = Files.readAllBytes(path)
        val text = Charsets.UTF_8.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        return parse(text)
    }

    fun parse(text: String): TelemetryTrack {
        val normalized = text.trimStart('\uFEFF').replace("\r\n", "\n").replace('\r', '\n')
        val cues = mutableListOf<TelemetryCue>()
        var skipped = 0
        var startMillis: Long? = null
        var endMillis: Long? = null
        val body = StringBuilder()

        fun flush() {
            val start = startMillis
            val end = endMillis
            if (start != null && end != null) {
                val sample = parseSample(body.toString())
                if (sample.isEmpty()) {
                    skipped += 1
                } else {
                    cues += TelemetryCue(start, end, sample)
                }
            }
            startMillis = null
            endMillis = null
            body.clear()
        }

        for (rawLine in normalized.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                flush()
                continue
            }
            val timing = TIMING.find(line)
            if (timing != null) {
                flush()
                startMillis = toMillis(timing.groupValues[1], timing.groupValues[2], timing.groupValues[3], timing.groupValues[4])
                endMillis = toMillis(timing.groupValues[5], timing.groupValues[6], timing.groupValues[7], timing.groupValues[8])
                continue
            }
            if (startMillis == null && line.toIntOrNull() != null) {
                continue
            }
            if (startMillis != null) {
                if (body.isNotEmpty()) body.append(' ')
                body.append(line)
            }
        }
        flush()

        val ordered = cues.sortedBy { it.startMillis }
        return TelemetryTrack(ordered, skipped)
    }

    internal fun parseSample(text: String): TelemetrySample {
        val cleaned = TAGS.replace(text, " ")
        var signal: Int? = null
        var channel: Int? = null
        var frequency: Double? = null
        var latency: Int? = null
        var bitrate: Double? = null
        var goggleTemp: Double? = null
        var vtxTemp: Double? = null
        var goggleVoltage: Double? = null
        var vtxVoltage: Double? = null
        var flightTime: Int? = null
        var distance: Int? = null

        for (match in FIELD.findAll(cleaned)) {
            val key = match.groupValues[1].lowercase(Locale.US)
            val value = match.groupValues[2]
            when (key) {
                "signal" -> signal = parseInt(value)
                "mcs" -> if (signal == null) signal = parseInt(value)
                "ch", "channel" -> channel = parseInt(value)
                "hz" -> frequency = parseDouble(value)
                "delay", "latency" -> latency = parseInt(value)
                "bitrate" -> bitrate = parseDouble(value)
                "gtemp", "goggletemp" -> goggleTemp = parseDouble(value)
                "stemp", "vtxtemp", "skytemp" -> vtxTemp = parseDouble(value)
                "gbat", "gogglebat", "groundbat" -> goggleVoltage = parseDouble(value)
                "sbat", "vtxbat", "skybat" -> vtxVoltage = parseDouble(value)
                "flighttime" -> flightTime = parseInt(value)
                "distance" -> distance = parseInt(value)
            }
        }

        if (channel == null && frequency != null) {
            channel = channelFromFrequency(frequency)
        }

        return TelemetrySample(
            signal = signal,
            channel = channel,
            latencyMillis = latency,
            bitrateMbps = bitrate,
            goggleTemperatureCelsius = goggleTemp,
            vtxTemperatureCelsius = vtxTemp,
            goggleVoltage = goggleVoltage,
            vtxVoltage = vtxVoltage,
            flightTimeSeconds = flightTime,
            distanceMeters = distance,
        )
    }

    /**
     * Avatar R-band centers, in MHz. Ascent writes the carrier in Hz (`Hz:5695000` is R2).
     * A reading within 5 MHz of a center maps to that channel (1..8).
     */
    internal fun channelFromFrequency(raw: Double): Int? {
        val mhz = if (raw >= 10_000.0) raw / 1_000.0 else raw
        val index = R_BAND_MHZ.indexOfFirst { abs(it - mhz) <= 5.0 }
        return if (index >= 0) index + 1 else null
    }

    private fun parseInt(raw: String): Int? = parseDouble(raw)?.toInt()

    private fun parseDouble(raw: String): Double? {
        var token = raw.trim().trimEnd(',', ';')
        if (token.isEmpty()) return null
        token = UNIT_SUFFIX.replace(token, "")
        if (token.isEmpty() || (token.any { it == '-' } && token.none { it.isDigit() })) return null
        val number = token.toDoubleOrNull() ?: return null
        if (!number.isFinite() || number < 0.0) return null
        return number
    }

    private fun toMillis(hours: String, minutes: String, seconds: String, fraction: String): Long {
        val millis = when (fraction.length) {
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return ((hours.toLong() * 60 + minutes.toLong()) * 60 + seconds.toLong()) * 1000 + millis
    }

    private val TIMING = Regex(
        """(\d+):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d+):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )
    private val FIELD = Regex("""([A-Za-z][A-Za-z0-9]*)\s*:\s*([^\s,\]]+)""")
    private val TAGS = Regex("<[^>]*>")
    private val UNIT_SUFFIX = Regex("(?i)(mbps|mbit/s|mbit|mb/s|mb|mhz|ms|°c|℃|c|v|m)$")
    private val R_BAND_MHZ = doubleArrayOf(5658.0, 5695.0, 5732.0, 5769.0, 5806.0, 5843.0, 5880.0, 5917.0)
}
