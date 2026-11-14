package dev.grogdunn.kwalksnailosd.srt

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

object SrtOverlay {
    fun draw(image: BufferedImage, data: SrtData, options: SrtOptions) {
        val text = srtLabel(data, options)
        if (text.isEmpty()) return
        val graphics = image.createGraphics()
        try {
            val fontSize = (options.scale / 1080f * image.height).coerceAtLeast(1f)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 12).deriveFont(fontSize)
            // Il tool originale usa lo stesso grigio con alpha 10, quasi invisibile
            // (backend/src/overlay/srt.rs). Qui il testo è opaco. y Java è la baseline:
            // si somma l'ascent così il bordo superiore resta sulla percentuale scelta.
            graphics.color = Color(240, 240, 240)
            val x = (options.xPercent / 100f * image.width).toInt()
            val baseline = (options.yPercent / 100f * image.height).toInt() + graphics.fontMetrics.ascent
            graphics.drawString(text, x, baseline)
        } finally {
            graphics.dispose()
        }
    }
}
