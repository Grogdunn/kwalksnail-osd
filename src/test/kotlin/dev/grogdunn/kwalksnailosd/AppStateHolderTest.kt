package dev.grogdunn.kwalksnailosd

import dev.grogdunn.kwalksnailosd.clip.ClipList
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AppStateHolderTest {
    @Test
    fun invalidFontIsShownAsAnErrorAndLeavesTheAppUsable() {
        runBlocking {
            val directory = createTempDirectory("font-ui")
            val holder = AppStateHolder()
            try {
                val bad = directory.resolve("bad.png")
                ImageIO.write(BufferedImage(37, 80, BufferedImage.TYPE_INT_ARGB), "png", bad.toFile())

                holder.onFontFilePickerClosed(bad)
                holder.parseSelectedFont()

                assertEquals(bad, holder.state.selectedFont)
                assertNull(holder.state.font)
                assertTrue(holder.state.fontError?.contains("37") == true)

                val valid = fontResource("font_36x13878.png")
                holder.onFontFilePickerClosed(valid)
                holder.parseSelectedFont()

                assertNotNull(holder.state.font)
                assertEquals(256, holder.state.font?.characterCount)
                assertNull(holder.state.fontError)

                holder.onFontFilePickerClosed(bad)
                holder.parseSelectedFont()

                assertNull(holder.state.font)
                assertNotNull(holder.state.fontError)
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun unreadableMp4SetsVideoErrorAndClearsVideoInfo() {
        runBlocking {
            val directory = createTempDirectory("video-ui")
            val holder = AppStateHolder()
            try {
                val bad = directory.resolve("notes.mp4")
                bad.writeText("this is not a video")

                holder.onFilePickerClosed(bad)
                assertNull(holder.state.videoError)
                holder.parseSelectedMp4()

                assertEquals(bad, holder.state.selectedMp4)
                assertNull(holder.state.videoInfo)
                assertNotNull(holder.state.videoError)

                val other = directory.resolve("other.mp4")
                other.writeText("still not a video")
                holder.onFilePickerClosed(other)
                assertEquals(other, holder.state.selectedMp4)
                assertNull(holder.state.videoInfo)
                assertNull(holder.state.videoError)

                holder.parseSelectedMp4()
                assertNull(holder.state.videoInfo)
                assertNotNull(holder.state.videoError)
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun pickingAFolderFillsClipListAndAFilePickClearsIt() {
        runBlocking {
            val directory = createTempDirectory("folder-ui")
            val holder = folderHolder(mapOf("a.mp4" to clipVideo(), "b.mp4" to clipVideo()))
            try {
                val stray = directory.resolve("solo.mp4").also { it.writeText("x") }
                holder.onFilePickerClosed(stray)
                holder.setOsdOffset(3, 4)
                holder.onFontFilePickerClosed(fontResource("font_36x13878.png"))
                holder.parseSelectedFont()

                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                folder.resolve("b.mp4").writeText("x")
                folder.resolve("a.mp4").writeText("x")
                writeOsd(folder.resolve("a.osd"))
                folder.resolve("b.osd").writeText("x")
                folder.resolve("a.srt").writeText("x")

                holder.onFolderPicked(null)
                assertEquals(stray, holder.state.selectedMp4)

                holder.onFolderPicked(folder)
                assertNull(holder.state.selectedMp4)
                assertNull(holder.state.videoInfo)
                assertNull(holder.state.videoError)
                assertNull(holder.state.clipFolderError)
                assertEquals(3, holder.state.osdOffsetX)
                assertNotNull(holder.state.font)
                val list = requireNotNull(holder.state.clipList)
                assertEquals(listOf("a", "b"), list.entries.map { it.info.files.baseName })
                assertNotNull(list.entries[0].info.osd)
                assertNotNull(list.entries[0].info.srtError)
                assertNull(holder.state.srt)
                assertNull(list.entries[1].info.osd)
                assertNotNull(list.entries[1].info.osdError)

                holder.onFilePickerClosed(stray)
                assertNull(holder.state.clipList)
                assertNull(holder.state.clipFolderError)
                assertEquals(stray, holder.state.selectedMp4)
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun folderWithParsedSrtEnablesOverlayFlags() {
        runBlocking {
            val directory = createTempDirectory("folder-srt")
            val holder = folderHolder(mapOf("a.mp4" to clipVideo(), "b.mp4" to clipVideo()))
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                folder.resolve("a.mp4").writeText("x")
                folder.resolve("b.mp4").writeText("x")
                writeOsd(folder.resolve("a.osd"))
                writeOsd(folder.resolve("b.osd"))
                folder.resolve("a.srt").writeText(srtCue("CH:1 MCS:4 GSNR:25.9 SSNR:17.8 Delay:31ms"))
                folder.resolve("b.srt").writeText(
                    srtCue("Signal:4 CH:1 FlightTime:1 SBat:16.1V GBat:11.2V Delay:31ms Bitrate:25Mbps Distance:7m"),
                )

                holder.onFolderPicked(folder)

                val srt = requireNotNull(holder.state.srt)
                assertTrue(srt.hasDebugFields)
                assertTrue(srt.hasDistance)
                assertTrue(holder.state.srtOptions.showMcs)
                assertTrue(holder.state.srtOptions.showSnr)
                assertFalse(holder.state.srtOptions.showSignal)
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun excludeMoveDownAndRestoreUpdateClipListAndSuggestedName() {
        runBlocking {
            val directory = createTempDirectory("folder-edit")
            val holder = folderHolder(
                mapOf("alpha.mp4" to clipVideo(), "beta.mp4" to clipVideo(), "gamma.mp4" to clipVideo()),
            )
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                for (name in listOf("gamma", "alpha", "beta")) {
                    folder.resolve("$name.mp4").writeText("x")
                    writeOsd(folder.resolve("$name.osd"))
                }
                holder.onFolderPicked(folder)
                val list = requireNotNull(holder.state.clipList)
                val alpha = list.entries[0]
                val beta = list.entries[1]
                assertEquals(listOf("alpha", "beta", "gamma"), list.entries.map { it.info.files.baseName })
                assertEquals("alpha_joined.mp4", holder.suggestedOutputName())

                holder.moveClipDown(alpha.id)
                assertEquals(listOf("beta", "alpha", "gamma"), holder.state.clipList!!.entries.map { it.info.files.baseName })
                assertEquals("beta_joined.mp4", holder.suggestedOutputName())

                holder.setClipExcluded(beta.id, true)
                assertTrue(holder.state.clipList!!.entries.first { it.id == beta.id }.excluded)
                assertEquals("alpha_joined.mp4", holder.suggestedOutputName())

                holder.restoreClipOrder()
                val restored = requireNotNull(holder.state.clipList)
                assertEquals(listOf("alpha", "beta", "gamma"), restored.entries.map { it.info.files.baseName })
                assertEquals(list.naturalOrderIds, restored.naturalOrderIds)
                assertTrue(restored.entries.first { it.id == beta.id }.excluded)
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun canStartBurnRequiresMatchingParsedOsdAndAFont() {
        runBlocking {
            val directory = createTempDirectory("folder-burn")
            val matching = clipVideo()
            val holder = folderHolder(
                mapOf(
                    "alpha.mp4" to matching,
                    "bad.mp4" to matching,
                    "beta.mp4" to matching,
                    "mismatch.mp4" to clipVideo(width = 1280, height = 720, frameRate = 30.0),
                    "missing.mp4" to matching,
                ),
            )
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                for (name in listOf("alpha", "bad", "beta", "mismatch", "missing")) {
                    folder.resolve("$name.mp4").writeText("x")
                }
                writeOsd(folder.resolve("alpha.osd"))
                writeOsd(folder.resolve("beta.osd"))
                writeOsd(folder.resolve("mismatch.osd"))
                folder.resolve("bad.osd").writeText("x")

                holder.onFolderPicked(folder)
                val list = requireNotNull(holder.state.clipList)
                assertFalse(holder.canStartBurn())

                holder.setClipExcluded(entryId(list, "mismatch"), true)
                assertFalse(holder.canStartBurn())
                holder.setClipExcluded(entryId(list, "missing"), true)
                assertFalse(holder.canStartBurn())
                holder.onFontFilePickerClosed(fontResource("font_36x13878.png"))
                holder.parseSelectedFont()
                assertFalse(holder.canStartBurn())
                assertNotNull(list.entries.first { it.info.files.baseName == "bad" }.info.osdError)

                holder.setClipExcluded(entryId(list, "bad"), true)
                assertTrue(holder.canStartBurn())
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    private fun folderHolder(infos: Map<String, VideoInfo>): AppStateHolder =
        AppStateHolder(probeVideo = { path -> infos.getValue(path.fileName.toString()) })

    private fun entryId(list: ClipList, baseName: String): String =
        list.entries.first { it.info.files.baseName == baseName }.id

    private fun clipVideo(width: Int = 1920, height: Int = 1080, frameRate: Double = 60.0) = VideoInfo(
        height = height,
        width = width,
        codec = "h264",
        duration = Duration.ofSeconds(1),
        frameRate = frameRate,
        frameNumber = frameRate.toInt(),
        hasAudio = false,
    )

    private fun srtCue(body: String) = "1\n00:00:00,000 --> 00:00:00,400\n$body\n"

    private fun writeOsd(path: Path) {
        val gridWidth = 53
        val gridHeight = 20
        val bytes = ByteArray(40 + 4 + gridWidth * gridHeight * 2)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("BTFL".toByteArray(Charsets.US_ASCII))
        buffer.position(40)
        buffer.putInt(0)
        repeat(gridWidth * gridHeight) { buffer.putShort(0) }
        path.writeBytes(bytes)
    }

    private fun fontResource(name: String): Path {
        val resource = requireNotNull(javaClass.getResource("/fonts/$name")) { "Missing test font $name" }
        return Path.of(resource.toURI())
    }
}
