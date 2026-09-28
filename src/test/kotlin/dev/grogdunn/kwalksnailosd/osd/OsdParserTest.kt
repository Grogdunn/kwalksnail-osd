package dev.grogdunn.kwalksnailosd.osd

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val TEST_FRAME_BYTES = 2124
private const val TEST_TIMESTAMP_BYTES = 4

class OsdParserTest {
    @Test
    fun parseFrameReadsUnsignedLittleEndianTimestampAndPayload() {
        val frameBytes = ByteArray(TEST_FRAME_BYTES)
        val payload = ByteArray(TEST_FRAME_BYTES - TEST_TIMESTAMP_BYTES) { it.toByte() }
        ByteBuffer.wrap(frameBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(-1)
            .put(payload)

        val frame = OsdParser().parseFrame(frameBytes)

        assertEquals(0xFFFF_FFFFL, frame.timestamp)
        assertContentEquals(payload.toList(), frame.glyphs)
    }

    @Test
    fun parseReturnsFirmwareAndAllFrames() {
        val header = ByteArray(40)
        "BTFL".encodeToByteArray().copyInto(header)
        val frameBytes = createFrameBytes()

        val osd = parseFromTemporaryFile(header + frameBytes)

        assertEquals(FcFirmware.BETAFLIGHT, osd.fcFirmware)
        assertEquals(1, osd.frames.size)
        assertEquals(1234L, osd.frames.single().timestamp)
        assertEquals(TEST_FRAME_BYTES - TEST_TIMESTAMP_BYTES, osd.frames.single().glyphs.size)
    }

    @Test
    fun parseRejectsIncompleteHeader() {
        assertFailsWith<IllegalArgumentException> {
            parseFromTemporaryFile(ByteArray(3))
        }
    }

    @Test
    fun parseRejectsIncompleteFrame() {
        val header = ByteArray(40)
        "BTFL".encodeToByteArray().copyInto(header)

        assertFailsWith<IllegalArgumentException> {
            parseFromTemporaryFile(header + byteArrayOf(1, 2, 3))
        }
    }

    private fun createFrameBytes(): ByteArray = ByteArray(TEST_FRAME_BYTES).also { bytes ->
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(1234)
    }

    private fun parseFromTemporaryFile(bytes: ByteArray): OsdParser.Osd {
        val path = Files.createTempFile("osd-parser-test", ".osd")
        return try {
            Files.write(path, bytes)
            OsdParser().parse(path)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
