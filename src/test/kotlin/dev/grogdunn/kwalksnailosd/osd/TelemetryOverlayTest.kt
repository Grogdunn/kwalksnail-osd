package dev.grogdunn.kwalksnailosd.osd

import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.osd.font.FontType
import dev.grogdunn.kwalksnailosd.srt.TelemetrySample
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetryOverlayTest {
    private val overlay = TelemetryOverlay()
    private val full = TelemetrySample(
        signal = 4,
        channel = 8,
        latencyMillis = 36,
        bitrateMbps = 25.0,
        goggleTemperatureCelsius = 50.0,
        vtxTemperatureCelsius = 55.0,
        goggleVoltage = 7.6,
        vtxVoltage = 16.2,
        flightTimeSeconds = 65,
        distanceMeters = 263,
    )

    @Test
    fun formatsTheFieldsTheReferenceToolRenders() {
        val line = overlay.text(full, TelemetryOverlayOptions())

        assertEquals("Signal:4  CH:8  Latency: 36ms  Bitrate:25.0Mbps  Distance:263m", line)
    }

    @Test
    fun formatsOptionalFieldsAndConvertsLongDistanceToKilometres() {
        val line = overlay.text(
            full.copy(distanceMeters = 1_500),
            TelemetryOverlayOptions(
                showSignal = false,
                showChannel = false,
                showLatency = false,
                showBitrate = false,
                showDistance = true,
                showFlightTime = true,
                showVtxVoltage = true,
                showGoggleVoltage = true,
                showVtxTemperature = true,
                showGoggleTemperature = true,
            ),
        )

        assertEquals("Time:1:05  SBat:16.2V  GBat: 7.6V  STemp:55°C  GTemp:50°C  Distance:1.50km", line)
    }

    @Test
    fun omitsCheckedFieldsTheSampleDoesNotHave() {
        val line = overlay.text(
            TelemetrySample(signal = 1, latencyMillis = 8),
            TelemetryOverlayOptions(),
        )

        assertEquals("Signal:1  Latency:  8ms", line)
    }

    @Test
    fun hiddenFieldsAndANullSampleLeaveTheFrameUntouched() {
        val image = filled(320, 180, 0xFF203040.toInt())
        val before = copyOf(image)

        overlay.draw(image, full, TelemetryOverlayOptions(
            showSignal = false,
            showChannel = false,
            showLatency = false,
            showBitrate = false,
            showDistance = false,
        ))
        assertEquals(pixels(before), pixels(image))

        overlay.draw(image, null, TelemetryOverlayOptions())
        assertEquals(pixels(before), pixels(image))
    }

    @Test
    fun paintsNearTheRequestedCornerAndLeavesTheOppositeCornerAlone() {
        val image = filled(960, 540, 0xFF203040.toInt())
        overlay.draw(
            image,
            TelemetrySample(signal = 4),
            TelemetryOverlayOptions(
                xPercent = 0f,
                yPercent = 0f,
                scale = 48f,
                showChannel = false,
                showLatency = false,
                showBitrate = false,
                showDistance = false,
            ),
        )

        val changed = changedPixels(0xFF203040.toInt(), image)
        assertTrue(changed.isNotEmpty())
        assertTrue(changed.all { it.first < 700 && it.second < 80 })
        assertEquals(0xFF203040.toInt(), image.getRGB(image.width - 1, image.height - 1))
    }

    @Test
    fun aLargerScaleCoversMoreVerticalPixels() {
        val small = filled(1280, 1080, 0xFF101010.toInt())
        val large = filled(1280, 1080, 0xFF101010.toInt())
        overlay.draw(small, full, TelemetryOverlayOptions(xPercent = 2f, yPercent = 10f, scale = 12f))
        overlay.draw(large, full, TelemetryOverlayOptions(xPercent = 2f, yPercent = 10f, scale = 48f))

        val smallSpan = verticalSpan(changedPixels(0xFF101010.toInt(), small))
        val largeSpan = verticalSpan(changedPixels(0xFF101010.toInt(), large))
        assertTrue(largeSpan >= smallSpan + 10, "large=$largeSpan small=$smallSpan")
    }

    @Test
    fun lowerPositionMovesTheInkDownTheFrame() {
        val image = filled(640, 480, 0xFF010203.toInt())
        overlay.draw(image, TelemetrySample(signal = 4, latencyMillis = 20, bitrateMbps = 25.0), TelemetryOverlayOptions(
            xPercent = 0f,
            yPercent = 50f,
            scale = 40f,
            showChannel = false,
            showDistance = false,
        ))

        val rows = changedPixels(0xFF010203.toInt(), image).map { it.second }
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.min() > 200, "top row ${rows.min()}")
        assertTrue(rows.max() < 320, "bottom row ${rows.max()}")
        assertEquals(0xFF010203.toInt(), image.getRGB(10, 10))
    }

    @Test
    fun telemetryDoesNotEraseAnOsdTileInTheOtherCorner() {
        val image = filled(1920, 1080, 0xFF000000.toInt())
        val tile = BufferedImage(36, 54, BufferedImage.TYPE_INT_ARGB)
        val tileGraphics = tile.createGraphics()
        tileGraphics.color = Color(255, 0, 0)
        tileGraphics.fillRect(0, 0, 36, 54)
        tileGraphics.dispose()
        val font = Font(
            filePath = java.nio.file.Path.of("fixture-font.png"),
            characterCount = 1,
            characterSize = dev.grogdunn.kwalksnailosd.osd.font.CharacterSize.LARGE,
            fontType = FontType.STANDARD,
            characters = listOf(tile),
        )
        OsdRenderer().renderInPlace(image, Frame(0, listOf(Glyph(0, Position(0, 0)))), font)
        overlay.draw(image, full, TelemetryOverlayOptions())

        assertEquals(0xFFFF0000.toInt(), image.getRGB(0, 0))
        assertEquals(0xFFFF0000.toInt(), image.getRGB(35, 53))
        val changedNearDefaultAnchor = changedPixels(0xFF000000.toInt(), image).any { (_, y) -> y > 900 }
        assertTrue(changedNearDefaultAnchor)
    }

    @Test
    fun previewFixtureShowsTelemetryOnA720pFrame() {
        val image = filled(1280, 720, 0xFF14202C.toInt())
        val sky = image.createGraphics()
        for (y in 0 until image.height) {
            sky.color = Color(12 + y / 8, 28 + y / 14, 48 + y / 10)
            sky.drawLine(0, y, image.width, y)
        }
        sky.dispose()
        val before = copyOf(image)
        overlay.draw(image, full, TelemetryOverlayOptions())

        val reports = Files.createDirectories(java.nio.file.Path.of("build", "reports"))
        ImageIO.write(image, "png", reports.resolve("srt-overlay-preview.png").toFile())

        val changed = diff(before, image)
        assertTrue(changed.any { (x, y) -> x < 500 && y > 640 })
        assertTrue(changed.none { (_, y) -> y < 600 })
    }
}

private fun filled(width: Int, height: Int, color: Int): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = Color(color)
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()
    return image
}

private fun copyOf(image: BufferedImage): BufferedImage {
    val copy = BufferedImage(image.width, image.height, image.type)
    val graphics = copy.createGraphics()
    graphics.drawImage(image, 0, 0, null)
    graphics.dispose()
    return copy
}

private fun pixels(image: BufferedImage): List<Int> {
    val values = ArrayList<Int>(image.width * image.height)
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            values += image.getRGB(x, y)
        }
    }
    return values
}

private fun diff(before: BufferedImage, after: BufferedImage): List<Pair<Int, Int>> {
    val changed = mutableListOf<Pair<Int, Int>>()
    for (y in 0 until after.height) {
        for (x in 0 until after.width) {
            if (before.getRGB(x, y) != after.getRGB(x, y)) changed += x to y
        }
    }
    return changed
}

private fun changedPixels(background: Int, image: BufferedImage): List<Pair<Int, Int>> {
    val changed = mutableListOf<Pair<Int, Int>>()
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            if (image.getRGB(x, y) != background) changed += x to y
        }
    }
    return changed
}

private fun verticalSpan(pixels: List<Pair<Int, Int>>): Int {
    assertTrue(pixels.isNotEmpty())
    return pixels.maxOf { it.second } - pixels.minOf { it.second }
}
