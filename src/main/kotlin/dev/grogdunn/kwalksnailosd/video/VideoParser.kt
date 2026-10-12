package dev.grogdunn.kwalksnailosd.video

import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.time.Duration

class VideoParser : AutoCloseable {

    private val converter = Java2DFrameConverter()
    private var grabber: FrameGrabber? = null
    private var currentPath: Path? = null

    @Synchronized
    fun open(mp4: Path): VideoInfo {
        if (currentPath == mp4 && grabber != null) {
            val g = grabber!!
            return VideoInfo(
                height = g.imageHeight,
                width = g.imageWidth,
                codec = g.videoCodecName ?: "unknown",
                duration = Duration.ofMillis(g.lengthInTime / 1000),
                frameRate = g.frameRate,
                frameNumber = g.lengthInFrames
            )
        }

        closeGrabber()

        val newGrabber = FrameGrabber.createDefault(mp4.toFile())
        newGrabber.start()
        grabber = newGrabber
        currentPath = mp4

        return VideoInfo(
            height = newGrabber.imageHeight,
            width = newGrabber.imageWidth,
            codec = newGrabber.videoCodecName ?: "unknown",
            duration = Duration.ofMillis(newGrabber.lengthInTime / 1000),
            frameRate = newGrabber.frameRate,
            frameNumber = newGrabber.lengthInFrames
        )
    }

    @Synchronized
    fun getVideoInfo(mp4: Path): VideoInfo {
        return open(mp4)
    }

    @Synchronized
    fun getVideoFrame(frameNumber: Int): BufferedImage? {
        val g = grabber ?: return null
        val safeFrame = frameNumber.coerceIn(0, (g.lengthInFrames - 1).coerceAtLeast(0))
        g.frameNumber = safeFrame
        val frame = g.grab() ?: return null
        return converter.convert(frame)
    }

    @Synchronized
    fun getVideoFrame(mp4: Path, frameNumber: Int): BufferedImage? {
        if (currentPath != mp4 || grabber == null) {
            open(mp4)
        }
        return getVideoFrame(frameNumber)
    }

    @Synchronized
    private fun closeGrabber() {
        val g = grabber
        grabber = null
        currentPath = null
        if (g != null) {
            try {
                g.stop()
            } catch (_: Exception) {}
            try {
                g.release()
            } catch (_: Exception) {}
            try {
                g.close()
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    override fun close() {
        closeGrabber()
        try {
            converter.close()
        } catch (_: Exception) {}
    }
}

