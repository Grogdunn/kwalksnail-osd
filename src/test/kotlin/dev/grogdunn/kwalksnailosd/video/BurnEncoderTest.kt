package dev.grogdunn.kwalksnailosd.video

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.export_encoder_none
import dev.grogdunn.kwalksnailosd.generated.resources.export_encoder_nvenc
import dev.grogdunn.kwalksnailosd.uiText
import kotlinx.coroutines.CancellationException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BurnEncoderTest {
    @Test
    fun autoPicksTheFirstHardwareEncoderWhoseProbeSucceeds() {
        val probes = mutableListOf<String>()
        val catalog = catalog(
            path = "ffmpeg-auto",
            compiled = setOf("h264_nvenc", "h264_vaapi", "h264_qsv", "libx264"),
            probe = { recipe ->
                probes += recipe.name
                recipe.name == "h264_vaapi"
            },
        )

        val selection = catalog.resolve(VideoCodec.H264, BurnEncoderPreference.Auto)

        assertEquals("h264_vaapi", selection.chosen.name)
        assertEquals("libx264", selection.softwareFallback?.name)
        assertNull(selection.fallbackNote)
        assertEquals(listOf("h264_nvenc", "h264_vaapi", "h264_qsv"), probes)
    }

    @Test
    fun autoUsesSoftwareWhenEveryProbeFails() {
        val catalog = catalog(
            path = "ffmpeg-none",
            compiled = setOf("h264_nvenc", "libx264"),
            probe = { false },
        )

        val selection = catalog.resolve(VideoCodec.H264, BurnEncoderPreference.Auto)

        assertEquals("libx264", selection.chosen.name)
        assertFalse(selection.chosen.hardware)
        assertNull(selection.softwareFallback)
        assertEquals(uiText(Res.string.export_encoder_none), selection.fallbackNote)
    }

    @Test
    fun softwarePreferenceDoesNotProbeHardware() {
        var probes = 0
        val catalog = catalog(
            path = "ffmpeg-software",
            compiled = setOf("h264_nvenc", "libopenh264"),
            probe = {
                probes += 1
                true
            },
            software = "libopenh264",
        )

        val selection = catalog.resolve(VideoCodec.H264, BurnEncoderPreference.Software)

        assertEquals("libopenh264", selection.chosen.name)
        assertEquals("yuv420p", selection.chosen.pixelFormat)
        assertEquals(0, probes)
        assertNull(selection.fallbackNote)
    }

    @Test
    fun specificEncoderIsUsedOnlyWhenItsProbeSucceeds() {
        val working = catalog(
            path = "ffmpeg-nvenc-ok",
            compiled = setOf("hevc_nvenc", "libx265"),
            probe = { it.kind == HardwareKind.Nvenc },
            software = "libx265",
        )
        val missing = catalog(
            path = "ffmpeg-nvenc-missing",
            compiled = setOf("libx265"),
            probe = { true },
            software = "libx265",
        )

        val selected = working.resolve(VideoCodec.H265, BurnEncoderPreference.Hardware(HardwareKind.Nvenc))
        val unavailable = missing.resolve(VideoCodec.H265, BurnEncoderPreference.Hardware(HardwareKind.Nvenc))

        assertEquals("hevc_nvenc", selected.chosen.name)
        assertEquals("libx265", selected.softwareFallback?.name)
        assertEquals("libx265", unavailable.chosen.name)
        assertTrue(unavailable.fallbackNote.orEmpty().contains("NVIDIA NVENC"))
    }

    @Test
    fun amfIsOfferedOnlyWhenTheBinaryListsIt() {
        val without = hardwareCandidates(
            HardwareKind.Amf,
            VideoCodec.H264,
            compiled = setOf("h264_nvenc", "h264_vaapi"),
            renderNodes = emptyList(),
        )
        val with = hardwareCandidates(
            HardwareKind.Amf,
            VideoCodec.H264,
            compiled = setOf("h264_amf"),
            renderNodes = emptyList(),
        )

        assertTrue(without.isEmpty())
        assertEquals("h264_amf", with.single().name)
        assertEquals("nv12", with.single().pixelFormat)
    }

    @Test
    fun vaapiUploadsNv12AndDoesNotAskForYuv420p() {
        val recipe = hardwareCandidates(
            HardwareKind.Vaapi,
            VideoCodec.H264,
            compiled = setOf("h264_vaapi"),
            renderNodes = listOf("/dev/dri/renderD128", "/dev/dri/renderD129"),
        ).first()
        val command = probeCommand(Path.of("ffmpeg"), recipe).joinToString(" ")
        val fileArgs = encoderOutputArgs(recipe, bitrateMbps = 8, includeFileArgs = true).joinToString(" ")

        assertTrue(command.contains("-vaapi_device /dev/dri/renderD128"))
        assertTrue(command.contains("format=nv12,hwupload"))
        assertFalse(command.contains("yuv420p"))
        assertFalse(fileArgs.contains("-pix_fmt"))
        assertTrue(fileArgs.contains("-rc_mode VBR -b:v 8M -maxrate 8M"))
        assertTrue(fileArgs.contains("-profile:v high"))
    }

    @Test
    fun nvencMapsTheBitrateToVbrAndNv12() {
        val recipe = hardwareCandidates(
            HardwareKind.Nvenc,
            VideoCodec.H265,
            compiled = setOf("hevc_nvenc"),
            renderNodes = emptyList(),
        ).single()
        val probe = probeCommand(Path.of("ffmpeg"), recipe).joinToString(" ")
        val fileArgs = encoderOutputArgs(recipe, bitrateMbps = 40, includeFileArgs = true).joinToString(" ")

        assertTrue(probe.contains("-pix_fmt nv12"))
        assertTrue(probe.contains("-rc vbr -b:v 1M -maxrate 1M -bufsize 2M -preset p4 -tune hq"))
        assertFalse(probe.contains("hvc1"))
        assertTrue(fileArgs.contains("-tag:v hvc1"))
        assertFalse(fileArgs.contains("-profile:v high"))
    }

    @Test
    fun qsvOnARenderNodeUploadsFramesAndFallsBackToSystemMemoryNv12() {
        val recipes = hardwareCandidates(
            HardwareKind.Qsv,
            VideoCodec.H264,
            compiled = setOf("h264_qsv"),
            renderNodes = listOf("/dev/dri/renderD128"),
        )
        val device = recipes.first()
        val system = recipes.last()

        assertTrue(device.globalArgs.contains("vaapi=va:/dev/dri/renderD128"))
        assertEquals("format=nv12,hwupload=extra_hw_frames=64", device.videoFilter)
        assertNull(device.pixelFormat)
        assertEquals("nv12", system.pixelFormat)
        assertTrue(system.globalArgs.isEmpty())
        val rate = rateArguments(RateControl.QsvBitrate, 12).joinToString(" ")
        assertEquals("-b:v 12M -maxrate 12M -bufsize 24M -preset medium", rate)
    }

    @Test
    fun softwareCommandKeepsYuv420pAndTheBitrate() {
        val args = encoderOutputArgs(softwareRecipe("libx264"), bitrateMbps = 8, includeFileArgs = true)
        assertEquals(listOf("-c:v", "libx264", "-pix_fmt", "yuv420p", "-b:v", "8M"), args)
    }

    @Test
    fun probeResultIsCachedForTheSameRecipe() {
        EncoderProbeCache.clear()
        var calls = 0
        val catalog = catalog(
            path = "ffmpeg-cache",
            compiled = setOf("h264_nvenc", "libx264"),
            probe = {
                calls += 1
                true
            },
        )

        assertEquals("h264_nvenc", catalog.working(VideoCodec.H264).single().name)
        assertEquals("h264_nvenc", catalog.working(VideoCodec.H264).single().name)
        assertEquals(1, calls)
    }

    @Test
    fun hardwareFailureRetriesSoftwareAndReportsIt() {
        val hardware = nvenc()
        val software = softwareRecipe("libx264")
        val used = mutableListOf<String>()

        val result = encodeWithFallback(
            primary = hardware,
            fallback = software,
            encode = { recipe ->
                used += recipe.name
                if (recipe.hardware) throw VideoBurnException("apertura rifiutata")
                "ok"
            },
            onFallback = { failed, next, error ->
                assertEquals("h264_nvenc", failed.name)
                assertEquals("libx264", next.name)
                assertEquals("apertura rifiutata", error.message)
            },
        )

        assertEquals("ok", result)
        assertEquals(listOf("h264_nvenc", "libx264"), used)
    }

    @Test
    fun cancellationIsNotTurnedIntoASoftwareEncode() {
        var calls = 0
        val error = assertFailsWith<CancellationException> {
            encodeWithFallback(
                primary = nvenc(),
                fallback = softwareRecipe("libx264"),
                encode = {
                    calls += 1
                    throw CancellationException("Burn annullato")
                },
                onFallback = { _, _, _ -> error("il fallback non deve partire") },
            )
        }

        assertEquals("Burn annullato", error.message)
        assertEquals(1, calls)
    }

    @Test
    fun softwareFailureAfterHardwareFailureNamesBothEncoders() {
        val error = assertFailsWith<VideoBurnException> {
            encodeWithFallback(
                primary = nvenc(),
                fallback = softwareRecipe("libopenh264"),
                encode = { recipe -> throw VideoBurnException("${recipe.name} fermo") },
                onFallback = { _, _, _ -> },
            )
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("h264_nvenc"), message)
        assertTrue(message.contains("libopenh264"), message)
    }

    @Test
    fun encoderListingParserKeepsOnlyEncoderRows() {
        val names = encoderNamesFromListing(
            """
            Encoders:
             V..... = Video
             A..... = Audio
             ------
             V....D libopenh264          OpenH264
             V....D h264_nvenc           NVIDIA NVENC
             V..... h264_amf             AMD AMF
            """.trimIndent(),
        )
        assertEquals(setOf("libopenh264", "h264_nvenc", "h264_amf"), names)
    }

    @Test
    fun hardwareLabelsUseTheStringResources() {
        assertEquals(uiText(Res.string.export_encoder_nvenc), HardwareKind.Nvenc.label())
        assertEquals("NVIDIA NVENC", HardwareKind.Nvenc.label())
        assertEquals("VAAPI", HardwareKind.Vaapi.label())
        assertEquals("AMD AMF", HardwareKind.Amf.label())
        assertEquals("Intel QSV", HardwareKind.Qsv.label())
        assertEquals("Apple VideoToolbox", HardwareKind.VideoToolbox.label())
    }

    @Test
    fun linuxPrefersNvencThenVaapiThenQsv() {
        assertEquals(
            listOf(HardwareKind.Nvenc, HardwareKind.Vaapi, HardwareKind.Qsv, HardwareKind.Amf, HardwareKind.VideoToolbox),
            hardwareAutoOrder("Linux"),
        )
        assertEquals(HardwareKind.VideoToolbox, hardwareAutoOrder("Mac OS X").first())
        assertEquals(listOf(HardwareKind.Nvenc, HardwareKind.Qsv, HardwareKind.Amf), hardwareAutoOrder("Windows 11").take(3))
    }

    private fun catalog(
        path: String,
        compiled: Set<String>,
        probe: (EncoderRecipe) -> Boolean,
        software: String = "libx264",
    ) = HardwareEncoderCatalog(
        ffmpeg = Path.of(path),
        compiled = { compiled },
        probe = probe,
        renderNodes = { emptyList() },
        softwareName = { software },
        osName = "Linux",
    )

    private fun nvenc(): EncoderRecipe = hardwareCandidates(
        HardwareKind.Nvenc,
        VideoCodec.H264,
        compiled = setOf("h264_nvenc"),
        renderNodes = emptyList(),
    ).single()
}
