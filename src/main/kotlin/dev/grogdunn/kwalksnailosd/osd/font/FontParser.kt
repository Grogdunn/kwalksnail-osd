package dev.grogdunn.kwalksnailosd.osd.font

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.error_font_decode
import dev.grogdunn.kwalksnailosd.uiText
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

class FontParser {
    fun parse(selectedFont: Path): Font {
        val fontImage = ImageIO.read(selectedFont.toFile())
            ?: throw IllegalArgumentException(uiText(Res.string.error_font_decode, selectedFont))

        val (characterSize, fontType, characterCount) = detectDimensions(fontImage.width, fontImage.height)
        val characters = splitCharacters(fontImage, characterSize, fontType, characterCount)

        return Font(
            filePath = selectedFont,
            characterCount = characterCount,
            characterSize = characterSize,
            fontType = fontType,
            characters = characters
        )
    }

    private fun splitCharacters(
        fontImage: BufferedImage,
        characterSize: CharacterSize,
        fontType: FontType,
        characterCount: Int
    ): List<BufferedImage> {
        val pages = fontType.pages
        val charWidth = characterSize.width
        val charHeight = characterSize.height

        val characters = ArrayList<BufferedImage>(characterCount * pages)

        for (pageIdx in 0 until pages) {
            val x = pageIdx * charWidth
            for (charIdx in 0 until characterCount) {
                val y = charIdx * charHeight
                characters.add(fontImage.getSubimage(x, y, charWidth, charHeight))
            }
        }

        return characters
    }

    fun detectDimensions(width: Int, height: Int): FontDimensions {
        var inexactPageHeight = false
        for (type in FontType.entries) {
            for (size in CharacterSize.entries) {
                if (width != size.width * type.pages) continue
                val tileCount = height / size.height
                if (tileCount <= 0) continue
                if (height % size.height != 0) {
                    if (tileCount % TILES_PER_PAGE == 0) inexactPageHeight = true
                    continue
                }
                val usable = tileCount / TILES_PER_PAGE * TILES_PER_PAGE
                if (usable == 0) continue
                return FontDimensions(size, type, usable)
            }
        }
        if (inexactPageHeight) throw FontFileException.InvalidFontFileHeight(height)
        throw FontFileException.InvalidFontFileWidth(width)
    }

    private companion object {
        const val TILES_PER_PAGE = 256
    }
}