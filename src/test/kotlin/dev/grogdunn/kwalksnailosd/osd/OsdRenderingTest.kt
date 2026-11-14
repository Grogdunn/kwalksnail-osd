package dev.grogdunn.kwalksnailosd.osd

import dev.grogdunn.kwalksnailosd.osd.font.CharacterSize
import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.osd.font.FontDimensions
import dev.grogdunn.kwalksnailosd.osd.font.FontFileException
import dev.grogdunn.kwalksnailosd.osd.font.FontParser
import dev.grogdunn.kwalksnailosd.osd.font.FontType
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class OsdRenderingTest {
    @Test
    fun fontDimensionsMatchWalksnailAtlasSizes() {
        val parser = FontParser()

        assertEquals(FontDimensions(CharacterSize.LARGE, FontType.STANDARD, 256), parser.detectDimensions(36, 13_824))
        assertEquals(FontDimensions(CharacterSize.SMALL, FontType.STANDARD, 512), parser.detectDimensions(24, 18_432))
        assertEquals(FontDimensions(CharacterSize.RACE, FontType.STANDARD, 256), parser.detectDimensions(18, 6_912))
        assertEquals(FontDimensions(CharacterSize.LARGE, FontType.FOUR_COLOR, 256), parser.detectDimensions(144, 13_824))
        assertEquals(FontDimensions(CharacterSize.RACE, FontType.TWO_PAGES, 256), parser.detectDimensions(36, 6_912))
        assertEquals(FontDimensions(CharacterSize.RACE, FontType.FOUR_COLOR, 256), parser.detectDimensions(72, 6_912))
        assertEquals(FontDimensions(CharacterSize.ULTRA, FontType.STANDARD, 256), parser.detectDimensions(72, 27_648))
    }

    @Test
    fun fontDimensionsRejectUnknownWidthAndInexactHeight() {
        val parser = FontParser()

        assertIs<FontFileException.InvalidFontFileWidth>(
            assertFailsWith<FontFileException> { parser.detectDimensions(37, 13_824) }
        )
        assertIs<FontFileException.InvalidFontFileWidth>(
            assertFailsWith<FontFileException> { parser.detectDimensions(36, 5_400) }
        )
        assertIs<FontFileException.InvalidFontFileHeight>(
            assertFailsWith<FontFileException> { parser.detectDimensions(36, 13_825) }
        )
    }

    @Test
    fun characterSizeFollowsTheFrameHeight() {
        val renderer = OsdRenderer()

        assertEquals(CharacterSize.RACE, renderer.characterSizeForFrameHeight(540))
        assertEquals(CharacterSize.SMALL, renderer.characterSizeForFrameHeight(720))
        assertEquals(CharacterSize.LARGE, renderer.characterSizeForFrameHeight(1080))
        assertEquals(CharacterSize.XLARGE, renderer.characterSizeForFrameHeight(1440))
        assertEquals(CharacterSize.ULTRA, renderer.characterSizeForFrameHeight(2160))
        assertEquals(CharacterSize.LARGE, renderer.characterSizeForFrameHeight(1520))
    }

    @Test
    fun renderScalesTilesToTheFrameHeight() {
        val font = fontWithTile(index = 3, tile = solidTile(CharacterSize.LARGE, 0xFFFF0000.toInt()), size = CharacterSize.LARGE)
        val image = filledFrame(width = 1280, height = 720, color = 0xFF102030.toInt())

        OsdRenderer().renderInPlace(image, Frame(0, listOf(Glyph(3, Position(2, 1)))), font)

        assertEquals(0xFFFF0000.toInt(), image.getRGB(48, 36))
        assertEquals(0xFFFF0000.toInt(), image.getRGB(71, 71))
        assertEquals(0xFF102030.toInt(), image.getRGB(47, 36))
        assertEquals(0xFF102030.toInt(), image.getRGB(72, 36))
        assertEquals(0xFF102030.toInt(), image.getRGB(48, 35))
        assertEquals(0xFF102030.toInt(), image.getRGB(48, 72))
    }

    @Test
    fun renderOn1080pKeepsNativeLargeTiles() {
        val font = fontWithTile(index = 1, tile = solidTile(CharacterSize.LARGE, 0xFFFFFF00.toInt()), size = CharacterSize.LARGE)
        val image = filledFrame(width = 1920, height = 1080, color = 0xFF000000.toInt())

        OsdRenderer().renderInPlace(image, Frame(0, listOf(Glyph(1, Position(0, 0)))), font)

        assertEquals(0xFFFFFF00.toInt(), image.getRGB(0, 0))
        assertEquals(0xFFFFFF00.toInt(), image.getRGB(35, 53))
        assertEquals(0xFF000000.toInt(), image.getRGB(36, 0))
        assertEquals(0xFF000000.toInt(), image.getRGB(0, 54))
    }

    @Test
    fun transparentGlyphPixelsLeaveTheFrameUntouched() {
        val tile = BufferedImage(CharacterSize.LARGE.width, CharacterSize.LARGE.height, BufferedImage.TYPE_INT_ARGB)
        tile.setRGB(0, 0, 0x00000000)
        tile.setRGB(1, 0, 0xFF00FF00.toInt())
        val font = fontWithTile(index = 4, tile = tile, size = CharacterSize.LARGE)
        val image = filledFrame(width = 1920, height = 1080, color = 0xFF0000FF.toInt())

        OsdRenderer().renderInPlace(image, Frame(0, listOf(Glyph(4, Position(0, 0)))), font)

        assertEquals(0xFF0000FF.toInt(), image.getRGB(0, 0))
        assertEquals(0xFF00FF00.toInt(), image.getRGB(1, 0))
    }

    @Test
    fun missingGlyphIndexIsSkipped() {
        val font = fontWithTile(index = 0, tile = solidTile(CharacterSize.LARGE, 0xFFFFFFFF.toInt()), size = CharacterSize.LARGE)
        val image = filledFrame(width = 1920, height = 1080, color = 0xFF111111.toInt())

        OsdRenderer().renderInPlace(image, Frame(0, listOf(Glyph(40, Position(0, 0)))), font)

        assertEquals(0xFF111111.toInt(), image.getRGB(0, 0))
    }

    @Test
    fun parsedFontRendersTheRequestedCharacter() {
        val directory = createTempDirectory("osd-font")
        val fontPath = directory.resolve("font.png")
        val race = CharacterSize.RACE
        val atlas = BufferedImage(race.width, race.height * 256, BufferedImage.TYPE_INT_ARGB)
        val tile = atlas.createGraphics()
        tile.color = Color(0x00FFFF)
        tile.fillRect(0, 7 * race.height, race.width, race.height)
        tile.dispose()
        ImageIO.write(atlas, "png", fontPath.toFile())

        val font = FontParser().parse(fontPath)
        val image = filledFrame(width = 960, height = 540, color = 0xFF010101.toInt())
        OsdRenderer().renderInPlace(image, Frame(0, listOf(Glyph(7, Position(1, 1)))), font)

        assertEquals(CharacterSize.RACE, font.characterSize)
        assertEquals(256, font.characterCount)
        assertEquals(0xFF00FFFF.toInt(), image.getRGB(18, 27))
        assertEquals(0xFF00FFFF.toInt(), image.getRGB(35, 53))
        assertEquals(0xFF010101.toInt(), image.getRGB(17, 27))
        Files.deleteIfExists(fontPath)
    }

    @Test
    fun previewFixtureShowsAnOsdOnA540pFrame() {
        val image = renderPreviewFixture()
        val reports = Files.createDirectories(java.nio.file.Path.of("build", "reports"))
        ImageIO.write(image, "png", reports.resolve("osd-preview-540p.png").toFile())

        val letterOriginX = 2 * CharacterSize.RACE.width
        val letterOriginY = 2 * CharacterSize.RACE.height
        assertEquals(0xFFFFFFFF.toInt(), image.getRGB(letterOriginX + 1, letterOriginY + 1))
        assertEquals(0xFF00FF66.toInt(), image.getRGB(CharacterSize.RACE.width + 1, 14 * CharacterSize.RACE.height + 1))
        assertEquals(0xFFFF3030.toInt(), image.getRGB(50 * CharacterSize.RACE.width + 1, CharacterSize.RACE.height + 1))
    }
}

private fun renderPreviewFixture(): BufferedImage {
    val image = BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB)
    val background = image.createGraphics()
    for (y in 0 until image.height) {
        val shade = 18 + y * 28 / image.height
        background.color = Color(shade, shade + 8, shade + 24)
        background.drawLine(0, y, image.width, y)
    }
    background.dispose()

    val race = CharacterSize.RACE
    val white = solidTile(race, 0xFFFFFFFF.toInt())
    val green = solidTile(race, 0xFF00FF66.toInt())
    val red = solidTile(race, 0xFFFF3030.toInt())
    val glyphs = mutableListOf<Glyph>()
    val tiles = mutableListOf(transparentTile(race))

    fun tileIndex(tile: BufferedImage): Int {
        tiles.add(tile)
        return tiles.lastIndex
    }

    val whiteIndex = tileIndex(white)
    val greenIndex = tileIndex(green)
    val redIndex = tileIndex(red)
    stampLetter(glyphs, whiteIndex, originX = 2, originY = 2, rows = LETTER_O)
    stampLetter(glyphs, whiteIndex, originX = 6, originY = 2, rows = LETTER_S)
    stampLetter(glyphs, whiteIndex, originX = 10, originY = 2, rows = LETTER_D)
    for (row in 12..16) {
        glyphs.add(Glyph(greenIndex, Position(1, row)))
    }
    glyphs.add(Glyph(redIndex, Position(50, 1)))

    OsdRenderer().renderInPlace(
        image,
        Frame(timestampMillis = 0, glyphs = glyphs),
        Font(
            filePath = java.nio.file.Path.of("preview-font.png"),
            characterCount = tiles.size,
            characterSize = race,
            fontType = FontType.STANDARD,
            characters = tiles,
        ),
    )
    return image
}

private fun stampLetter(glyphs: MutableList<Glyph>, index: Int, originX: Int, originY: Int, rows: List<String>) {
    rows.forEachIndexed { row, pattern ->
        pattern.forEachIndexed { column, pixel ->
            if (pixel == '#') {
                glyphs.add(Glyph(index, Position(originX + column, originY + row)))
            }
        }
    }
}

private val LETTER_O = listOf("###", "#.#", "#.#", "#.#", "###")
private val LETTER_S = listOf("###", "#..", "###", "..#", "###")
private val LETTER_D = listOf("##.", "#.#", "#.#", "#.#", "##.")

private fun solidTile(size: CharacterSize, argb: Int): BufferedImage {
    val tile = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = tile.createGraphics()
    graphics.color = Color(argb, true)
    graphics.fillRect(0, 0, size.width, size.height)
    graphics.dispose()
    return tile
}

private fun transparentTile(size: CharacterSize): BufferedImage =
    BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)

private fun fontWithTile(index: Int, tile: BufferedImage, size: CharacterSize): Font {
    val characters = MutableList(index + 1) { transparentTile(size) }
    characters[index] = tile
    return Font(
        filePath = java.nio.file.Path.of("fixture-font.png"),
        characterCount = characters.size,
        characterSize = size,
        fontType = FontType.STANDARD,
        characters = characters,
    )
}

private fun filledFrame(width: Int, height: Int, color: Int): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = Color(color)
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()
    return image
}
