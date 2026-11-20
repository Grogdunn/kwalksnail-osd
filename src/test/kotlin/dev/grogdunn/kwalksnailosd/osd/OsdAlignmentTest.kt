package dev.grogdunn.kwalksnailosd.osd

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OsdAlignmentTest {
    @Test
    fun frameLookupMatchesFindLastSemantics() {
        assertNull(osdFrameAt(emptyList(), videoTimeMillis = 0.0, speedFactor = 1.0))

        val only = frame(50, 1)
        assertEquals(only, osdFrameAt(listOf(only), videoTimeMillis = -10.0, speedFactor = 1.0))
        assertEquals(only, osdFrameAt(listOf(only), videoTimeMillis = 50.0, speedFactor = 1.0))
        assertEquals(only, osdFrameAt(listOf(only), videoTimeMillis = 51.0, speedFactor = 1.0))

        val first = frame(0, 1)
        val middle = frame(100, 2)
        val middleDuplicate = frame(100, 3)
        val last = frame(250, 4)
        val frames = listOf(first, middle, middleDuplicate, last)

        assertEquals(first, osdFrameAt(frames, videoTimeMillis = -1.0, speedFactor = 1.0))
        assertEquals(first, osdFrameAt(frames, videoTimeMillis = 0.0, speedFactor = 1.0))
        assertEquals(first, osdFrameAt(frames, videoTimeMillis = 50.0, speedFactor = 1.0))
        assertEquals(first, osdFrameAt(frames, videoTimeMillis = 100.0, speedFactor = 1.0))
        assertEquals(middleDuplicate, osdFrameAt(frames, videoTimeMillis = 100.1, speedFactor = 1.0))
        assertEquals(middleDuplicate, osdFrameAt(frames, videoTimeMillis = 250.0, speedFactor = 1.0))
        assertEquals(last, osdFrameAt(frames, videoTimeMillis = 250.1, speedFactor = 1.0))
        assertEquals(last, osdFrameAt(frames, videoTimeMillis = 10_000.0, speedFactor = 1.0))

        assertEquals(first, osdFrameAt(frames, videoTimeMillis = 200.0, speedFactor = 2.0))
        assertEquals(middleDuplicate, osdFrameAt(frames, videoTimeMillis = 200.2, speedFactor = 2.0))
        assertEquals(middleDuplicate, osdFrameAt(frames, videoTimeMillis = 500.0, speedFactor = 2.0))
        assertEquals(last, osdFrameAt(frames, videoTimeMillis = 500.2, speedFactor = 2.0))
        assertEquals(
            osdFrameAt(frames, videoTimeMillis = 100.0, speedFactor = 1.0),
            osdFrameAt(frames, videoTimeMillis = 100.0, speedFactor = 0.0),
        )
        assertEquals(
            osdFrameAt(frames, videoTimeMillis = 250.1, speedFactor = 1.0),
            osdFrameAt(frames, videoTimeMillis = 250.1, speedFactor = -4.0),
        )
    }

    @Test
    fun largeSortedListMatchesFindLastForRandomAndEdgeTimes() {
        val frames = List(10_000) { index -> frame(index * 10L, index) }
        val random = Random(20_261_120)
        val times = buildList {
            add(Double.NEGATIVE_INFINITY)
            add(-1.0)
            add(0.0)
            add(0.5)
            add(10.0)
            add(99_990.0)
            add(100_000.0)
            add(1.0e12)
            add(Double.POSITIVE_INFINITY)
            add(Double.NaN)
            repeat(200) { add(random.nextDouble(-1_000.0, 120_000.0)) }
            repeat(40) { add(random.nextInt(10_000) * 10.0) }
        }
        for (speed in doubleArrayOf(1.0, 2.0, 0.0, -1.0)) {
            for (time in times) {
                assertEquals(
                    referenceOsdFrameAt(frames, time, speed),
                    osdFrameAt(frames, time, speed),
                    "time=$time speed=$speed",
                )
            }
        }
    }

    private fun frame(timestampMillis: Long, glyph: Int) = Frame(timestampMillis, listOf(Glyph(glyph, Position(glyph % 53, 0))))

    private fun referenceOsdFrameAt(frames: List<Frame>, videoTimeMillis: Double, speedFactor: Double): Frame? {
        if (frames.isEmpty()) return null
        val factor = if (speedFactor <= 0.0) 1.0 else speedFactor
        val scaledVideoTime = videoTimeMillis / factor
        return frames.findLast { it.timestampMillis < scaledVideoTime } ?: frames.first()
    }
}
