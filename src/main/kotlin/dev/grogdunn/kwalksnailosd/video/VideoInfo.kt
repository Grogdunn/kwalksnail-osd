package dev.grogdunn.kwalksnailosd.video

import java.time.Duration

data class VideoInfo(val height: Int, val width: Int, val codec: String, val duration: Duration, val frameRate: Double, val frameNumber: Int)