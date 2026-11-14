package dev.grogdunn.kwalksnailosd.srt

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalksnailSrtTest {
    @Test
    fun parsesStandardLineWithIntegerBitrate() {
        val sample = WalksnailSrt.parseSample(
            "Signal:4 CH:8 FlightTime:0 SBat:4.7V GBat:7.2V Delay:32ms Bitrate:25Mbps Distance:7m"
        )

        assertEquals(4, sample.signal)
        assertEquals(8, sample.channel)
        assertEquals(0, sample.flightTimeSeconds)
        assertEquals(4.7, sample.vtxVoltage)
        assertEquals(7.2, sample.goggleVoltage)
        assertEquals(32, sample.latencyMillis)
        assertEquals(25.0, sample.bitrateMbps)
        assertEquals(7, sample.distanceMeters)
        assertNull(sample.goggleTemperatureCelsius)
        assertNull(sample.vtxTemperatureCelsius)
    }

    @Test
    fun parsesDecimalBitrateFromFirmware323710() {
        val sample = WalksnailSrt.parseSample(
            "Signal:4 CH:7 FlightTime:0 SBat:16.7V GBat:12.5V Delay:25ms Bitrate:25.0Mbps Distance:1m"
        )

        assertEquals(4, sample.signal)
        assertEquals(7, sample.channel)
        assertEquals(16.7, sample.vtxVoltage)
        assertEquals(12.5, sample.goggleVoltage)
        assertEquals(25, sample.latencyMillis)
        assertEquals(25.0, sample.bitrateMbps)
        assertEquals(1, sample.distanceMeters)
    }

    @Test
    fun parsesEarlyLineWithoutUnitsOrDistance() {
        val sample = WalksnailSrt.parseSample(
            "Signal:4 CH:8 FlightTime:9 SBat:16.2 GBat:7.6 Delay:28 Bitrate:25.0"
        )

        assertEquals(4, sample.signal)
        assertEquals(8, sample.channel)
        assertEquals(9, sample.flightTimeSeconds)
        assertEquals(16.2, sample.vtxVoltage)
        assertEquals(7.6, sample.goggleVoltage)
        assertEquals(28, sample.latencyMillis)
        assertEquals(25.0, sample.bitrateMbps)
        assertNull(sample.distanceMeters)
    }

    @Test
    fun parsesAscentFrequencyAsWalksnailChannel() {
        val sample = WalksnailSrt.parseSample(
            "Signal:3 Hz:5695000 FlightTime:0 SBat:4.5V GBat:8.7V Delay:32ms Bitrate:13.3Mbps Distance:0m"
        )

        assertEquals(3, sample.signal)
        assertEquals(2, sample.channel)
        assertEquals(4.5, sample.vtxVoltage)
        assertEquals(8.7, sample.goggleVoltage)
        assertEquals(13.3, sample.bitrateMbps)
        assertEquals(0, sample.distanceMeters)
    }

    @Test
    fun mapsEveryRBandCenterAndRejectsTheGap() {
        val centers = listOf(5658, 5695, 5732, 5769, 5806, 5843, 5880, 5917)
        centers.forEachIndexed { index, mhz ->
            assertEquals(index + 1, WalksnailSrt.channelFromFrequency(mhz * 1000.0), "Hz for $mhz")
            assertEquals(index + 1, WalksnailSrt.channelFromFrequency(mhz.toDouble()), "MHz for $mhz")
        }
        assertEquals(2, WalksnailSrt.channelFromFrequency(5_690_000.0))
        assertNull(WalksnailSrt.channelFromFrequency(5_689_000.0))
    }

    @Test
    fun keepsExplicitChannelWhenFrequencyIsAlsoPresent() {
        val sample = WalksnailSrt.parseSample("Signal:4 CH:8 Hz:5695000 Delay:10ms")

        assertEquals(8, sample.channel)
    }

    @Test
    fun unknownFrequencyLeavesChannelEmpty() {
        val sample = WalksnailSrt.parseSample("Signal:2 Hz:2400000 Delay:10ms Bitrate:5Mbps")

        assertEquals(2, sample.signal)
        assertNull(sample.channel)
        assertEquals(5.0, sample.bitrateMbps)
    }

    @Test
    fun parsesDebugLineWithTemperatures() {
        val sample = WalksnailSrt.parseSample(
            "CH:1 MCS:4 SP[ 45 152  47 149] GP[ 49  48  45  47] GTP:27 GTP0:00 STP:24 STP0:00 " +
                "GSNR:25.9 SSNR:17.8 Gtemp:50 Stemp:82 Delay:31ms Frame:60  Gerr:0 SErr:0 42, " +
                "[iso:0,mode=max, exp:0] [gain:0.00 exp:0.000ms]"
        )

        assertEquals(1, sample.channel)
        assertEquals(4, sample.signal)
        assertEquals(31, sample.latencyMillis)
        assertEquals(50.0, sample.goggleTemperatureCelsius)
        assertEquals(82.0, sample.vtxTemperatureCelsius)
        assertNull(sample.bitrateMbps)
        assertNull(sample.vtxVoltage)
        assertNull(sample.goggleVoltage)
        assertNull(sample.distanceMeters)
    }

    @Test
    fun parsesExtendedDebugLineFromFirmware394415() {
        val sample = WalksnailSrt.parseSample(
            "CH:8 MCS:4 SP[ 41 151  45 151] GP[ 50  53  52  48] GTP:29 GTP0:00 STP:26 STP0:00 " +
                "GSNR:19.7 SSNR:15.3 Gtemp:50 Stemp:55 Delay:36ms Frame:60  Gerr:0 SErr:0 24, " +
                "[iso:0,mode=max, exp:0] [gain:0.00 exp:0.000ms, Lx:0] [cct:0, rb:0.000 0.000], " +
                "SKY:[brF:5841.5, slot0F:5839, bestF:S=5340,G=5735, freq:0000,0000,0000,0000,0000,0000,0000,0000, " +
                "s-avrp:0,0,0,0,0,0,0,0, g-avrp:-88,-87,-89,-86,-85,-87,-89,-85]"
        )

        assertEquals(8, sample.channel)
        assertEquals(4, sample.signal)
        assertEquals(36, sample.latencyMillis)
        assertEquals(50.0, sample.goggleTemperatureCelsius)
        assertEquals(55.0, sample.vtxTemperatureCelsius)
        assertNull(sample.bitrateMbps)
    }

    @Test
    fun missingAndDashedFieldsStayNullWithoutDroppingTheOthers() {
        val sample = WalksnailSrt.parseSample(
            "Signal:2 CH:-- FlightTime:5 SBat:14.0V GBat:--V Delay:10ms Bitrate:8.5Mb"
        )

        assertEquals(2, sample.signal)
        assertNull(sample.channel)
        assertEquals(5, sample.flightTimeSeconds)
        assertEquals(14.0, sample.vtxVoltage)
        assertNull(sample.goggleVoltage)
        assertEquals(10, sample.latencyMillis)
        assertEquals(8.5, sample.bitrateMbps)
        assertNull(sample.distanceMeters)
        assertTrue(!sample.isEmpty())
    }

    @Test
    fun stripsFontTagsAndAcceptsLooseSpacing() {
        val sample = WalksnailSrt.parseSample(
            "<font size=\"28\">Signal : 1 CH : 3 FlightTime : 4 SBat : 12.0V GBat : 6.5V Delay : 22ms Bitrate : 10Mbps Distance : 15m</font>"
        )

        assertEquals(1, sample.signal)
        assertEquals(3, sample.channel)
        assertEquals(4, sample.flightTimeSeconds)
        assertEquals(12.0, sample.vtxVoltage)
        assertEquals(6.5, sample.goggleVoltage)
        assertEquals(22, sample.latencyMillis)
        assertEquals(10.0, sample.bitrateMbps)
        assertEquals(15, sample.distanceMeters)
    }

    @Test
    fun realFileKeepsGoodCuesAndSkipsTheCorruptedOne() {
        val track = WalksnailSrt.parse(
            """
            1
            00:00:00,000 --> 00:00:01,000
            Signal:4 CH:8 FlightTime:0 SBat:16.2V GBat:7.6V Delay:30ms Bitrate:25.0Mbps Distance:0m

            not a cue at all

            2
            00:00:01,000 --> 00:00:02,000
            Signal:0 CH:-- FlightTime:-- SBat:--V GBat:--V Delay:--ms Bitrate:--Mbps Distance:--m

            3
            00:00:02,000 --> 00:00:02,400
            Signal:-- CH:-- FlightTime:-- SBat:--V GBat:--V Delay:--ms Bitrate:--Mbps Distance:--m

            4
            00:00:02.500 --> 00:00:03.000
            Signal:3 CH:2 FlightTime:65 SBat:15.5V GBat:7.4V Delay:40ms Bitrate:15.0Mbps Distance:1200m
            """.trimIndent()
        )

        assertEquals(3, track.cues.size)
        assertEquals(1, track.skippedCues)
        assertEquals(4, track.sampleAt(0)?.signal)
        assertEquals(0, track.sampleAt(500)?.distanceMeters)
        assertEquals(0, track.sampleAt(1_500)?.signal)
        assertNull(track.sampleAt(1_500)?.channel)
        assertNull(track.sampleAt(1_500)?.bitrateMbps)
        assertEquals(0, track.sampleAt(2_200)?.signal)
        assertEquals(3, track.sampleAt(2_500)?.signal)
        assertEquals(65, track.sampleAt(2_500)?.flightTimeSeconds)
        assertEquals(1_200, track.sampleAt(2_500)?.distanceMeters)
        assertEquals(3, track.sampleAt(60_000)?.signal)
        assertEquals(4, track.sampleAt(-1)?.signal)
    }

    @Test
    fun holdsThePreviousCueAcrossAGapAndAfterTheLastEnd() {
        val track = WalksnailSrt.parse(
            """
            00:00:00,000 --> 00:00:00,150
            Signal:4 CH:1 Delay:20ms Bitrate:25Mbps

            00:00:01,000 --> 00:00:01,150
            Signal:1 CH:1 Delay:80ms Bitrate:2.5Mbps
            """.trimIndent()
        )

        assertEquals(4, track.sampleAt(0)?.signal)
        assertEquals(4, track.sampleAt(149)?.signal)
        assertEquals(4, track.sampleAt(999)?.signal)
        assertEquals(1, track.sampleAt(1_000)?.signal)
        assertEquals(1, track.sampleAt(5_000)?.signal)
        assertEquals(4, track.sampleAt(-5)?.signal)
    }

    @Test
    fun sortsCuesAndUsesTheLatestOneThatHasStarted() {
        val track = WalksnailSrt.parse(
            """
            2
            00:00:01,000 --> 00:00:02,000
            Signal:2 CH:1 Delay:10ms

            1
            00:00:00,000 --> 00:00:02,000
            Signal:1 CH:1 Delay:10ms

            3
            00:00:01,000 --> 00:00:01,500
            Signal:3 CH:1 Delay:10ms
            """.trimIndent()
        )

        assertEquals(1, track.sampleAt(0)?.signal)
        assertEquals(3, track.sampleAt(1_000)?.signal)
        assertEquals(3, track.sampleAt(1_600)?.signal)
    }

    @Test
    fun readsBomCrlfAndAMissingIndexFromAFile() {
        val directory = createTempDirectory("walksnail-srt")
        val path = directory.resolve("clip.srt")
        val body = "\uFEFF1\r\n00:00:00,000 --> 00:00:01,000\r\n" +
            "Signal:4 CH:6 FlightTime:12 SBat:15.8V GBat:8.1V Delay:33ms Bitrate:25.0Mbps Distance:128m\r\n"
        Files.write(path, body.toByteArray(Charsets.UTF_8))

        val track = WalksnailSrt.parse(path)

        assertEquals(1, track.cues.size)
        assertEquals(0, track.skippedCues)
        assertEquals(6, track.sampleAt(10)?.channel)
        assertEquals(128, track.sampleAt(10)?.distanceMeters)
        assertEquals(33, track.sampleAt(10)?.latencyMillis)
        Files.deleteIfExists(path)
    }

    @Test
    fun malformedFileDoesNotThrowAndYieldsNoCues() {
        val track = WalksnailSrt.parse("this is not an srt\n\nSignal without a time\n-->\n")

        assertEquals(0, track.cues.size)
        assertEquals(0, track.skippedCues)
        assertNull(track.sampleAt(0))
    }

    @Test
    fun emptyAndWhitespaceFilesAreEmptyTracks() {
        assertEquals(0, WalksnailSrt.parse("").cues.size)
        assertEquals(0, WalksnailSrt.parse("\n\n  \n").cues.size)
    }

    @Test
    fun reportsWhichFieldsATrackActuallyContains() {
        val standard = WalksnailSrt.parse(
            "1\n00:00:00,000 --> 00:00:01,000\nSignal:4 CH:8 FlightTime:1 SBat:16.0V GBat:7.0V Delay:20ms Bitrate:25Mbps Distance:3m\n"
        )
        val debug = WalksnailSrt.parse(
            "1\n00:00:00,000 --> 00:00:00,150\nCH:8 MCS:4 Gtemp:50 Stemp:55 Delay:36ms\n"
        )

        assertTrue(standard.hasSignal && standard.hasChannel && standard.hasLatency && standard.hasBitrate)
        assertTrue(standard.hasVtxVoltage && standard.hasGoggleVoltage && standard.hasFlightTime && standard.hasDistance)
        assertTrue(!standard.hasGoggleTemperature && !standard.hasVtxTemperature)

        assertTrue(debug.hasSignal && debug.hasChannel && debug.hasLatency)
        assertTrue(debug.hasGoggleTemperature && debug.hasVtxTemperature)
        assertTrue(!debug.hasBitrate && !debug.hasVtxVoltage && !debug.hasDistance)
    }

    @Test
    fun writesTheParsedTrackFromATempFileWithAShortCue() {
        val directory = createTempDirectory("walksnail-srt-short")
        val path = directory.resolve("short.srt")
        path.writeText("00:00:00,016 --> 00:00:00,150\nSignal:0 CH:1 Delay:94ms Bitrate:1.7Mbps Distance:263m\n")

        val sample = WalksnailSrt.parse(path).sampleAt(16)

        assertEquals(0, sample?.signal)
        assertEquals(94, sample?.latencyMillis)
        assertEquals(1.7, sample?.bitrateMbps)
        assertEquals(263, sample?.distanceMeters)
    }
}
