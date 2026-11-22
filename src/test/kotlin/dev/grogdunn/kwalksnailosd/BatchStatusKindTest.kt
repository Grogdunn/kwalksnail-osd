package dev.grogdunn.kwalksnailosd

import dev.grogdunn.kwalksnailosd.clip.BatchJobState
import dev.grogdunn.kwalksnailosd.video.BurnResult
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BatchStatusKindTest {
    @Test
    fun mapsEachJobState() {
        assertNull(batchStatusKind(null))
        assertEquals(BatchStatusKind.Pending, batchStatusKind(BatchJobState.Pending))
        assertEquals(BatchStatusKind.Cancelled, batchStatusKind(BatchJobState.Cancelled))
        assertEquals(
            BatchStatusKind.Running(fraction = 0.335f, percent = 34),
            batchStatusKind(BatchJobState.Running(0.335f, "burning")),
        )
        assertEquals(
            BatchStatusKind.Running(fraction = 1.4f, percent = 100),
            batchStatusKind(BatchJobState.Running(1.4f, "over")),
        )
        assertEquals(
            BatchStatusKind.Failed("no osd"),
            batchStatusKind(BatchJobState.Failed("no osd")),
        )
        val output = Path.of("out", "clip_with_osd.mp4")
        val done = batchStatusKind(
            BatchJobState.Done(BurnResult(output, "libx264", false, 10, false)),
        )
        assertEquals(BatchStatusKind.Done("clip_with_osd.mp4"), done)
    }
}
