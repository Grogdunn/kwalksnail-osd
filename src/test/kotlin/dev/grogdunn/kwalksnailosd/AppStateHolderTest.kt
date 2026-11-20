package dev.grogdunn.kwalksnailosd

import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun fontResource(name: String): Path {
        val resource = requireNotNull(javaClass.getResource("/fonts/$name")) { "Missing test font $name" }
        return Path.of(resource.toURI())
    }
}
