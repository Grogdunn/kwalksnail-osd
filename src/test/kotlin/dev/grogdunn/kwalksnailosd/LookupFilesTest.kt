package dev.grogdunn.kwalksnailosd

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LookupFilesTest {
    @Test
    fun findsTheSidecarSrtWithTheSameNameRegardlessOfCase() {
        val folder = createTempDirectory("lookup-files")
        val video = folder.resolve("AvatarG0001.MP4")
        val osd = folder.resolve("avatarg0001.osd")
        val srt = folder.resolve("AvatarG0001.SRT")
        Files.write(video, byteArrayOf())
        Files.write(osd, byteArrayOf())
        Files.write(srt, byteArrayOf())

        val found = lookupFiles(video)

        assertEquals(video, found.mp4Path)
        assertEquals(osd, found.osdPath)
        assertEquals(srt, found.srtPath)
    }

    @Test
    fun leavesTheSrtEmptyWhenTheVideoHasNoSidecar() {
        val folder = createTempDirectory("lookup-files-empty")
        val video = folder.resolve("solo.mp4")
        Files.write(video, byteArrayOf())

        val found = lookupFiles(video)

        assertEquals(video, found.mp4Path)
        assertNull(found.osdPath)
        assertNull(found.srtPath)
    }
}
