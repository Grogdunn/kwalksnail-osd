package dev.grogdunn.kwalksnailosd.srt

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SrtCueLookupTest {
    @Test
    fun cueLookupMatchesFindLastSemantics() {
        assertNull(srtCueAt(emptyList(), 0.0))

        val only = cue(50, 1)
        assertEquals(only, srtCueAt(listOf(only), -10.0))
        assertEquals(only, srtCueAt(listOf(only), 50.0))
        assertEquals(only, srtCueAt(listOf(only), 51.0))

        val first = cue(0, 1)
        val middle = cue(100, 2)
        val middleDuplicate = cue(100, 3)
        val last = cue(250, 4)
        val cues = listOf(first, middle, middleDuplicate, last)

        assertEquals(first, srtCueAt(cues, -1.0))
        assertEquals(first, srtCueAt(cues, 0.0))
        assertEquals(first, srtCueAt(cues, 50.0))
        assertEquals(first, srtCueAt(cues, 100.0))
        assertEquals(middleDuplicate, srtCueAt(cues, 100.1))
        assertEquals(middleDuplicate, srtCueAt(cues, 250.0))
        assertEquals(last, srtCueAt(cues, 250.1))
        assertEquals(last, srtCueAt(cues, 10_000.0))
    }

    @Test
    fun largeSortedListMatchesFindLastForRandomAndEdgeTimes() {
        val cues = List(10_000) { index -> cue(index * 10L, index) }
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
        for (time in times) {
            assertEquals(referenceSrtCueAt(cues, time), srtCueAt(cues, time), "time=$time")
        }
    }

    private fun cue(startMillis: Long, marker: Int) = SrtCue(startMillis, startMillis + marker, data = null)

    private fun referenceSrtCueAt(cues: List<SrtCue>, videoTimeMillis: Double): SrtCue? {
        if (cues.isEmpty()) return null
        return cues.findLast { it.startMillis < videoTimeMillis } ?: cues.first()
    }
}
