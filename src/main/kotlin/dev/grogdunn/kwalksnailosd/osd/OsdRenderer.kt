package dev.grogdunn.kwalksnailosd.osd

import dev.grogdunn.kwalksnailosd.osd.font.CharacterSize
import dev.grogdunn.kwalksnailosd.osd.font.Font
import java.awt.image.BufferedImage

class OsdRenderer {

    fun characterSizeForFrameHeight(frameHeight: Int): CharacterSize =
        CharacterSize.entries.firstOrNull { it.height * GRID_ROWS == frameHeight }
            ?: CharacterSize.LARGE

    fun renderInPlace(image: BufferedImage, frame: Frame, font: Font) {
        val characterSize = characterSizeForFrameHeight(image.height)
        val graphics = image.createGraphics()
        try {
            frame.glyphs.forEach { glyph ->
                val character = font.getCharacter(glyph.index, characterSize) ?: return@forEach
                graphics.drawImage(
                    character,
                    glyph.position.x * character.width,
                    glyph.position.y * character.height,
                    null,
                )
            }
        } finally {
            graphics.dispose()
        }
    }

    private companion object {
        const val GRID_ROWS = 20
    }
}