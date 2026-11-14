package dev.grogdunn.kwalksnailosd.video

import dev.grogdunn.kwalksnailosd.osd.OsdParser
import dev.grogdunn.kwalksnailosd.osd.OsdRenderer
import dev.grogdunn.kwalksnailosd.osd.Position
import dev.grogdunn.kwalksnailosd.osd.font.CharacterSize
import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.osd.font.FontType
import kotlinx.coroutines.CancellationException
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoBurnTest {
    @Test
    fun locatorFindsARunnableFfmpeg() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        assertTrue(Files.isExecutable(ffmpeg), ffmpeg.toString())
    }

    @Test
    fun missingFfmpegNamesThePackagedBinaryAndThePath() {
        val locator = FfmpegLocator(bundled = { null }, onPath = { null })
        val error = assertFailsWith<FfmpegNotFoundException> { locator.locateFor(VideoCodec.H265) }
        val message = error.message.orEmpty()
        assertTrue(message.contains("JavaCV"), message)
        assertTrue(message.contains("PATH"), message)
    }

    @Test
    fun h265WithoutLibx265ExplainsThePackagedBuild() {
        org.junit.jupiter.api.Assumptions.assumeFalse(
            System.getProperty("os.name").orEmpty().lowercase().contains("win"),
            "lo stub è uno script di shell",
        )
        val directory = createTempDirectory("fake-ffmpeg")
        val fake = directory.resolve("ffmpeg")
        Files.writeString(
            fake,
            """
            #!/bin/sh
            case " ${'$'}* " in
              *" -version "*) echo ffmpeg version test; exit 0 ;;
              *" -encoders "*) printf '%s\n' ' V..... libopenh264'; exit 0 ;;
              *) exit 1 ;;
            esac
            """.trimIndent(),
        )
        fake.toFile().setExecutable(true)
        val locator = FfmpegLocator(bundled = { fake }, onPath = { null })
        val error = assertFailsWith<FfmpegNotFoundException> { locator.locateFor(VideoCodec.H265) }
        val message = error.message.orEmpty()
        assertTrue(message.contains("libx265"), message)
        assertTrue(message.contains("libopenh264"), message)
        assertEquals(fake, locator.locateFor(VideoCodec.H264))
    }

    @Test
    fun packagedJavaCvFfmpegBurnsH264() {
        val bundled = FfmpegLocator.bundledExecutable()
        check(bundled != null) { "manca il binario ffmpeg delle librerie JavaCV (${FfmpegLocator.lastLoadError})" }
        val openH264 = FfmpegEncoders.supports(bundled, "libopenh264")
        assertTrue(openH264, FfmpegEncoders.lastFailure ?: "libopenh264 assente")
        val directory = createTempDirectory("burn-openh264")
        val input = directory.resolve("clip.mp4")
        renderTestSrc(bundled, input, width = 160, height = 90, fps = 10, seconds = 1, withAudio = false)
        val videoInfo = VideoParser().use { it.getVideoInfo(input) }
        val output = directory.resolve("clip_with_osd.mp4")
        val result = VideoBurner(bundled).burn(
            input,
            output,
            videoInfo,
            OsdParser().parse(writeOsd(directory.resolve("clip.osd"))),
            magentaFont(),
            BurnSettings(VideoCodec.H264, bitrateMbps = 4, preferHardware = false, offsetX = 0, offsetY = 0, adjustPlaybackSpeed = false),
        )
        assertEquals("libopenh264", result.encoderName)
        assertTrue(output.exists())
        VideoParser().use { parser ->
            val burned = parser.getVideoInfo(output)
            assertTrue(burned.codec.lowercase().contains("264"), burned.codec)
        }
    }

    @Test
    fun burnKeepsDurationAudioAndDrawsTheOsdAtTheOffset() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val directory = createTempDirectory("burn-h264")
        val input = directory.resolve("clip.mp4")
        renderTestSrc(ffmpeg, input, width = 320, height = 180, fps = 10, seconds = 2, withAudio = true)
        val videoInfo = VideoParser().use { it.getVideoInfo(input) }
        assertTrue(videoInfo.frameNumber > 18, "frame nel video di test: ${videoInfo.frameNumber}")
        val osd = OsdParser().parse(writeOsd(directory.resolve("clip.osd")))
        val font = magentaFont()
        val output = directory.resolve("clip_with_osd.mp4")
        val settings = BurnSettings(
            codec = VideoCodec.H264,
            bitrateMbps = 8,
            preferHardware = false,
            offsetX = 20,
            offsetY = 8,
            adjustPlaybackSpeed = true,
        )

        val result = VideoBurner(ffmpeg).burn(input, output, videoInfo, osd, font, settings)

        assertTrue(output.exists())
        assertTrue(result.framesWritten > 0)
        VideoParser().use { parser ->
            val burned = parser.getVideoInfo(output)
            assertTrue(burned.hasAudio, "l'audio del video di origine deve restare nel file esportato")
            assertTrue(
                abs(burned.duration.toMillis() - videoInfo.duration.toMillis()) < 250,
                "durata attesa ${videoInfo.duration.toMillis()} ms, ottenuta ${burned.duration.toMillis()} ms",
            )
        }

        val early = extractFrame(ffmpeg, output, frameIndex = 2, png = directory.resolve("early.png"))
        val late = extractFrame(ffmpeg, output, frameIndex = 18, png = directory.resolve("late.png"))
        val reports = Files.createDirectories(Path.of("build", "reports"))
        ImageIO.write(early, "png", reports.resolve("burn-frame-early.png").toFile())
        ImageIO.write(late, "png", reports.resolve("burn-frame-late.png").toFile())

        val tile = OsdRenderer().characterSizeForFrameHeight(early.height)
        assertEquals(CharacterSize.LARGE, tile)
        val earlyGlyph = tileOrigin(gridX = 1, gridY = 1, tile = tile, offsetX = 20, offsetY = 8)
        val lateGlyph = tileOrigin(gridX = 6, gridY = 1, tile = tile, offsetX = 20, offsetY = 8)
        assertMagentaTile(early, earlyGlyph, tile)
        assertNotMagenta(early, lateGlyph, tile)
        assertMagentaTile(late, lateGlyph, tile)
        assertNotMagenta(late, earlyGlyph, tile)
    }

    @Test
    fun h265BurnProducesAnHevcFileOfTheSameDuration() {
        val hevc = try {
            FfmpegLocator().locateFor(VideoCodec.H265)
        } catch (missing: FfmpegNotFoundException) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, missing.message)
            throw missing
        }
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val directory = createTempDirectory("burn-h265")
        val input = directory.resolve("clip.mp4")
        renderTestSrc(ffmpeg, input, width = 320, height = 180, fps = 10, seconds = 1, withAudio = false)
        val videoInfo = VideoParser().use { it.getVideoInfo(input) }
        val osd = OsdParser().parse(writeOsd(directory.resolve("clip.osd")))
        val output = directory.resolve("clip_with_osd.mp4")

        val result = VideoBurner(hevc).burn(
            input,
            output,
            videoInfo,
            osd,
            magentaFont(),
            BurnSettings(VideoCodec.H265, bitrateMbps = 4, preferHardware = false, offsetX = 0, offsetY = 0, adjustPlaybackSpeed = false),
        )
        assertEquals("libx265", result.encoderName)

        assertTrue(output.exists())
        VideoParser().use { parser ->
            val burned = parser.getVideoInfo(output)
            assertFalse(burned.hasAudio)
            val codec = burned.codec.lowercase()
            assertTrue(codec.contains("265") || codec.contains("hevc"), codec)
            assertTrue(
                abs(burned.duration.toMillis() - videoInfo.duration.toMillis()) < 250,
                "durata attesa ${videoInfo.duration.toMillis()} ms, ottenuta ${burned.duration.toMillis()} ms",
            )
        }
    }

    @Test
    fun cancelDeletesThePartialOutput() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val directory = createTempDirectory("burn-cancel")
        val input = directory.resolve("clip.mp4")
        renderTestSrc(ffmpeg, input, width = 320, height = 180, fps = 10, seconds = 2, withAudio = false)
        val videoInfo = VideoParser().use { it.getVideoInfo(input) }
        val output = directory.resolve("clip_with_osd.mp4")
        var framesReported = 0

        assertFailsWith<CancellationException> {
            VideoBurner(ffmpeg).burn(
                input,
                output,
                videoInfo,
                OsdParser().parse(writeOsd(directory.resolve("clip.osd"))),
                magentaFont(),
                BurnSettings(VideoCodec.H264, 4, preferHardware = false, 0, 0, adjustPlaybackSpeed = false),
                onProgress = { framesReported += 1 },
                isCancelled = { framesReported > 0 },
            )
        }
        assertFalse(output.exists())
    }
}

private fun renderTestSrc(ffmpeg: Path, output: Path, width: Int, height: Int, fps: Int, seconds: Int, withAudio: Boolean) {
    val command = mutableListOf(
        ffmpeg.toString(),
        "-y",
        "-nostdin",
        "-hide_banner",
        "-loglevel",
        "error",
        "-f",
        "lavfi",
        "-i",
        "testsrc=size=${width}x${height}:rate=$fps",
    )
    if (withAudio) {
        command += listOf("-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100")
    }
    command += listOf("-t", seconds.toString())
    if (withAudio) command += "-shortest"
    val encoder = FfmpegEncoders.firstSoftware(ffmpeg, VideoCodec.H264)
        ?: error("nessun encoder H.264 in $ffmpeg")
    command += listOf("-c:v", encoder, "-pix_fmt", "yuv420p")
    if (withAudio) command += listOf("-c:a", "aac")
    command += output.toString()
    runFfmpeg(command)
}

private fun extractFrame(ffmpeg: Path, video: Path, frameIndex: Int, png: Path): BufferedImage {
    runFfmpeg(
        listOf(
            ffmpeg.toString(),
            "-y",
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            video.toString(),
            "-vf",
            "select=eq(n\\,$frameIndex)",
            "-fps_mode",
            "vfr",
            "-vframes",
            "1",
            png.toString(),
        )
    )
    return ImageIO.read(png.toFile())
}

private fun runFfmpeg(command: List<String>) {
    val process = ProcessBuilder(command).forBundledFfmpeg(Path.of(command.first())).redirectErrorStream(true).start()
    val log = process.inputStream.readBytes().decodeToString()
    val code = process.waitFor()
    if (code != 0) error("ffmpeg exit $code\n$log")
}

private fun writeOsd(path: Path): Path {
    val gridWidth = 53
    val gridHeight = 20
    val frames = listOf(
        0L to listOf(Position(1, 1)),
        500L to listOf(Position(6, 1)),
    )
    val bytes = ByteArray(40 + frames.size * (4 + gridWidth * gridHeight * 2))
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("BTFL".toByteArray(Charsets.US_ASCII))
    buffer.position(40)
    for ((timestamp, glyphs) in frames) {
        buffer.putInt(timestamp.toInt())
        val cells = IntArray(gridWidth * gridHeight)
        for (glyph in glyphs) cells[glyph.y * gridWidth + glyph.x] = 7
        for (cell in cells) buffer.putShort(cell.toShort())
    }
    Files.write(path, bytes)
    return path
}

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

private fun tileOrigin(gridX: Int, gridY: Int, tile: CharacterSize, offsetX: Int, offsetY: Int): Position =
    Position(gridX * tile.width + offsetX, gridY * tile.height + offsetY)

private fun assertMagentaTile(image: BufferedImage, origin: Position, tile: CharacterSize) {
    val centerX = origin.x + tile.width / 2
    val centerY = origin.y + tile.height / 2
    val center = image.getRGB(centerX, centerY)
    assertTrue(isMagenta(center), "pixel OSD ($centerX,$centerY) = ${hex(center)}")
    val outside = image.getRGB(origin.x - 4, centerY)
    assertFalse(isMagenta(outside), "pixel fuori dal tile (${origin.x - 4},$centerY) = ${hex(outside)}")
}

private fun assertNotMagenta(image: BufferedImage, origin: Position, tile: CharacterSize) {
    val centerX = origin.x + tile.width / 2
    val centerY = origin.y + tile.height / 2
    val center = image.getRGB(centerX, centerY)
    assertFalse(isMagenta(center), "il glifo non dovrebbe essere qui ($centerX,$centerY) = ${hex(center)}")
}

private fun isMagenta(rgb: Int): Boolean {
    val red = (rgb shr 16) and 0xFF
    val green = (rgb shr 8) and 0xFF
    val blue = rgb and 0xFF
    return red > 170 && blue > 170 && green < 120
}

private fun hex(rgb: Int): String = "0x${(rgb and 0xFFFFFF).toString(16).padStart(6, '0')}"
