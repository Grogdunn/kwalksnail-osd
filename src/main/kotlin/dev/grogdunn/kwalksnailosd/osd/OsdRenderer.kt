package dev.grogdunn.kwalksnailosd.osd

import dev.grogdunn.kwalksnailosd.osd.font.CharacterSize
import dev.grogdunn.kwalksnailosd.osd.font.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

class OsdRenderer {

    fun characterSizeForFrameHeight(frameHeight: Int): CharacterSize =
        CharacterSize.entries.firstOrNull { it.height * GRID_ROWS == frameHeight }
            ?: CharacterSize.LARGE

    fun renderInPlace(
        image: BufferedImage,
        frame: Frame,
        font: Font,
        offsetX: Int = 0,
        offsetY: Int = 0,
        nearestNeighbor: Boolean = false,
    ) {
        val characterSize = characterSizeForFrameHeight(image.height)
        val graphics = image.createGraphics()
        try {
            if (nearestNeighbor) {
                graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
                )
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            }
            frame.glyphs.forEach { glyph ->
                if (nearestNeighbor) {
                    val character = font.getCharacter(glyph.index) ?: return@forEach
                    graphics.drawImage(
                        character,
                        glyph.position.x * characterSize.width + offsetX,
                        glyph.position.y * characterSize.height + offsetY,
                        characterSize.width,
                        characterSize.height,
                        null,
                    )
                } else {
                    val character = font.getCharacter(glyph.index, characterSize) ?: return@forEach
                    graphics.drawImage(
                        character,
                        glyph.position.x * character.width + offsetX,
                        glyph.position.y * character.height + offsetY,
                        null,
                    )
                }
            }
        } finally {
            graphics.dispose()
        }
    }

    private companion object {
        const val GRID_ROWS = 20
    }
}