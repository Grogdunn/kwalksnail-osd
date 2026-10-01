package dev.grogdunn.kwalksnailosd.osd

import dev.grogdunn.kwalksnailosd.osd.font.CharacterSize
import dev.grogdunn.kwalksnailosd.osd.font.Font
import java.awt.image.BufferedImage

class OsdRenderer {

    fun renderInPlace(image: BufferedImage, frame: Frame, font: Font) {
        val graphics = image.createGraphics()
        frame.glyphs.forEach { glyph ->
            font.getCharacter(glyph.index, CharacterSize.LARGE)?.let { character ->
                graphics.apply {
                    drawImage(character, glyph.position.x * character.width, glyph.position.y * character.height, null)
                }
            }
        }
        graphics.dispose()
    }
}