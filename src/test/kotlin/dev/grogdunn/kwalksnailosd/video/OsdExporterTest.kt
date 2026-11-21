package dev.grogdunn.kwalksnailosd.video

import dev.grogdunn.kwalksnailosd.osd.FcFirmware
import dev.grogdunn.kwalksnailosd.osd.Frame
import dev.grogdunn.kwalksnailosd.osd.Glyph
import dev.grogdunn.kwalksnailosd.osd.Osd
import dev.grogdunn.kwalksnailosd.osd.OsdRenderer
import dev.grogdunn.kwalksnailosd.osd.Position
import dev.grogdunn.kwalksnailosd.osd.font.CharacterSize
import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.osd.font.FontType
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsdExporterTest {
    @Test
    fun chromaKeyBackgroundMatchesTheChosenColorAndGlyphIsVisible() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val directory = createTempDirectory("osd-chroma")
        val output = directory.resolve("osd.mp4")
        val key = Color.GREEN

        val result = OsdExporter(ffmpeg).export(
            videoInfo(),
            sampleOsd(),
            magentaFont(),
            OsdExportSettings(
                mode = OsdExportMode.ChromaKey,
                chromaColor = key,
                codec = VideoCodec.H264,
                bitrateMbps = 8,
                encoder = BurnEncoderPreference.Software,
            ),
            output,
        )

        assertTrue(output.exists())
        assertTrue(result.framesWritten == 10, "frames ${result.framesWritten}")
        val frame = extractFrame(ffmpeg, output, frameIndex = 5, png = directory.resolve("mid.png"))
        val tile = OsdRenderer().characterSizeForFrameHeight(frame.height)
        val origin = Position(0, tile.height / 2)
        val glyph = frame.getRGB(tile.width / 2, tile.height / 2)
        val outside = frame.getRGB(frame.width - 2, frame.height - 2)
        assertTrue(isMagenta(glyph), "glyph ${hex(glyph)}")
        assertTrue(closeTo(outside, key, tolerance = 24), "outside ${hex(outside)}")
        assertFalse(isMagenta(outside), "outside tile should stay the key color ${hex(outside)}")
        assertTrue(origin.y < frame.height)
    }

    @Test
    fun pngSequenceIsArgbWithATransparentBackground() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val directory = createTempDirectory("osd-png")
        val output = directory.resolve("frames")

        val result = OsdExporter(ffmpeg).export(
            videoInfo(),
            sampleOsd(),
            magentaFont(),
            OsdExportSettings(mode = OsdExportMode.PngSequence),
            output,
        )

        assertEquals(10, result.framesWritten)
        val names = Files.list(output).use { stream -> stream.map { it.fileName.toString() }.sorted().toList() }
        assertEquals((1..10).map { String.format(Locale.US, "osd_%06d.png", it) }, names)
        val image = ImageIO.read(output.resolve("osd_000001.png").toFile())
        assertTrue(image.colorModel.hasAlpha(), "PNG must keep an alpha channel")
        val tile = CharacterSize.LARGE
        val outside = image.getRGB(image.width - 2, image.height - 2)
        val glyph = image.getRGB(tile.width / 2, tile.height / 2)
        assertEquals(0, alpha(outside), "outside alpha ${hex(outside)}")
        assertEquals(255, alpha(glyph), "glyph alpha ${hex(glyph)}")
        assertTrue(isMagenta(glyph), "glyph ${hex(glyph)}")
    }

    @Test
    fun pngByteEstimateAndDiskCheck() {
        val frames = 10
        val estimate = estimatePngSequenceBytes(160, 90, frames)
        assertTrue(estimate >= 160L * 90 * 4 * frames, "estimate $estimate")
        val directory = createTempDirectory("osd-space")
        val check = checkPngDiskSpace(directory.resolve("missing"), requiredBytes = 64)
        assertTrue(check.enough, "available ${check.availableBytes}")
        assertTrue(check.availableBytes >= 64)
        assertEquals(64, check.requiredBytes)
    }

    @Test
    fun proRes4444KeepsAlphaWhenTheEncoderExists() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        assumeTrue(
            FfmpegEncoders.supports(ffmpeg, "prores_ks") || FfmpegEncoders.supports(ffmpeg, "prores"),
            "prores encoder missing",
        )
        val directory = createTempDirectory("osd-prores")
        val output = directory.resolve("osd.mov")

        OsdExporter(ffmpeg).export(
            videoInfo(),
            sampleOsd(),
            magentaFont(),
            OsdExportSettings(mode = OsdExportMode.ProRes4444),
            output,
        )

        assertTrue(output.exists())
        val frame = extractFrame(ffmpeg, output, frameIndex = 4, png = directory.resolve("frame.png"), alpha = true)
        assertTransparentCornerAndGlyph(frame)
    }

    @Test
    fun vp9AlphaKeepsAlphaWhenTheEncoderExists() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        assumeTrue(FfmpegEncoders.supports(ffmpeg, "libvpx-vp9"), "libvpx-vp9 missing")
        val directory = createTempDirectory("osd-vp9")
        val output = directory.resolve("osd.webm")

        OsdExporter(ffmpeg).export(
            videoInfo(),
            sampleOsd(),
            magentaFont(),
            OsdExportSettings(mode = OsdExportMode.Vp9Alpha, bitrateMbps = 2),
            output,
        )

        assertTrue(output.exists())
        val frame = extractFrame(
            ffmpeg,
            output,
            frameIndex = 4,
            png = directory.resolve("frame.png"),
            alpha = true,
            decoder = "libvpx-vp9",
        )
        assertTransparentCornerAndGlyph(frame)
    }

    @Test
    fun cancelDeletesPartialPngOutput() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val output = createTempDirectory("osd-png-cancel").resolve("frames")
        var reported = 0

        assertFailsWith<CancellationException> {
            OsdExporter(ffmpeg).export(
                videoInfo(),
                sampleOsd(),
                magentaFont(),
                OsdExportSettings(mode = OsdExportMode.PngSequence),
                output,
                onProgress = { reported += 1 },
                isCancelled = { reported > 0 },
            )
        }
        assertFalse(output.exists(), "cancelled PNG directory should be removed")
    }

    @Test
    fun cancelDeletesPartialChromaOutput() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val output = createTempDirectory("osd-chroma-cancel").resolve("osd.mp4")
        var reported = 0

        assertFailsWith<CancellationException> {
            OsdExporter(ffmpeg).export(
                videoInfo(),
                sampleOsd(),
                magentaFont(),
                OsdExportSettings(
                    mode = OsdExportMode.ChromaKey,
                    chromaColor = Color.GREEN,
                    encoder = BurnEncoderPreference.Software,
                ),
                output,
                onProgress = { reported += 1 },
                isCancelled = { reported > 0 },
            )
        }
        assertFalse(output.exists(), "cancelled chroma file should be removed")
    }
}

private fun videoInfo() = VideoInfo(
    height = 90,
    width = 160,
    codec = "h264",
    duration = Duration.ofSeconds(1),
    frameRate = 10.0,
    frameNumber = 10,
    hasAudio = false,
)

private fun sampleOsd() = Osd(
    fcFirmware = FcFirmware.BETAFLIGHT,
    frames = listOf(Frame(timestampMillis = 0, glyphs = listOf(Glyph(index = 7, position = Position(0, 0))))),
    duration = Duration.ofSeconds(1),
)

private fun magentaFont(): Font {
    val size = CharacterSize.LARGE
    val tile = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = tile.createGraphics()
    graphics.color = Color(255, 0, 255, 255)
    graphics.fillRect(0, 0, size.width, size.height)
    graphics.dispose()
    val blank = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
    val characters = MutableList(8) { blank }
    characters[7] = tile
    return Font(
        filePath = Path.of("burn-font.png"),
        characterCount = characters.size,
        characterSize = size,
        fontType = FontType.STANDARD,
        characters = characters,
    )
}

private fun extractFrame(
    ffmpeg: Path,
    video: Path,
    frameIndex: Int,
    png: Path,
    alpha: Boolean = false,
    decoder: String? = null,
): BufferedImage {
    val command = mutableListOf(
        ffmpeg.toString(),
        "-y",
        "-nostdin",
        "-hide_banner",
        "-loglevel",
        "error",
    )
    if (decoder != null) command += listOf("-c:v", decoder)
    command += listOf(
        "-i",
        video.toString(),
        "-vf",
        "select=eq(n\\,$frameIndex)",
        "-fps_mode",
        "vfr",
        "-frames:v",
        "1",
    )
    if (alpha) command += listOf("-pix_fmt", "rgba")
    command += png.toString()
    val process = ProcessBuilder(command).forBundledFfmpeg(ffmpeg).redirectErrorStream(true).start()
    val log = process.inputStream.readBytes().decodeToString()
    val code = process.waitFor()
    if (code != 0) error("ffmpeg exit $code\n$log")
    return ImageIO.read(png.toFile())
}

private fun assertTransparentCornerAndGlyph(frame: BufferedImage) {
    val tile = CharacterSize.LARGE
    val corner = frame.getRGB(frame.width - 2, frame.height - 2)
    val glyph = frame.getRGB(tile.width / 2, tile.height / 2)
    assertTrue(alpha(corner) < 16, "corner alpha ${alpha(corner)} ${hex(corner)}")
    assertTrue(alpha(glyph) > 200, "glyph alpha ${alpha(glyph)} ${hex(glyph)}")
    assertTrue(isMagenta(glyph, tolerance = 40), "glyph ${hex(glyph)}")
}

private fun isMagenta(rgb: Int, tolerance: Int = 40): Boolean {
    val red = rgb shr 16 and 0xFF
    val green = rgb shr 8 and 0xFF
    val blue = rgb and 0xFF
    return red >= 255 - tolerance && blue >= 255 - tolerance && green <= tolerance
}

private fun closeTo(rgb: Int, color: Color, tolerance: Int): Boolean =
    abs((rgb shr 16 and 0xFF) - color.red) <= tolerance &&
        abs((rgb shr 8 and 0xFF) - color.green) <= tolerance &&
        abs((rgb and 0xFF) - color.blue) <= tolerance

private fun alpha(rgb: Int): Int = rgb ushr 24 and 0xFF

private fun hex(rgb: Int): String = "0x${rgb.toUInt().toString(16)}"
