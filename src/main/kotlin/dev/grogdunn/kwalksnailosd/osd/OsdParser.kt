package dev.grogdunn.kwalksnailosd.osd

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.error_osd_frame
import dev.grogdunn.kwalksnailosd.generated.resources.error_osd_frame_length
import dev.grogdunn.kwalksnailosd.generated.resources.error_osd_header
import dev.grogdunn.kwalksnailosd.uiText
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration


class OsdParser {
    private val HEADER_BYTES = 40
    private val FC_TYPE_BYTES = 4
    private val TIMESTAMP_BYTES = 4
    private val GLYPH_BYTES = 2
    private val GRID_WIDTH = 53
    private val GRID_HEIGHT = 20
    private val FRAME_BYTES = TIMESTAMP_BYTES + GRID_WIDTH * GRID_HEIGHT * GLYPH_BYTES

    fun parse(selectedOsd: Path): Osd {
        Files.newInputStream(selectedOsd).use { input ->
            val header = input.readNBytes(HEADER_BYTES)
            if (header.size != HEADER_BYTES) {
                throw IllegalArgumentException(uiText(Res.string.error_osd_header, HEADER_BYTES, header.size))
            }
            val fcBytes = header.take(FC_TYPE_BYTES).toByteArray()
            val fcFirmware = FcFirmware.getFcFirmware(fcBytes)
            val frames = mutableListOf<Frame>()
            while (true) {
                val frameBytes = input.readNBytes(FRAME_BYTES)
                if (frameBytes.isEmpty()) break
                if (frameBytes.size != FRAME_BYTES) {
                    throw IllegalArgumentException(uiText(Res.string.error_osd_frame, FRAME_BYTES, frameBytes.size))
                }
                frames.add(parseFrame(frameBytes))
            }

            // Sorted so osdFrameAt can binary-search. Valid files are already non-decreasing; a malformed file may not be.
            frames.sortBy { it.timestampMillis }
            val frameIntervalMillis = (frames.last().timestampMillis - frames.first().timestampMillis).toDouble() / (frames.size - 1).toDouble()
            val duration = Duration.ofMillis(frames.last().timestampMillis) + Duration.ofMillis(frameIntervalMillis.toLong())
            return Osd(fcFirmware, frames, duration)
        }
    }

    private fun parseFrame(frameBytes: ByteArray): Frame {
        require(frameBytes.size == FRAME_BYTES) {
            uiText(Res.string.error_osd_frame_length, FRAME_BYTES, frameBytes.size)
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

