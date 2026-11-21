package dev.grogdunn.kwalksnailosd.srt

import dev.grogdunn.kwalksnailosd.AppStateHolder
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class SrtParserFormatsTest {
    @Test
    fun parsesLegacyLinesIncludingIntegerBitrate() {
        val file = parse(
            "Signal:4 CH:1 FlightTime:1 SBat:16.1V GBat:11.2V Delay:31ms Bitrate:25.0Mbps Distance:0m",
            "Signal:2 CH:8 FlightTime:65 SBat:4.7V GBat:7.2V Delay:32ms Bitrate:25Mbps Distance:7m",
        )
        assertFalse(file.hasDebugFields)
        assertTrue(file.hasDistance)
        assertEquals(
            SrtData(4, 1, 1, 16.1, 11.2, 31, 25.0, 0),
            file.cues[0].data,
        )
        assertEquals(25.0, file.cues[1].data?.bitrateMbps)
        assertEquals(7, file.cues[1].data?.distanceMeters)
        assertNull(file.cues[0].data?.mcs)
    }

    @Test
    fun mapsAscentFrequencyToRBandChannel() {
        val bands = listOf(
            5_658_000 to 1,
            5_695_000 to 2,
            5_732_000 to 3,
            5_769_000 to 4,
            5_806_000 to 5,
            5_843_000 to 6,
            5_880_000 to 7,
            5_917_000 to 8,
        )
        val bodies = bands.map { (khz, _) -> ascent(khz) } + ascent(5_000_000) + ascent(5_695_000 + 5_000)
        val file = parse(*bodies.toTypedArray())
        bands.forEachIndexed { index, (_, channel) ->
            assertEquals(channel, file.cues[index].data?.channel, "Hz ${bands[index].first}")
            assertEquals(3, file.cues[index].data?.signal)
            assertNull(file.cues[index].data?.mcs)
        }
        assertNull(file.cues[8].data?.channel)
        assertNotNull(file.cues[8].data)
        assertEquals(2, file.cues[9].data?.channel)
        assertFalse(file.hasDebugFields)
    }

    @Test
    fun parsesDebugShortForm() {
        val file = parse(DEBUG_SHORT)
        val data = file.cues.single().data
        assertEquals(1, data?.channel)
        assertEquals(4, data?.mcs)
        assertEquals(25.9, data?.groundSnr)
        assertEquals(17.8, data?.skySnr)
        assertEquals(50, data?.groundTempC)
        assertEquals(82, data?.skyTempC)
        assertEquals(31, data?.latencyMillis)
        assertNull(data?.signal)
        assertNull(data?.bitrateMbps)
        assertNull(data?.distanceMeters)
        assertTrue(file.hasDebugFields)
        assertFalse(file.hasDistance)
    }

    @Test
    fun parsesDebugFirmware394415LongForm() {
        val file = parse(DEBUG_LONG)
        val data = file.cues.single().data
        assertEquals(8, data?.channel)
        assertEquals(0, data?.mcs)
        assertEquals(13.5, data?.groundSnr)
        assertEquals(9.9, data?.skySnr)
        assertEquals(61, data?.groundTempC)
        assertEquals(63, data?.skyTempC)
        assertEquals(64, data?.latencyMillis)
        assertNull(data?.signal)
        assertTrue(file.hasDebugFields)
    }

    @Test
    fun parsesAvatarDebugFixture() {
        val resource = requireNotNull(javaClass.getResource("/srt/avatar-debug-sample.srt"))
        val file = SrtParser().parse(Path.of(resource.toURI()))
        assertEquals(16, file.cues.size)
        assertTrue(file.hasDebugFields)
        assertFalse(file.hasDistance)
        val first = file.cues.first().data
        assertEquals(8, first?.channel)
        assertEquals(4, first?.mcs)
        assertEquals(19.7, first?.groundSnr)
        assertEquals(15.3, first?.skySnr)
        assertEquals(50, first?.groundTempC)
        assertEquals(55, first?.skyTempC)
        assertEquals(36, first?.latencyMillis)
        val last = file.cues.last().data
        assertEquals(8, last?.channel)
        assertEquals(0, last?.mcs)
        assertEquals(13.5, last?.groundSnr)
        assertEquals(9.9, last?.skySnr)
        assertEquals(61, last?.groundTempC)
        assertEquals(63, last?.skyTempC)
        assertEquals(64, last?.latencyMillis)
        assertEquals(300_966L, file.cues.last().startMillis)
    }

    @Test
    fun parsesAFileMixingAllFormats() {
        val file = parse(
            "Signal:4 CH:1 FlightTime:1 SBat:16.1V GBat:11.2V Delay:31ms Bitrate:25Mbps Distance:12m",
            ascent(5_695_000),
            DEBUG_SHORT,
        )
        assertEquals(3, file.cues.size)
        assertEquals(1, file.cues[0].data?.channel)
        assertEquals(2, file.cues[1].data?.channel)
        assertEquals(4, file.cues[2].data?.mcs)
        assertTrue(file.hasDistance)
        assertTrue(file.hasDebugFields)
    }

    @Test
    fun truncatedLastLineKeepsFieldsAlreadyFound() {
        val file = parse(
            "Signal:4 CH:1 FlightTime:1 SBat:16.1V GBat:11.2V Delay:31ms Bitrate:25.0Mbps Distance:5m",
            "Signal:3 CH:2 FlightTime:9 SBat:15.0V GBat:10",
        )
        assertEquals(2, file.cues.size)
        assertEquals(5, file.cues[0].data?.distanceMeters)
        assertEquals(3, file.cues[1].data?.signal)
        assertEquals(2, file.cues[1].data?.channel)
        assertEquals(9, file.cues[1].data?.flightTimeSeconds)
        assertEquals(15.0, file.cues[1].data?.skyBatteryVolts)
        assertNull(file.cues[1].data?.groundBatteryVolts)
        assertNull(file.cues[1].data?.latencyMillis)
    }

    @Test
    fun garbageCueTextIsKeptWithNullData() {
        val file = parse(
            "this is not telemetry",
            "Signal:4 CH:1 FlightTime:1 SBat:16.1V GBat:11.2V Delay:31ms Bitrate:25.0Mbps Distance:0m",
        )
        assertEquals(2, file.cues.size)
        assertNull(file.cues[0].data)
        assertEquals(4, file.cues[1].data?.signal)
        assertFalse(file.hasDistance)
    }

    @Test
    fun unrecognizedTelemetryIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> {
            parse("this is a regular movie subtitle")
        }
        assertTrue(error.message!!.contains("SRT"), error.message)
    }

    @Test
    fun channelAndFrequencyTokensInsideOtherWordsAreIgnored() {
        val file = parse("GCH:9 MHz:1111000 Signal:1 Hz:5695000 FlightTime:0 SBat:4.5V GBat:8.7V Delay:32ms Bitrate:13.3Mbps Distance:0m")
        assertEquals(2, file.cues.single().data?.channel)
        assertEquals(1, file.cues.single().data?.signal)
    }

    @Test
    fun labelsOmitNullFieldsAndFormatDebugOptions() {
        val legacy = SrtData(4, 8, 0, 4.7, 7.2, 32, 25.0, 7)
        assertEquals(
            "Signal:4  Latency: 32ms  Bitrate:25.0Mbps  Distance:  7m",
            srtLabel(legacy, SrtOptions(showMcs = true, showSnr = true, showTemp = true)),
        )
        val debug = SrtData(
            signal = null,
            channel = 1,
            flightTimeSeconds = null,
            skyBatteryVolts = null,
            groundBatteryVolts = null,
            latencyMillis = 31,
            bitrateMbps = null,
            distanceMeters = null,
            mcs = 4,
            groundSnr = 25.9,
            skySnr = 17.8,
            groundTempC = 50,
            skyTempC = 82,
        )
        val shown = srtLabel(
            debug,
            SrtOptions(
                showSignal = false,
                showLatency = false,
                showBitrate = false,
                showDistance = false,
                showMcs = true,
                showSnr = true,
                showTemp = true,
            ),
        )
        assertEquals("MCS:4  SNR:25.9/17.8  Temp:50/82C  ", shown)
        assertFalse(shown.contains("null"))
        val hidden = srtLabel(
            debug.copy(mcs = null, groundSnr = null, skySnr = null, groundTempC = null, skyTempC = null, latencyMillis = null),
            SrtOptions(showTime = true, showSignal = true, showLatency = true, showMcs = true, showSnr = true, showTemp = true),
        )
        assertEquals("", hidden)
    }

    @Test
    fun debugOnlyFileEnablesMcsAndSnr() {
        runBlocking {
            val directory = createTempDirectory("srt-debug-options")
            val holder = AppStateHolder()
            try {
                val path = directory.resolve("debug.srt")
                path.writeText(cueFile(DEBUG_SHORT))
                holder.onFilePickerClosed(path)
                holder.parseSelectedSrt()
                assertTrue(holder.state.srtOptions.showMcs)
                assertTrue(holder.state.srtOptions.showSnr)
                assertFalse(holder.state.srtOptions.showTemp)
                assertFalse(holder.state.srtOptions.showSignal)
                assertFalse(holder.state.srtOptions.showBitrate)
                assertFalse(holder.state.srtOptions.showDistance)
            } finally {
                holder.close()
            }
            val legacyHolder = AppStateHolder()
            try {
                val legacy = directory.resolve("legacy.srt")
                legacy.writeText(cueFile("Signal:4 CH:1 FlightTime:1 SBat:16.1V GBat:11.2V Delay:31ms Bitrate:25Mbps Distance:7m"))
                legacyHolder.onFilePickerClosed(legacy)
                legacyHolder.parseSelectedSrt()
                assertFalse(legacyHolder.state.srtOptions.showMcs)
                assertFalse(legacyHolder.state.srtOptions.showSnr)
                assertTrue(legacyHolder.state.srtOptions.showSignal)
                assertTrue(legacyHolder.state.srtOptions.showDistance)
            } finally {
                legacyHolder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    private fun ascent(khz: Int) =
        "Signal:3 Hz:$khz FlightTime:0 SBat:4.5V GBat:8.7V Delay:32ms Bitrate:13.3Mbps Distance:0m"

    private fun parse(vararg bodies: String): SrtFile {
        val path = createTempDirectory("srt-formats").resolve("clip.srt")
        path.writeText(cueFile(*bodies))
        return SrtParser().parse(path)
    }

    private fun cueFile(vararg bodies: String): String = bodies.mapIndexed { index, body ->
        val start = index * 500
        "${index + 1}\n${stamp(start)} --> ${stamp(start + 400)}\n$body"
    }.joinToString("\n\n")

    private fun stamp(millis: Int): String {
        val minutes = millis / 60_000
        val seconds = (millis / 1000) % 60
        val fraction = millis % 1000
        return "%02d:%02d:%02d,%03d".format(0, minutes, seconds, fraction)
    }

    private companion object {
        const val DEBUG_SHORT =
            "CH:1 MCS:4 SP[ 45 152  47 149] GP[ 49  48  45  47] GTP:27 GTP0:00 STP:24 STP0:00 GSNR:25.9 SSNR:17.8 Gtemp:50 Stemp:82 Delay:31ms Frame:60  Gerr:0 SErr:0 42, [iso:0,mode=max, exp:0] [gain:0.00 exp:0.000ms]"
        const val DEBUG_LONG =
            "CH:8 MCS:0 SP[ 93 151  97 151] GP[ 91  93  96  88] GTP:29 GTP0:00 STP:29 STP0:00 GSNR:13.5 SSNR:9.9 Gtemp:61 Stemp:63 Delay:64ms Frame:58  Gerr:0 SErr:18  3, [iso:0,mode=max, exp:0] [gain:0.00 exp:0.000ms, Lx:0] [cct:0, rb:0.000 0.000], SKY:[brF:5841.5, slot0F:5839, bestF:S=5340,G=5735, freq:0000,0000,0000,0000,0000,0000,0000,0000, s-avrp:0,0,0,0,0,0,0,0, g-avrp:-87,-89,-90,-87,-87,-89,-89,-85]"
    }
}
