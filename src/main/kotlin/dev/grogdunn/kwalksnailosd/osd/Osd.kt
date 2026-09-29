package dev.grogdunn.kwalksnailosd.osd

import java.time.Duration

data class Osd(val fcFirmware: FcFirmware, val frames: List<Frame>, val duration: Duration)