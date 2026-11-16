package dev.grogdunn.kwalksnailosd.osd.font

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FontParserTest {
    private val parser = FontParser()

    @Test
    fun dimensionsCoverWalksnailAtlasLayouts() {
        val cases = listOf(
            Expected(18, 6_912, CharacterSize.RACE, FontType.STANDARD, 256),
            Expected(18, 6_912 + 27, CharacterSize.RACE, FontType.STANDARD, 256),
            Expected(18, 13_824, CharacterSize.RACE, FontType.STANDARD, 512),
            Expected(24, 9_216, CharacterSize.SMALL, FontType.STANDARD, 256),
            Expected(24, 18_432, CharacterSize.SMALL, FontType.STANDARD, 512),
            Expected(24, 18_432 + 36, CharacterSize.SMALL, FontType.STANDARD, 512),
            Expected(36, 13_824, CharacterSize.LARGE, FontType.STANDARD, 256),
            Expected(36, 13_878, CharacterSize.LARGE, FontType.STANDARD, 256),
            Expected(36, 27_648, CharacterSize.LARGE, FontType.STANDARD, 512),
            Expected(48, 18_432, CharacterSize.XLARGE, FontType.STANDARD, 256),
            Expected(48, 18_432 + 72, CharacterSize.XLARGE, FontType.STANDARD, 256),
            Expected(72, 27_648, CharacterSize.ULTRA, FontType.STANDARD, 256),
            Expected(72, 27_648 + 108, CharacterSize.ULTRA, FontType.STANDARD, 256),
            Expected(36, 6_912, CharacterSize.RACE, FontType.TWO_PAGES, 256),
            Expected(36, 6_912 + 27, CharacterSize.RACE, FontType.TWO_PAGES, 256),
            Expected(48, 9_216, CharacterSize.SMALL, FontType.TWO_PAGES, 256),
            Expected(72, 13_824, CharacterSize.LARGE, FontType.TWO_PAGES, 256),
            Expected(54, 6_912, CharacterSize.RACE, FontType.THREE_PAGES, 256),
            Expected(108, 13_824, CharacterSize.LARGE, FontType.THREE_PAGES, 256),
            Expected(72, 6_912, CharacterSize.RACE, FontType.FOUR_COLOR, 256),
            Expected(96, 9_216, CharacterSize.SMALL, FontType.FOUR_COLOR, 256),
            Expected(144, 13_824, CharacterSize.LARGE, FontType.FOUR_COLOR, 256),
            Expected(144, 13_824 + 54, CharacterSize.LARGE, FontType.FOUR_COLOR, 256),
        )

        for (case in cases) {
            assertEquals(
                FontDimensions(case.size, case.type, case.count),
                parser.detectDimensions(case.width, case.height),
                "${case.width}x${case.height}",
            )
        }
    }

    @Test
    fun dimensionsRejectUnknownWidthInexactHeightAndNonWalksnailTiles() {
        assertIs<FontFileException.InvalidFontFileWidth>(
            assertFailsWith<FontFileException> { parser.detectDimensions(37, 13_824) }
        )
        assertIs<FontFileException.InvalidFontFileWidth>(
            assertFailsWith<FontFileException> { parser.detectDimensions(36, 5_400) }
        )
        assertIs<FontFileException.InvalidFontFileWidth>(
            assertFailsWith<FontFileException> { parser.detectDimensions(12, 18 * 256) }
        )
        assertIs<FontFileException.InvalidFontFileHeight>(
            assertFailsWith<FontFileException> { parser.detectDimensions(36, 13_825) }
        )
    }

    @Test
    fun attachedFontsMapEachGlyphIndexToItsCell() {
        assertColumnFont(
            name = "font_24x9216.png",
            size = CharacterSize.SMALL,
            rowsInFile = 256,
        )
        assertColumnFont(
            name = "font_36x13878.png",
            size = CharacterSize.LARGE,
            rowsInFile = 257,
        )
    }

    @Test
    fun trailingNonEmptyCellsStayOutOfTheGlyphIndex() {
        val directory = createTempDirectory("font-trailing")
        try {
            val path = directory.resolve("large-extra.png")
            val size = CharacterSize.LARGE
            val rows = 257
            val image = BufferedImage(size.width, size.height * rows, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(1, 10 * size.height + 2, OPAQUE_RED)
            image.setRGB(2, 255 * size.height + 3, OPAQUE_BLUE)
            image.setRGB(0, 256 * size.height, OPAQUE_GREEN)
            ImageIO.write(image, "png", path.toFile())

            val font = parser.parse(path)

            assertEquals(CharacterSize.LARGE, font.characterSize)
            assertEquals(FontType.STANDARD, font.fontType)
            assertEquals(256, font.characterCount)
            assertEquals(256, font.characters.size)
            assertEquals(OPAQUE_RED, font.getCharacter(10)!!.getRGB(1, 2))
            assertEquals(OPAQUE_BLUE, font.getCharacter(255)!!.getRGB(2, 3))
            assertNull(font.getCharacter(256))
            assertTrue(font.characters.none { glyph -> glyphContains(glyph, OPAQUE_GREEN) })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun sideBySidePagesKeepGlyphIndicesWhenATrailingRowIsPresent() {
        val directory = createTempDirectory("font-pages")
        try {
            val path = directory.resolve("race-two-pages.png")
            val size = CharacterSize.RACE
            val rows = 257
            val image = BufferedImage(size.width * 2, size.height * rows, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 4 * size.height, OPAQUE_RED)
            image.setRGB(size.width, 4 * size.height + 1, OPAQUE_BLUE)
            image.setRGB(0, 256 * size.height, OPAQUE_GREEN)
            ImageIO.write(image, "png", path.toFile())

            val font = parser.parse(path)

            assertEquals(CharacterSize.RACE, font.characterSize)
            assertEquals(FontType.TWO_PAGES, font.fontType)
            assertEquals(256, font.characterCount)
            assertEquals(512, font.characters.size)
            assertEquals(OPAQUE_RED, font.getCharacter(4)!!.getRGB(0, 0))
            assertEquals(OPAQUE_BLUE, font.getCharacter(256 + 4)!!.getRGB(0, 1))
            assertTrue(font.characters.none { glyph -> glyphContains(glyph, OPAQUE_GREEN) })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun malformedFontIsRejected() {
        val directory = createTempDirectory("font-malformed")
        try {
            val badSize = directory.resolve("bad-size.png")
            ImageIO.write(BufferedImage(37, 100, BufferedImage.TYPE_INT_ARGB), "png", badSize.toFile())
            assertIs<FontFileException.InvalidFontFileWidth>(
                assertFailsWith<FontFileException> { parser.parse(badSize) }
            )

            val notAnImage = directory.resolve("not-a-font.png")
            Files.write(notAnImage, byteArrayOf(0x00, 0x01, 0x02, 0x03))
            assertFailsWith<Exception> { parser.parse(notAnImage) }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun assertColumnFont(name: String, size: CharacterSize, rowsInFile: Int) {
        val path = fontResource(name)
        val image = ImageIO.read(path.toFile())
        assertEquals(size.width, image.width)
        assertEquals(size.height * rowsInFile, image.height)

        val font = parser.parse(path)

        assertEquals(size, font.characterSize)
        assertEquals(FontType.STANDARD, font.fontType)
        assertEquals(256, font.characterCount)
        assertEquals(256, font.characters.size)
        for (index in 0 until 256) {
            val glyph = font.getCharacter(index)!!
            val expected = image.getRGB(0, index * size.height, size.width, size.height, null, 0, size.width)
            val actual = glyph.getRGB(0, 0, size.width, size.height, null, 0, size.width)
            assertTrue(expected.contentEquals(actual), "$name glyph $index")
        }
        if (rowsInFile > 256) {
            val trailing = image.getRGB(0, 256 * size.height, size.width, size.height, null, 0, size.width)
            assertTrue(trailing.all { it ushr 24 == 0 }, "$name trailing cell")
            assertTrue(actualOpaqueCount(font.getCharacter(255)!!) > 0, "$name glyph 255")
            assertEquals(0, actualOpaqueCount(font.getCharacter(0)!!), "$name glyph 0")
        }
    }

    private fun actualOpaqueCount(glyph: BufferedImage): Int {
        var count = 0
        for (y in 0 until glyph.height) {
            for (x in 0 until glyph.width) {
                if (glyph.getRGB(x, y) ushr 24 != 0) count++
            }
        }
        return count
    }

    private fun glyphContains(glyph: BufferedImage, argb: Int): Boolean {
        for (y in 0 until glyph.height) {
            for (x in 0 until glyph.width) {
                if (glyph.getRGB(x, y) == argb) return true
            }
        }
        return false
    }

    private fun fontResource(name: String): Path {
        val resource = requireNotNull(javaClass.getResource("/fonts/$name")) { "Missing test font $name" }
        return Path.of(resource.toURI())
    }

    private data class Expected(
        val width: Int,
        val height: Int,
        val size: CharacterSize,
        val type: FontType,
        val count: Int,
    )

    private companion object {
        const val OPAQUE_RED = 0xFFFF0000.toInt()
        const val OPAQUE_GREEN = 0xFF00FF00.toInt()
        const val OPAQUE_BLUE = 0xFF0000FF.toInt()
    }
}
