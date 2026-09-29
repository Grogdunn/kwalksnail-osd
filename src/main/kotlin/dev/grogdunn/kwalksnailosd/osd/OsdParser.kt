package dev.grogdunn.kwalksnailosd.osd

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

private const val HEADER_BYTES = 40
private const val FC_TYPE_BYTES = 4
private const val TIMESTAMP_BYTES = 4
private const val GLYPH_BYTES = 2
private const val GRID_WIDTH = 53
private const val GRID_HEIGHT = 20
private const val FRAME_BYTES = TIMESTAMP_BYTES + GRID_WIDTH * GRID_HEIGHT * GLYPH_BYTES

class OsdParser {
    fun parse(selectedOsd: Path): Osd {
        Files.newInputStream(selectedOsd).use { input ->
            val header = input.readNBytes(HEADER_BYTES)
            if (header.size != HEADER_BYTES) {
                throw IllegalArgumentException("OSD header is incomplete: expected $HEADER_BYTES bytes, got ${header.size}")
            }
            val fcBytes = header.take(FC_TYPE_BYTES).toByteArray()
            val fcFirmware = FcFirmware.getFcFirmware(fcBytes)
            val frames = mutableListOf<Frame>()
            while (true) {
                val frameBytes = input.readNBytes(FRAME_BYTES)
                if (frameBytes.isEmpty()) break
                if (frameBytes.size != FRAME_BYTES) {
                    throw IllegalArgumentException("OSD frame is incomplete: expected $FRAME_BYTES bytes, got ${frameBytes.size}")
                }
                frames.add(parseFrame(frameBytes))
            }

            val frameIntervalMillis = (frames.last().timestampMillis - frames.first().timestampMillis).toDouble() / (frames.size - 1).toDouble()
            val duration = Duration.ofMillis(frames.last().timestampMillis) + Duration.ofMillis(frameIntervalMillis.toLong())
            return Osd(fcFirmware, frames, duration)
        }
    }

    private fun parseFrame(frameBytes: ByteArray): Frame {
        require(frameBytes.size == FRAME_BYTES) {
            "OSD frame must be $FRAME_BYTES bytes long, but was ${frameBytes.size}"
        }
        val buffer = ByteBuffer.wrap(frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        val timestamp = Integer.toUnsignedLong(buffer.int)
        val totalGlyphs = GRID_WIDTH * GRID_HEIGHT
        val glyphs = ArrayList<Glyph>()
        for (idx in 0 until totalGlyphs) {
            val glyphValue = buffer.short.toUShort().toInt()
            if (glyphValue == 0x00 || glyphValue == 0x20) {
                continue
            }
            val x = idx % GRID_WIDTH
            val y = idx / GRID_WIDTH
            glyphs.add(Glyph(index = glyphValue, position = Position(x, y)))
        }
        return Frame(timestamp, glyphs)
    }
}

data class Osd(val fcFirmware: FcFirmware, val frames: List<Frame>, val duration: Duration)

enum class FcFirmware {
    BETAFLIGHT,
    INAV,
    ARDUPILOT,
    KISS,
    KISSULTRA,
    UNKNOWN;

    companion object {
        fun getFcFirmware(fcBytes: ByteArray): FcFirmware {
            if (fcBytes.size < 4) {
                throw IllegalArgumentException("FC bytes must be at least 4 bytes long")
            }
            return getFcFirmware(String(fcBytes, Charsets.UTF_8))
        }


        fun getFcFirmware(osdString: String): FcFirmware {
            return when (osdString) {
                "BTFL" -> BETAFLIGHT
                "INAV" -> INAV
                "ARDU" -> ARDUPILOT
                "KISS" -> KISS
                "ULTR" -> KISSULTRA
                else -> UNKNOWN
            }
        }


    }
}

data class Frame(val timestampMillis: Long, val glyphs: List<Glyph>)

data class Glyph(val index: Int, val position: Position)
data class Position(val x: Int, val y: Int)

