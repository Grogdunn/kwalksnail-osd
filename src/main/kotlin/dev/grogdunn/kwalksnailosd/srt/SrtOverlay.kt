package dev.grogdunn.kwalksnailosd.srt

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

object SrtOverlay {
    fun draw(image: BufferedImage, data: SrtData, options: SrtOptions, antialias: Boolean = true) {
        val text = srtLabel(data, options)
        if (text.isEmpty()) return
        val graphics = image.createGraphics()
        try {
            val fontSize = (options.scale / 1080f * image.height).coerceAtLeast(1f)
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 12).deriveFont(fontSize)
            // Il tool originale usa lo stesso grigio con alpha 10, quasi invisibile
            // (backend/src/overlay/srt.rs). Qui il testo è opaco. y Java è la baseline:
            // si somma l'ascent così il bordo superiore resta sulla percentuale scelta.
            val x = (options.xPercent / 100f * image.width).toInt()
            val baseline = (options.yPercent / 100f * image.height).toInt() + graphics.fontMetrics.ascent
            if (!antialias) {
                // macOS still antialiases when TEXT_ANTIALIAS_OFF is set, which leaves
                // key-colored fringe. Stamp only pixels whose coverage is at least half.
                stampHardText(image, text, graphics.font, x, baseline)
            } else {
                graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                )
                graphics.color = INK
                graphics.drawString(text, x, baseline)
            }
        } finally {
            graphics.dispose()
        }
    }

    private fun stampHardText(image: BufferedImage, text: String, font: Font, x: Int, baseline: Int) {
        val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        val metrics = try {
            probe.font = font
            probe.fontMetrics
        } finally {
            probe.dispose()
        }
        val maskWidth = (metrics.stringWidth(text) + PAD * 2).coerceAtLeast(1)
        val maskHeight = (metrics.ascent + metrics.descent + PAD * 2).coerceAtLeast(1)
        val mask = BufferedImage(maskWidth, maskHeight, BufferedImage.TYPE_INT_ARGB)
        val maskGraphics = mask.createGraphics()
        try {
            maskGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
            maskGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            maskGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF)
            maskGraphics.font = font
            maskGraphics.color = Color.WHITE
            maskGraphics.drawString(text, PAD, PAD + metrics.ascent)
        } finally {
            maskGraphics.dispose()
        }
        val originX = x - PAD
        val originY = baseline - metrics.ascent - PAD
        for (maskY in 0 until maskHeight) {
            val targetY = originY + maskY
            if (targetY !in 0 until image.height) continue
            for (maskX in 0 until maskWidth) {
                val targetX = originX + maskX
                if (targetX !in 0 until image.width) continue
                if ((mask.getRGB(maskX, maskY) ushr 24) >= COVERAGE_THRESHOLD) {
                    image.setRGB(targetX, targetY, INK.rgb)
                }
            }
        }
    }

    private val INK = Color(240, 240, 240)
    private const val PAD = 4
    private const val COVERAGE_THRESHOLD = 128
}
