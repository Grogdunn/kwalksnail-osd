package dev.grogdunn.kwalksnailosd.osd

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

private const val HEADER_BYTES = 40
private const val FC_TYPE_BYTES = 4
private const val TIMESTAMP_BYTES = 4
private const val BYTES_PER_GLYPH = 2
private const val GRID_WIDTH = 53
private const val GRID_HEIGHT = 20
private const val FRAME_BYTES = TIMESTAMP_BYTES + GRID_WIDTH * GRID_HEIGHT * BYTES_PER_GLYPH

class OsdParser {
    fun parse(selectedOsd: Path): Osd {
        Files.newInputStream(selectedOsd).use { input ->
            val header = input.readNBytes(HEADER_BYTES)
            if (header.size != HEADER_BYTES) {
                throw IllegalArgumentException("OSD header is incomplete: expected $HEADER_BYTES bytes, got ${header.size}")
            }
            val fcBytes = header.take(FC_TYPE_BYTES).toByteArray()
            val frames = mutableListOf<Frame>()
            while (true) {
                val frameBytes = input.readNBytes(FRAME_BYTES)
                if (frameBytes.isEmpty()) break
                if (frameBytes.size != FRAME_BYTES) {
                    throw IllegalArgumentException("OSD frame is incomplete: expected $FRAME_BYTES bytes, got ${frameBytes.size}")
                }
                frames.add(parseFrame(frameBytes))
            }
            return Osd(FcFirmware.getFcFirmware(fcBytes), frames)
        }
    }

    fun parseFrame(frameBytes: ByteArray): Frame {
        require(frameBytes.size == FRAME_BYTES) {
            "OSD frame must be $FRAME_BYTES bytes long, but was ${frameBytes.size}"
        }

        val buffer = ByteBuffer.wrap(frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        val timestamp = Integer.toUnsignedLong(buffer.int)
        val glyphs = ByteArray(buffer.remaining()).also(buffer::get).toList()
        return Frame(timestamp, glyphs)
    }

    data class Osd(val fcFirmware: FcFirmware, val frames: List<Frame>)
}

data class Frame(val timestamp: Long, val glyphs: List<Byte>)


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