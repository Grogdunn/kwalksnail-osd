package dev.grogdunn.kwalksnailosd.clip

import dev.grogdunn.kwalksnailosd.osd.OsdParser
import dev.grogdunn.kwalksnailosd.osd.Position
import dev.grogdunn.kwalksnailosd.osd.font.CharacterSize
import dev.grogdunn.kwalksnailosd.osd.font.Font
import dev.grogdunn.kwalksnailosd.osd.font.FontType
import dev.grogdunn.kwalksnailosd.video.BurnEncoderPreference
import dev.grogdunn.kwalksnailosd.video.BurnSettings
import dev.grogdunn.kwalksnailosd.video.FfmpegEncoders
import dev.grogdunn.kwalksnailosd.video.OsdOffset
import dev.grogdunn.kwalksnailosd.video.FfmpegLocator
import dev.grogdunn.kwalksnailosd.video.VideoBurner
import dev.grogdunn.kwalksnailosd.video.VideoCodec
import dev.grogdunn.kwalksnailosd.video.VideoParser
import dev.grogdunn.kwalksnailosd.video.forBundledFfmpeg
import kotlinx.coroutines.CancellationException
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiClipBurnTest {
    @Test
    fun twoClipsJoinDurationAudioAndDistinctOsd() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val directory = createTempDirectory("multiclip-burn")
        val first = preparedClip(ffmpeg, directory, "one", withAudio = true, gridX = 1)
        val second = preparedClip(ffmpeg, directory, "two", withAudio = false, gridX = 3)
        val output = directory.resolve("joined.mp4")
        val messages = mutableListOf<String>()
        var maxFraction = 0f
        var previews = 0

        val result = MultiClipBurner(ffmpeg).burn(
            clips = listOf(first, second),
            font = magentaFont(),
            settings = softwareSettings(),
            output = output,
            onProgress = { progress ->
                messages += progress.message
                maxFraction = maxOf(maxFraction, progress.fraction)
            },
            onPreviewFrame = { previews += 1 },
        )

        assertTrue(output.exists())
        assertTrue(result.framesWritten > 0)
        assertTrue(previews >= 1, "preview frames: $previews")
        val firstClip = messages.indexOfFirst { it.contains("1/2") }
        val secondClip = messages.indexOfFirst { it.contains("2/2") }
        assertTrue(firstClip >= 0, messages.joinToString(" | "))
        assertTrue(secondClip > firstClip, messages.joinToString(" | "))
        assertTrue(maxFraction >= 0.999f, "fraction $maxFraction")
        VideoParser().use { parser ->
            val burned = parser.getVideoInfo(output)
            assertTrue(burned.hasAudio, "joined export must keep an audio track")
            assertTrue(
                abs(burned.duration.toMillis() - 2_000) < 300,
                "duration ${burned.duration.toMillis()} ms",
            )
        }

        val early = extractFrameAt(ffmpeg, output, seconds = 0.1, directory.resolve("early.png"))
        val late = extractFrameAt(ffmpeg, output, seconds = 1.2, directory.resolve("late.png"))
        val tile = CharacterSize.LARGE
        val clip1 = tileOrigin(gridX = 1, gridY = 0, tile = tile)
        val clip2 = tileOrigin(gridX = 3, gridY = 0, tile = tile)
        assertMagentaTile(early, clip1, tile)
        assertNotMagenta(early, clip2, tile)
        assertMagentaTile(late, clip2, tile)
        assertNotMagenta(late, clip1, tile)
    }

    @Test
    fun cancelDuringTheFirstClipDeletesTheOutput() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val directory = createTempDirectory("multiclip-cancel")
        val first = preparedClip(ffmpeg, directory, "one", withAudio = true, gridX = 1)
        val second = preparedClip(ffmpeg, directory, "two", withAudio = false, gridX = 3)
        val output = directory.resolve("joined.mp4")
        var reported = false

        assertFailsWith<CancellationException> {
            MultiClipBurner(ffmpeg, VideoBurner(ffmpeg)).burn(
                clips = listOf(first, second),
                font = magentaFont(),
                settings = softwareSettings(),
                output = output,
                onProgress = { reported = true },
                isCancelled = { reported },
            )
        }
        assertFalse(output.exists())
    }

    @Test
    fun secondClipOffsetShiftsItsTileWhileTheFirstKeepsTheGlobalOrigin() {
        val ffmpeg = FfmpegLocator().locateFor(VideoCodec.H264)
        val directory = createTempDirectory("multiclip-offset")
        val gridX = 1
        val tile = CharacterSize.LARGE
        val first = preparedClip(ffmpeg, directory, "one", withAudio = true, gridX = gridX)
        val second = preparedClip(ffmpeg, directory, "two", withAudio = false, gridX = gridX)
            .copy(offset = OsdOffset(tile.width * 2, 0))
        val output = directory.resolve("joined.mp4")

        MultiClipBurner(ffmpeg).burn(
            clips = listOf(first, second),
            font = magentaFont(),
            settings = softwareSettings(),
            output = output,
        )

        val early = extractFrameAt(ffmpeg, output, seconds = 0.1, directory.resolve("early.png"))
        val late = extractFrameAt(ffmpeg, output, seconds = 1.2, directory.resolve("late.png"))
        val original = tileOrigin(gridX = gridX, gridY = 0, tile = tile)
        val shifted = tileOrigin(gridX = gridX + 2, gridY = 0, tile = tile)
        assertMagentaTile(early, original, tile)
        assertMagentaTile(late, shifted, tile)
        assertNotMagenta(late, original, tile)
    }
}

internal fun softwareSettings() = BurnSettings(
    codec = VideoCodec.H264,
    bitrateMbps = 4,
    encoder = BurnEncoderPreference.Software,
    offsetX = 0,
    offsetY = 0,
    adjustPlaybackSpeed = false,
)

internal fun preparedClip(
    ffmpeg: Path,
    directory: Path,
    name: String,
    withAudio: Boolean,
    gridX: Int,
): PreparedClip {
    val video = directory.resolve("$name.mp4")
    renderTestSrc(ffmpeg, video, width = 160, height = 90, fps = 10, seconds = 1, withAudio = withAudio)
    val videoInfo = VideoParser().use { it.getVideoInfo(video) }
    val osd = OsdParser().parse(writeOsd(directory.resolve("$name.osd"), gridX))
    return PreparedClip(video, videoInfo, osd)
}

private fun renderTestSrc(
    ffmpeg: Path,
    output: Path,
    width: Int,
    height: Int,
    fps: Int,
    seconds: Int,
    withAudio: Boolean,
) {
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
        "color=c=black:s=${width}x${height}:r=$fps",
    )
    if (withAudio) {
        command += listOf("-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100")
    }
    command += listOf("-t", seconds.toString())
    if (withAudio) command += "-shortest"
    val encoder = FfmpegEncoders.firstSoftware(ffmpeg, VideoCodec.H264)
        ?: error("no H.264 encoder in $ffmpeg")
    command += listOf("-c:v", encoder, "-pix_fmt", "yuv420p")
    if (withAudio) command += listOf("-c:a", "aac")
    command += output.toString()
    runFfmpeg(command)
}

private fun extractFrameAt(ffmpeg: Path, video: Path, seconds: Double, png: Path): BufferedImage {
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
            "-ss",
            String.format(Locale.US, "%.3f", seconds),
            "-frames:v",
            "1",
            png.toString(),
        ),
    )
    return ImageIO.read(png.toFile())
}

private fun runFfmpeg(command: List<String>) {
    val process = ProcessBuilder(command).forBundledFfmpeg(Path.of(command.first())).redirectErrorStream(true).start()
    val log = process.inputStream.readBytes().decodeToString()
    val code = process.waitFor()
    if (code != 0) error("ffmpeg exit $code\n$log")
}

private fun writeOsd(path: Path, gridX: Int): Path {
    val gridWidth = 53
    val gridHeight = 20
    val bytes = ByteArray(40 + 4 + gridWidth * gridHeight * 2)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("BTFL".toByteArray(Charsets.US_ASCII))
    buffer.position(40)
    buffer.putInt(0)
    val cells = IntArray(gridWidth * gridHeight)
    cells[gridX] = 7
    for (cell in cells) buffer.putShort(cell.toShort())
    Files.write(path, bytes)
    return path
}

internal fun magentaFont(): Font {
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

private fun tileOrigin(gridX: Int, gridY: Int, tile: CharacterSize): Position =
    Position(gridX * tile.width, gridY * tile.height)

private fun assertMagentaTile(image: BufferedImage, origin: Position, tile: CharacterSize) {
    val centerX = origin.x + tile.width / 2
    val centerY = origin.y + tile.height / 2
    val center = image.getRGB(centerX, centerY)
    assertTrue(isMagenta(center), "OSD pixel ($centerX,$centerY) = ${hex(center)}")
    val outside = image.getRGB(origin.x - 4, centerY)
    assertFalse(isMagenta(outside), "pixel outside the tile (${origin.x - 4},$centerY) = ${hex(outside)}")
}

private fun assertNotMagenta(image: BufferedImage, origin: Position, tile: CharacterSize) {
    val centerX = origin.x + tile.width / 2
    val centerY = origin.y + tile.height / 2
    val center = image.getRGB(centerX, centerY)
    assertFalse(isMagenta(center), "glyph should not be here ($centerX,$centerY) = ${hex(center)}")
}

private fun isMagenta(rgb: Int): Boolean {
    val red = (rgb shr 16) and 0xFF
    val green = (rgb shr 8) and 0xFF
    val blue = rgb and 0xFF
    return red > 170 && blue > 170 && green < 120
}

private fun hex(rgb: Int): String = "0x${(rgb and 0xFFFFFF).toString(16).padStart(6, '0')}"
