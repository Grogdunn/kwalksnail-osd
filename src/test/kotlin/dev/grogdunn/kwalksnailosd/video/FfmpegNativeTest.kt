package dev.grogdunn.kwalksnailosd.video

import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.Loader
import kotlin.test.Test
import kotlin.test.assertTrue

class FfmpegNativeTest {
    @Test
    fun loadsFfmpegBuiltForThisOperatingSystem() {
        val loaded = Loader.load(avutil::class.java)
        assertTrue(loaded.isNotBlank(), "JavaCV did not load an ffmpeg library")
    }
}
