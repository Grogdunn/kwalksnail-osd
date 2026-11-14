package dev.grogdunn.kwalksnailosd.srt

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le righe di esempio sono quelle dei test di avsaase/walksnail-osd-tool (GPL-3.0),
 * backend/src/srt/frame.rs: firmware precedenti a 31.36.8, 32.37.10, e il formato debug 37.42.3.
 */
class SrtOverlayTest {
    @Test
    fun parsesWalksnailCuesFromTheOriginalSamples() {
        val directory = createTempDirectory("srt")
        val path = directory.resolve("clip.srt")
        path.writeText(
            """
            1
            00:00:00,000 --> 00:00:00,500
            $PRE_V31_36_8

            2
            00:00:01,000 --> 00:00:01,500
            $V32_37_10

            3
            00:00:02,000 --> 00:00:02,500
            $DEBUG_V37_42_3
            """.trimIndent(),
        )

        val file = SrtParser().parse(path)
        assertEquals(3, file.cues.size)
        assertTrue(file.hasDistance)
        assertEquals(0L, file.cues[0].startMillis)
        assertEquals(500L, file.cues[0].endMillis)
        assertEquals(
            SrtData(
                signal = 4,
                channel = 8,
                flightTimeSeconds = 0,
                skyBatteryVolts = 4.7,
                groundBatteryVolts = 7.2,
                latencyMillis = 32,
                bitrateMbps = 25.0,
                distanceMeters = 7,
            ),
            file.cues[0].data,
        )
        assertEquals(25.0, file.cues[1].data?.bitrateMbps)
        assertEquals(16.7, file.cues[1].data?.skyBatteryVolts)
        assertEquals(1, file.cues[1].data?.distanceMeters)
        assertNull(file.cues[2].data)

        assertEquals(file.cues[0], srtCueAt(file.cues, 0.0))
        assertEquals(file.cues[0], srtCueAt(file.cues, 1000.0))
        assertEquals(file.cues[1], srtCueAt(file.cues, 1001.0))
        assertNull(srtCueAt(file.cues, 2001.0)?.data)
        Files.deleteIfExists(path)
    }

    @Test
    fun emptyFileIsRejected() {
        val path = createTempDirectory("srt-empty").resolve("empty.srt")
        path.writeText("non è un sottotitolo")
        val error = assertFailsWith<IllegalArgumentException> { SrtParser().parse(path) }
        assertTrue(error.message!!.contains("SRT"))
    }

    @Test
    fun labelMatchesTheOriginalFieldOrderAndPadding() {
        val data = SrtData(4, 8, 0, 4.7, 7.2, 32, 25.0, 7)
        val all = SrtOptions(
            showTime = true,
            showSkyBattery = true,
            showGroundBattery = true,
            showSignal = true,
            showLatency = true,
            showBitrate = true,
            showDistance = true,
        )
        assertEquals(
            "Time:0:00  SBat: 4.7V  GBat: 7.2V  Signal:4  Latency: 32ms  Bitrate:25.0Mbps  Distance:  7m",
            srtLabel(data, all),
        )
        assertEquals(
            "Signal:4  Latency: 32ms  Bitrate:25.0Mbps  Distance:  7m",
            srtLabel(data, SrtOptions()),
        )
        assertEquals(
            "Distance:1.50km",
            srtLabel(data.copy(distanceMeters = 1500), SrtOptions(showSignal = false, showLatency = false, showBitrate = false, showDistance = true)),
        )
        assertEquals("", srtLabel(data, SrtOptions(showSignal = false, showLatency = false, showBitrate = false, showDistance = false)))
    }

    @Test
    fun overlayPaintsOnlyTheSelectedFields() {
        val data = SrtData(4, 8, 65, 16.7, 12.5, 25, 25.0, 1)
        val shown = blackFrame()
        SrtOverlay.draw(
            shown,
            data,
            SrtOptions(xPercent = 4f, yPercent = 20f, scale = 90f, showTime = true, showSkyBattery = true, showSignal = true, showLatency = false, showBitrate = false, showDistance = false),
        )
        val ink = inkBounds(shown)
        assertTrue(ink.count > 30, "i campi visibili devono lasciare pixel sul fotogramma")
        assertTrue(ink.minX in 30..80, "il testo parte vicino al 4% della larghezza, x=${ink.minX}")
        assertTrue(ink.minY in 90..140, "il bordo superiore sta vicino al 20% dell'altezza, y=${ink.minY}")
        assertTrue(ink.maxY < 220, "il testo non deve scendere oltre il riquadro, y=${ink.maxY}")

        val hidden = blackFrame()
        SrtOverlay.draw(
            hidden,
            data,
            SrtOptions(xPercent = 4f, yPercent = 20f, scale = 90f, showTime = false, showSkyBattery = false, showGroundBattery = false, showSignal = false, showLatency = false, showBitrate = false, showDistance = false),
        )
        assertEquals(0, nonBlackPixels(hidden))
        assertFalse(nonBlackPixels(shown) == 0)
    }
}

private fun blackFrame(): BufferedImage {
    val image = BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = Color.BLACK
    graphics.fillRect(0, 0, image.width, image.height)
    graphics.dispose()
    return image
}

private data class Ink(val count: Int, val minX: Int, val minY: Int, val maxY: Int)

private fun inkBounds(image: BufferedImage): Ink {
    var count = 0
    var minX = image.width
    var minY = image.height
    var maxY = 0
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            if ((image.getRGB(x, y) and 0xFFFFFF) == 0) continue
            count += 1
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    return Ink(count, minX, minY, maxY)
}

private fun nonBlackPixels(image: BufferedImage): Int = inkBounds(image).count

private const val PRE_V31_36_8 =
    "Signal:4 CH:8 FlightTime:0 SBat:4.7V GBat:7.2V Delay:32ms Bitrate:25Mbps Distance:7m"

private const val V32_37_10 =
    "Signal:4 CH:7 FlightTime:0 SBat:16.7V GBat:12.5V Delay:25ms Bitrate:25.0Mbps Distance:1m"

private const val DEBUG_V37_42_3 =
    "CH:1 MCS:4 SP[ 45 152  47 149] GP[ 49  48  45  47] GTP:27 GTP0:00 STP:24 STP0:00 GSNR:25.9 SSNR:17.8 Gtemp:50 Stemp:82 Delay:31ms Frame:60  Gerr:0 SErr:0 42, [iso:0,mode=max, exp:0] [gain:0.00 exp:0.000ms]"
