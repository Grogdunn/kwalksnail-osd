package dev.grogdunn.kwalksnailosd.srt

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.error_srt_empty
import dev.grogdunn.kwalksnailosd.uiText
import java.nio.file.Files
import java.nio.file.Path

class SrtParser {
    fun parse(path: Path): SrtFile {
        val text = Files.readString(path).removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val cues = text.split(Regex("\n\\s*\n"))
            .mapNotNull { parseBlock(it) }
            // Sorted so srtCueAt can binary-search. Valid files are already non-decreasing; a malformed file may not be.
            .sortedBy { it.startMillis }
        if (cues.isEmpty()) {
            throw IllegalArgumentException(uiText(Res.string.error_srt_empty))
        }
        return SrtFile(
            cues = cues,
            hasDistance = cues.any { (it.data?.distanceMeters ?: 0) > 0 },
            hasDebugFields = cues.any { it.data.hasDebugFields() },
        )
    }

    private fun parseBlock(block: String): SrtCue? {
        val lines = block.lines().filter { it.isNotBlank() }
        val timingIndex = lines.indexOfFirst { it.contains("-->") }
        if (timingIndex < 0) return null
        val match = TIMING.find(lines[timingIndex]) ?: return null
        val start = toMillis(match.groupValues, 1)
        val end = toMillis(match.groupValues, 5)
        val body = lines.drop(timingIndex + 1).joinToString(" ")
        return SrtCue(start, end, parseData(body))
    }

    private fun parseData(body: String): SrtData? {
        var found = false
        fun take(regex: Regex): String? {
            val value = regex.find(body)?.groupValues?.get(1) ?: return null
            found = true
            return value
        }
        val signal = take(SIGNAL)?.toIntOrNull()
        val explicitChannel = take(CHANNEL)?.toIntOrNull()
        val hz = take(HZ)?.toIntOrNull()
        val flightTimeSeconds = take(FLIGHT_TIME)?.toIntOrNull()
        val skyBatteryVolts = take(SKY_BATTERY)?.toDoubleOrNull()
        val groundBatteryVolts = take(GROUND_BATTERY)?.toDoubleOrNull()
        val latencyMillis = take(DELAY)?.toIntOrNull()
        val bitrateMbps = take(BITRATE)?.toDoubleOrNull()
        val distanceMeters = take(DISTANCE)?.toIntOrNull()
        val mcs = take(MCS)?.toIntOrNull()
        val groundSnr = take(GROUND_SNR)?.toDoubleOrNull()
        val skySnr = take(SKY_SNR)?.toDoubleOrNull()
        val groundTempC = take(GROUND_TEMP)?.toIntOrNull()
        val skyTempC = take(SKY_TEMP)?.toIntOrNull()
        if (!found) return null
        return SrtData(
            signal = signal,
            channel = explicitChannel ?: hz?.let(::channelFromKhz),
            flightTimeSeconds = flightTimeSeconds,
            skyBatteryVolts = skyBatteryVolts,
            groundBatteryVolts = groundBatteryVolts,
            latencyMillis = latencyMillis,
            bitrateMbps = bitrateMbps,
            distanceMeters = distanceMeters,
            mcs = mcs,
            groundSnr = groundSnr,
            skySnr = skySnr,
            groundTempC = groundTempC,
            skyTempC = skyTempC,
        )
    }

    private fun channelFromKhz(khz: Int): Int? {
        val index = R_BAND_KHZ.indexOfFirst { kotlin.math.abs(it - khz) <= CHANNEL_TOLERANCE_KHZ }
        return if (index >= 0) index + 1 else null
    }

    private fun toMillis(groups: List<String>, startIndex: Int): Long {
        val hours = groups[startIndex].toLong()
        val minutes = groups[startIndex + 1].toLong()
        val seconds = groups[startIndex + 2].toLong()
        val millis = groups[startIndex + 3].toLong()
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }

    private companion object {
        val TIMING: Regex = Regex(
            """(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})"""
        )

        // Campi indipendenti: legacy, Caddx Ascent (Hz al posto di CH) e debug.
        // \b evita che CH: o Hz: matchino dentro altri token (es. MHz:, GCH:).
        val SIGNAL: Regex = Regex("""\bSignal:(\d+)""")
        val CHANNEL: Regex = Regex("""\bCH:(\d+)""")
        val HZ: Regex = Regex("""\bHz:(\d+)""")
        val FLIGHT_TIME: Regex = Regex("""\bFlightTime:(\d+)""")
        val SKY_BATTERY: Regex = Regex("""\bSBat:([0-9.]+)V""")
        val GROUND_BATTERY: Regex = Regex("""\bGBat:([0-9.]+)V""")
        val DELAY: Regex = Regex("""\bDelay:(\d+)ms""")
        val BITRATE: Regex = Regex("""\bBitrate:([0-9.]+)Mbps""")
        val DISTANCE: Regex = Regex("""\bDistance:(\d+)m""")
        val MCS: Regex = Regex("""\bMCS:(\d+)""")
        val GROUND_SNR: Regex = Regex("""\bGSNR:(-?[0-9.]+)""")
        val SKY_SNR: Regex = Regex("""\bSSNR:(-?[0-9.]+)""")
        val GROUND_TEMP: Regex = Regex("""\bGtemp:(-?\d+)""")
        val SKY_TEMP: Regex = Regex("""\bStemp:(-?\d+)""")

        // Walksnail R-band, MHz * 1000. Il valore Hz del firmware è in kHz.
        val R_BAND_KHZ: IntArray = intArrayOf(
            5_658_000, 5_695_000, 5_732_000, 5_769_000,
            5_806_000, 5_843_000, 5_880_000, 5_917_000,
        )
        const val CHANNEL_TOLERANCE_KHZ: Int = 5_000
    }
}

private fun SrtData?.hasDebugFields(): Boolean {
    this ?: return false
    return mcs != null || groundSnr != null || skySnr != null || groundTempC != null || skyTempC != null
}
