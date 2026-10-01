package dev.grogdunn.kwalksnailosd.osd.font

import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

class FontParser {
    fun parse(selectedFont: Path): Font {
        val fontImage = ImageIO.read(selectedFont.toFile())
            ?: throw IllegalArgumentException("Failed to decode image from path: $selectedFont")

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
        for (type in FontType.entries) {
            for (size in CharacterSize.entries) {
                if (width == size.width * type.pages && ((height - 1) / size.height) % 256 == 0) {
                    if (height % size.height != 0) {
                        throw FontFileException.InvalidFontFileHeight(height)
                    }
                    val charactersCount = height / size.height
                    return FontDimensions(size, type, charactersCount)
                }
            }
        }
        throw FontFileException.InvalidFontFileWidth(width)
    }
}