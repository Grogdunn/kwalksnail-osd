package dev.grogdunn.kwalksnailosd.srt

import java.nio.file.Files
import java.nio.file.Path

class SrtParser {
    fun parse(path: Path): SrtFile {
        val text = Files.readString(path).removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val cues = text.split(Regex("\n\\s*\n"))
            .mapNotNull { parseBlock(it) }
        if (cues.isEmpty()) {
            throw IllegalArgumentException("Il file SRT non contiene sottotitoli.")
        }
        return SrtFile(
            cues = cues,
            hasDistance = cues.any { (it.data?.distanceMeters ?: 0) > 0 },
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
        val match = DATA.find(body) ?: return null
        return SrtData(
            signal = match.groupValues[1].toInt(),
            channel = match.groupValues[2].toInt(),
            flightTimeSeconds = match.groupValues[3].toInt(),
            skyBatteryVolts = match.groupValues[4].toDouble(),
            groundBatteryVolts = match.groupValues[5].toDouble(),
            latencyMillis = match.groupValues[6].toInt(),
            bitrateMbps = match.groupValues[7].toDouble(),
            distanceMeters = match.groupValues[8].toInt(),
        )
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

        // Righe reali da avsaase/walksnail-osd-tool, backend/src/srt/frame.rs (GPL-3.0).
        val DATA: Regex = Regex(
            """Signal:(\d+)\s+CH:(\d+)\s+FlightTime:(\d+)\s+SBat:([0-9.]+)V\s+GBat:([0-9.]+)V\s+Delay:(\d+)ms\s+Bitrate:([0-9.]+)Mbps\s+Distance:(\d+)m"""
        )
    }
}
