package dev.grogdunn.kwalksnailosd.video

import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.time.Duration

class VideoParser {

    private val converter = Java2DFrameConverter()
    fun getVideoInfo(mp4: Path): VideoInfo {
        return FrameGrabber.createDefault(mp4.toFile()).use {
            it.start()
            VideoInfo(it.imageHeight, it.imageWidth, it.videoCodecName, Duration.ofMillis(it.lengthInTime / 1000), it.frameRate, it.lengthInFrames)
        }
    }

    fun getVideoFrame(mp4: Path, frameNumber: Int): BufferedImage {
        return FrameGrabber.createDefault(mp4.toFile()).use {
            it.start()
            it.frameNumber = frameNumber
            converter.convert(it.grab())
        }
    }
}

