package dev.grogdunn.kwalksnailosd

import dev.grogdunn.kwalksnailosd.video.OsdOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class NudgeOffsetTest {
    @Test
    fun nudgesWithinTheGlobalSliderRange() {
        assertEquals(OsdOffset(10, -5), nudgeOffset(OsdOffset(0, 0), 10, -5))
        assertEquals(OsdOffset(OSD_OFFSET_MIN, OSD_OFFSET_MAX), nudgeOffset(OsdOffset(-190, 695), -20, 20))
        assertEquals(OsdOffset(OSD_OFFSET_MAX, OSD_OFFSET_MIN), nudgeOffset(OsdOffset(700, -200), 10, -1))
    }
}
