package dev.grogdunn.kwalksnailosd.video

import org.bytedeco.javacv.FrameGrabber
import java.nio.file.Path
import java.time.Duration

class VideoParser {

    fun getVideoInfo(mp4: Path): VideoInfo {
        return FrameGrabber.createDefault(mp4.toFile()).use {
            it.start()
            VideoInfo(it.imageHeight, it.imageWidth, it.videoCodecName, Duration.ofMillis(it.lengthInTime / 1000))
        }
    }
}

