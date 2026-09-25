package dev.grogdunn.kwalksnailosd

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class AppStateHolderTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `selecting a file discovers related files and keeps existing selections`() {
        val mp4 = Files.createFile(tempDirectory.resolve("first.mp4"))
        val osd = Files.createFile(tempDirectory.resolve("first.osd"))
        val srt = Files.createFile(tempDirectory.resolve("first.srt"))
        val secondOsd = Files.createFile(tempDirectory.resolve("second.osd"))
        val stateHolder = AppStateHolder()

        stateHolder.onFilePickerClosed(mp4)

        assertEquals(AppUiState(mp4, osd, srt), stateHolder.state)

        stateHolder.onFilePickerClosed(secondOsd)

        assertEquals(AppUiState(mp4, osd, srt), stateHolder.state)
    }

    @Test
    fun `cancelling file picker leaves state unchanged`() {
        val stateHolder = AppStateHolder()

        stateHolder.onFilePickerClosed(null)

        assertEquals(AppUiState(), stateHolder.state)
    }
}
