package dev.grogdunn.kwalksnailosd.osd

import dev.grogdunn.kwalksnailosd.osd.font.CharacterSize
import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.osd.font.FontType
import java.awt.Color
import java.awt.image.BufferedImage
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OsdPlacementTest {
    @Test
    fun playbackSpeedStretchesOsdTimestampsToTheVideo() {
        val frames = listOf(Frame(0, emptyList()), Frame(500, listOf(Glyph(1, Position(1, 1)))))
        val video = Duration.ofSeconds(2)
        val osd = Duration.ofSeconds(1)

        assertEquals(2.0, osdPlaybackSpeedFactor(video, osd, adjust = true))
        assertEquals(1.0, osdPlaybackSpeedFactor(video, osd, adjust = false))
        assertEquals(1.0, osdPlaybackSpeedFactor(video, Duration.ZERO, adjust = true))

        assertEquals(0, osdFrameAt(frames, videoTimeMillis = 400.0, speedFactor = 2.0)?.timestampMillis)
        assertEquals(500, osdFrameAt(frames, videoTimeMillis = 1_100.0, speedFactor = 2.0)?.timestampMillis)
        assertEquals(0, osdFrameAt(frames, videoTimeMillis = 1_000.0, speedFactor = 2.0)?.timestampMillis)
        assertEquals(500, osdFrameAt(frames, videoTimeMillis = 600.0, speedFactor = 1.0)?.timestampMillis)
    }

    @Test
    fun emptyOsdHasNoFrame() {
        assertNull(osdFrameAt(emptyList(), videoTimeMillis = 0.0, speedFactor = 1.0))
    }

    @Test
    fun centerPlacesTheGlyphBoxInTheMiddleOfTheFrame() {
        val frame = Frame(0, listOf(Glyph(3, Position(0, 0))))
        val tile = CharacterSize.LARGE

        assertEquals((1920 - tile.width) / 2, osdHorizontalOffset(1920, frame, tile.width))
        assertEquals((1080 - tile.height) / 2, osdVerticalOffset(1080, frame, tile.height))

        val image = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color(0x102030)
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.dispose()
        val offsetX = osdHorizontalOffset(image.width, frame, tile.width)
        val offsetY = osdVerticalOffset(image.height, frame, tile.height)
        OsdRenderer().renderInPlace(image, frame, solidFont(tile, 0xFFFF00FF.toInt()), offsetX, offsetY)

        assertEquals(0xFFFF00FF.toInt(), image.getRGB(offsetX, offsetY))
        assertEquals(0xFFFF00FF.toInt(), image.getRGB(offsetX + tile.width - 1, offsetY + tile.height - 1))
        assertEquals(0xFF102030.toInt(), image.getRGB(offsetX - 1, offsetY))
        assertEquals(0xFF102030.toInt(), image.getRGB(offsetX, offsetY - 1))
    }

    @Test
    fun offsetMovesTheTileByWholePixels() {
        val tile = CharacterSize.LARGE
        val image = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.dispose()
        OsdRenderer().renderInPlace(
            image,
            Frame(0, listOf(Glyph(3, Position(2, 1)))),
            solidFont(tile, 0xFF00FF00.toInt()),
            offsetX = 15,
            offsetY = -10,
        )

        val x = 2 * tile.width + 15
        val y = 1 * tile.height - 10
        assertEquals(0xFF00FF00.toInt(), image.getRGB(x, y))
        assertEquals(0xFF000000.toInt(), image.getRGB(x - 1, y))
        assertEquals(0xFF000000.toInt(), image.getRGB(x, y - 1))
    }
}

private fun solidFont(size: CharacterSize, argb: Int): Font {
    val tile = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = tile.createGraphics()
    graphics.color = Color(argb, true)
    graphics.fillRect(0, 0, size.width, size.height)
    graphics.dispose()
    val blank = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
    return Font(
        filePath = java.nio.file.Path.of("placement-font.png"),
        characterCount = 4,
        characterSize = size,
        fontType = FontType.STANDARD,
        characters = listOf(blank, blank, blank, tile),
    )
}
