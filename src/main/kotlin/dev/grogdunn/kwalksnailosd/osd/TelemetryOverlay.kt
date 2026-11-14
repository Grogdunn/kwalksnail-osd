package dev.grogdunn.kwalksnailosd.osd

import dev.grogdunn.kwalksnailosd.srt.TelemetrySample
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Which telemetry fields to paint, and where.
 * [xPercent] and [yPercent] are the top-left of the line, as a percentage of the frame.
 * [scale] is the font size at 1080p; other frame heights scale with it.
 */
data class TelemetryOverlayOptions(
    val xPercent: Float = DEFAULT_X,
    val yPercent: Float = DEFAULT_Y,
    val scale: Float = DEFAULT_SCALE,
    val showSignal: Boolean = true,
    val showChannel: Boolean = true,
    val showLatency: Boolean = true,
    val showBitrate: Boolean = true,
    val showGoggleVoltage: Boolean = false,
    val showVtxVoltage: Boolean = false,
    val showGoggleTemperature: Boolean = false,
    val showVtxTemperature: Boolean = false,
    val showFlightTime: Boolean = false,
    val showDistance: Boolean = true,
) {
    companion object {
        const val DEFAULT_X = 1.5f
        const val DEFAULT_Y = 95f
        const val DEFAULT_SCALE = 35f
    }
}

/**
 * Draws one telemetry line onto a video frame.
 * Preview and export both call [draw] for the sample that matches the frame timestamp.
 */
class TelemetryOverlay {
    fun text(sample: TelemetrySample, options: TelemetryOverlayOptions): String {
        val parts = mutableListOf<String>()
        if (options.showFlightTime) {
            sample.flightTimeSeconds?.let { seconds ->
                parts += "Time:%d:%02d".format(Locale.US, seconds / 60, seconds % 60)
            }
        }
        if (options.showVtxVoltage) {
            sample.vtxVoltage?.let { parts += "SBat:%4.1fV".format(Locale.US, it) }
        }
        if (options.showGoggleVoltage) {
            sample.goggleVoltage?.let { parts += "GBat:%4.1fV".format(Locale.US, it) }
        }
        if (options.showVtxTemperature) {
            sample.vtxTemperatureCelsius?.let { parts += "STemp:%.0f°C".format(Locale.US, it) }
        }
        if (options.showGoggleTemperature) {
            sample.goggleTemperatureCelsius?.let { parts += "GTemp:%.0f°C".format(Locale.US, it) }
        }
        if (options.showSignal) {
            sample.signal?.let { parts += "Signal:%d".format(Locale.US, it) }
        }
        if (options.showChannel) {
            sample.channel?.let { parts += "CH:%d".format(Locale.US, it) }
        }
        if (options.showLatency) {
            sample.latencyMillis?.let { parts += "Latency:%3dms".format(Locale.US, it) }
        }
        if (options.showBitrate) {
            sample.bitrateMbps?.let { parts += "Bitrate:%4.1fMbps".format(Locale.US, it) }
        }
        if (options.showDistance) {
            sample.distanceMeters?.let { distance ->
                parts += if (distance > 999) {
                    "Distance:%.2fkm".format(Locale.US, distance / 1000.0)
                } else {
                    "Distance:%3dm".format(Locale.US, distance)
                }
            }
        }
        return parts.joinToString("  ")
    }

    fun draw(image: BufferedImage, sample: TelemetrySample?, options: TelemetryOverlayOptions) {
        if (sample == null) return
        val line = text(sample, options)
        if (line.isEmpty() || image.width <= 0 || image.height <= 0) return

        val fontSize = (options.scale / REFERENCE_FRAME_HEIGHT * image.height).roundToInt().coerceAtLeast(1)
        val x = (options.xPercent / 100f * image.width).roundToInt()
        val y = (options.yPercent / 100f * image.height).roundToInt()
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, fontSize)
            val baseline = y + graphics.fontMetrics.ascent
            graphics.color = Color(0, 0, 0, 200)
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    graphics.drawString(line, x + dx, baseline + dy)
                }
            }
            graphics.color = Color(240, 240, 240)
            graphics.drawString(line, x, baseline)
        } finally {
            graphics.dispose()
        }
    }

    private companion object {
        const val REFERENCE_FRAME_HEIGHT = 1080f
    }
}
