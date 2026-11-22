package dev.grogdunn.kwalksnailosd

import dev.grogdunn.kwalksnailosd.clip.BatchBurner
import dev.grogdunn.kwalksnailosd.clip.BatchJobState
import dev.grogdunn.kwalksnailosd.clip.ClipList
import dev.grogdunn.kwalksnailosd.video.BurnResult
import dev.grogdunn.kwalksnailosd.video.OsdExportMode
import dev.grogdunn.kwalksnailosd.video.OsdOffset
import dev.grogdunn.kwalksnailosd.video.VideoCodec
import dev.grogdunn.kwalksnailosd.video.VideoInfo
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities
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

    @Test
    fun suggestedOsdExportNamesFollowModeAndFirstIncludedClip() {
        runBlocking {
            val directory = createTempDirectory("osd-name")
            val holder = folderHolder(mapOf("alpha.mp4" to clipVideo(), "beta.mp4" to clipVideo()))
            try {
                val video = directory.resolve("flight.mp4")
                video.writeText("x")
                holder.onFilePickerClosed(video)
                holder.setOsdExportMode(OsdExportMode.ChromaKey)
                assertEquals("flight_osd_chroma.mp4", holder.suggestedOsdExportName())
                holder.setOsdExportMode(OsdExportMode.ProRes4444)
                assertEquals("flight_osd.mov", holder.suggestedOsdExportName())
                holder.setOsdExportMode(OsdExportMode.Vp9Alpha)
                assertEquals("flight_osd.webm", holder.suggestedOsdExportName())
                holder.setOsdExportMode(OsdExportMode.PngSequence)
                assertEquals("flight_osd_png", holder.suggestedOsdExportName())

                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                for (name in listOf("beta", "alpha")) {
                    folder.resolve("$name.mp4").writeText("x")
                    writeOsd(folder.resolve("$name.osd"))
                }
                holder.onFolderPicked(folder)
                assertEquals("alpha_osd_png", holder.suggestedOsdExportName())
                holder.moveClipDown(entryId(requireNotNull(holder.state.clipList), "alpha"))
                assertEquals("beta_osd_png", holder.suggestedOsdExportName())
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun pngExportWithHugeEstimateSetsBurnErrorWithoutStarting() {
        runBlocking {
            val directory = createTempDirectory("png-space")
            val holder = AppStateHolder(
                probeVideo = { clipVideo(width = 16, height = 16, frameRate = 1.0).copy(frameNumber = 1) },
                pngRequiredBytes = { Long.MAX_VALUE },
            )
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                folder.resolve("tiny.mp4").writeText("x")
                writeOsd(folder.resolve("tiny.osd"))
                holder.onFolderPicked(folder)
                holder.onFontFilePickerClosed(fontResource("font_36x13878.png"))
                holder.parseSelectedFont()
                holder.setOsdExportMode(OsdExportMode.PngSequence)
                assertTrue(holder.canStartOsdExport())
                holder.startOsdExport(directory.resolve("out"))
                assertFalse(holder.state.isBurning)
                assertTrue(holder.state.burnError?.contains(Long.MAX_VALUE.toString()) == true)
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun onFilesPickedSelectsOnlyGivenClipsAndEnablesBatchMode() {
        runBlocking {
            val directory = createTempDirectory("files-pick")
            val holder = folderHolder(mapOf("a.mp4" to clipVideo(), "b.mp4" to clipVideo(), "c.mp4" to clipVideo()))
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                for (name in listOf("a", "b", "c")) {
                    folder.resolve("$name.mp4").writeText("x")
                    writeOsd(folder.resolve("$name.osd"))
                }
                holder.onFilesPicked(listOf(folder.resolve("c.mp4"), folder.resolve("a.mp4")))
                val list = requireNotNull(holder.state.clipList)
                assertTrue(holder.state.batchMode)
                assertEquals(listOf("a", "c"), list.entries.map { it.info.files.baseName })
                assertTrue(list.entries.all { it.info.osd != null && it.info.files.osd != null })
                assertEquals(emptyMap(), holder.state.batchJobs)
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun mismatchedResolutionsBlockJoinButNotBatch() {
        runBlocking {
            val directory = createTempDirectory("batch-mismatch")
            val holder = folderHolder(
                mapOf(
                    "a.mp4" to clipVideo(),
                    "b.mp4" to clipVideo(width = 1280, height = 720, frameRate = 30.0),
                ),
            )
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                for (name in listOf("a", "b")) {
                    folder.resolve("$name.mp4").writeText("x")
                    writeOsd(folder.resolve("$name.osd"))
                }
                holder.onFolderPicked(folder)
                holder.onFontFilePickerClosed(fontResource("font_36x13878.png"))
                holder.parseSelectedFont()
                assertFalse(holder.state.batchMode)
                assertFalse(holder.canStartBurn())
                holder.setBatchMode(true)
                assertTrue(holder.canStartBurn())
                assertTrue(holder.canStartBatch())
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun batchBurnMarksTheSecondClipFailedAndUsesDistinctOutputs() {
        val seen = mutableListOf<Path>()
        val directory = createTempDirectory("batch-run")
        val holder = AppStateHolder(
            probeVideo = { clipVideo() },
            batchBurnerFactory = recordingBurner("b.mp4", seen),
        )
        try {
            val folder = directory.resolve("clips")
            Files.createDirectories(folder)
            for (name in listOf("c", "a", "b")) {
                folder.resolve("$name.mp4").writeText("x")
                writeOsd(folder.resolve("$name.osd"))
            }
            holder.onFilesPicked(listOf(folder.resolve("a.mp4"), folder.resolve("b.mp4"), folder.resolve("c.mp4")))
            holder.onFontFilePickerClosed(fontResource("font_36x13878.png"))
            runBlocking { holder.parseSelectedFont() }
            val out = directory.resolve("out")
            Files.createDirectories(out)
            holder.startBatchBurn(out)
            awaitNotBurning(holder)
            val list = requireNotNull(holder.state.clipList)
            val jobs = holder.state.batchJobs
            assertTrue(jobs.getValue(entryId(list, "a")) is BatchJobState.Done)
            assertTrue(jobs.getValue(entryId(list, "b")) is BatchJobState.Failed)
            assertTrue(jobs.getValue(entryId(list, "c")) is BatchJobState.Done)
            assertTrue(holder.state.burnError?.contains("1") == true)
            assertEquals(
                listOf("a_with_osd.mp4", "b_with_osd.mp4", "c_with_osd.mp4"),
                seen.map { it.fileName.toString() },
            )
            assertTrue(seen.all { it.parent == out })
        } finally {
            holder.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun clipWithoutOsdFailsWhileTheOthersStillRun() {
        val seen = mutableListOf<Path>()
        val directory = createTempDirectory("batch-no-osd")
        val holder = AppStateHolder(
            probeVideo = { clipVideo() },
            batchBurnerFactory = recordingBurner(null, seen),
        )
        try {
            val folder = directory.resolve("clips")
            Files.createDirectories(folder)
            for (name in listOf("a", "b", "c")) folder.resolve("$name.mp4").writeText("x")
            writeOsd(folder.resolve("a.osd"))
            writeOsd(folder.resolve("c.osd"))
            holder.onFilesPicked(listOf(folder.resolve("a.mp4"), folder.resolve("b.mp4"), folder.resolve("c.mp4")))
            holder.onFontFilePickerClosed(fontResource("font_36x13878.png"))
            runBlocking { holder.parseSelectedFont() }
            holder.startBatchBurn(directory.resolve("out"))
            awaitNotBurning(holder)
            val list = requireNotNull(holder.state.clipList)
            assertTrue(holder.state.batchJobs.getValue(entryId(list, "b")) is BatchJobState.Failed)
            assertEquals(listOf("a_with_osd.mp4", "c_with_osd.mp4"), seen.map { it.fileName.toString() })
        } finally {
            holder.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun osdExportIsDisabledInBatchMode() {
        runBlocking {
            val directory = createTempDirectory("batch-osd-export")
            val holder = folderHolder(mapOf("a.mp4" to clipVideo()))
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                folder.resolve("a.mp4").writeText("x")
                writeOsd(folder.resolve("a.osd"))
                holder.onFolderPicked(folder)
                holder.onFontFilePickerClosed(fontResource("font_36x13878.png"))
                holder.parseSelectedFont()
                holder.setOsdExportMode(OsdExportMode.ChromaKey)
                assertTrue(holder.canStartOsdExport())
                holder.setBatchMode(true)
                assertFalse(holder.canStartOsdExport())
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun onFolderPickedKeepsBatchModeOff() {
        runBlocking {
            val directory = createTempDirectory("folder-not-batch")
            val holder = folderHolder(mapOf("a.mp4" to clipVideo(), "b.mp4" to clipVideo()))
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                for (name in listOf("a", "b")) {
                    folder.resolve("$name.mp4").writeText("x")
                    writeOsd(folder.resolve("$name.osd"))
                }
                holder.onFilesPicked(listOf(folder.resolve("a.mp4")))
                assertTrue(holder.state.batchMode)
                holder.onFolderPicked(folder)
                assertFalse(holder.state.batchMode)
                assertEquals(listOf("a", "b"), requireNotNull(holder.state.clipList).entries.map { it.info.files.baseName })
                assertEquals(emptyMap(), holder.state.batchJobs)
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun setClipOffsetAndEnableClipOffsetFollowTheGlobalOffset() {
        runBlocking {
            val directory = createTempDirectory("clip-offset")
            val holder = folderHolder(mapOf("a.mp4" to clipVideo()))
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                folder.resolve("a.mp4").writeText("x")
                writeOsd(folder.resolve("a.osd"))
                holder.onFolderPicked(folder)
                holder.setOsdOffset(3, 4)
                val id = entryId(requireNotNull(holder.state.clipList), "a")
                holder.enableClipOffset(id, true)
                assertEquals(OsdOffset(3, 4), holder.state.clipList!!.entries.first { it.id == id }.offset)
                holder.setClipOffset(id, OsdOffset(11, 22))
                assertEquals(OsdOffset(11, 22), holder.state.clipList!!.entries.first { it.id == id }.offset)
                holder.enableClipOffset(id, true)
                assertEquals(OsdOffset(11, 22), holder.state.clipList!!.entries.first { it.id == id }.offset)
                holder.enableClipOffset(id, false)
                assertNull(holder.state.clipList!!.entries.first { it.id == id }.offset)
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun canEditBurnOptionsNeedsAFontAndAValidClip() {
        runBlocking {
            val directory = createTempDirectory("edit-options")
            val holder = folderHolder(mapOf("a.mp4" to clipVideo()))
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                folder.resolve("a.mp4").writeText("x")
                writeOsd(folder.resolve("a.osd"))
                holder.onFolderPicked(folder)
                assertFalse(holder.canEditBurnOptions())
                holder.onFontFilePickerClosed(fontResource("font_36x13878.png"))
                holder.parseSelectedFont()
                assertTrue(holder.canEditBurnOptions())
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun previewClipUsesTheClipAndAFolderPickClearsIt() {
        runBlocking {
            val directory = createTempDirectory("clip-preview")
            val holder = folderHolder(mapOf("a.mp4" to clipVideo()))
            try {
                val folder = directory.resolve("clips")
                Files.createDirectories(folder)
                folder.resolve("a.mp4").writeText("x")
                writeOsd(folder.resolve("a.osd"))
                holder.onFolderPicked(folder)
                holder.onFontFilePickerClosed(fontResource("font_36x13878.png"))
                holder.parseSelectedFont()
                val options = holder.state.srtOptions
                val entry = requireNotNull(holder.state.clipList).entries.first()
                holder.previewClip(entry.id)
                assertEquals(entry.id, holder.state.previewClipId)
                assertEquals(entry.info.videoInfo, holder.state.videoInfo)
                assertEquals(entry.info.osd, holder.state.osd)
                assertNull(holder.state.selectedMp4)
                assertEquals(options, holder.state.srtOptions)
                holder.onFolderPicked(folder)
                assertNull(holder.state.previewClipId)
            } finally {
                holder.close()
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun batchBurnPassesPerClipOffsets() {
        val seen = mutableMapOf<String, Pair<Int, Int>>()
        val directory = createTempDirectory("batch-offset")
        val holder = AppStateHolder(
            probeVideo = { clipVideo() },
            batchBurnerFactory = {
                BatchBurner { job, _, settings, _, _, _, _ ->
                    seen[job.id] = settings.offsetX to settings.offsetY
                    BurnResult(job.output, "fake", hardware = false, framesWritten = 1, usedSoftwareFallback = false)
                }
            },
        )
        try {
            val folder = directory.resolve("clips")
            Files.createDirectories(folder)
            for (name in listOf("a", "b")) {
                folder.resolve("$name.mp4").writeText("x")
                writeOsd(folder.resolve("$name.osd"))
            }
            holder.onFilesPicked(listOf(folder.resolve("a.mp4"), folder.resolve("b.mp4")))
            holder.onFontFilePickerClosed(fontResource("font_36x13878.png"))
            runBlocking { holder.parseSelectedFont() }
            holder.setOsdOffset(3, 4)
            val list = requireNotNull(holder.state.clipList)
            holder.setClipOffset(entryId(list, "a"), OsdOffset(11, 22))
            holder.startBatchBurn(directory.resolve("out"))
            awaitNotBurning(holder)
            val updated = requireNotNull(holder.state.clipList)
            assertEquals(11 to 22, seen.getValue(entryId(updated, "a")))
            assertEquals(3 to 4, seen.getValue(entryId(updated, "b")))
        } finally {
            holder.close()
            directory.toFile().deleteRecursively()
        }
    }

    private fun folderHolder(infos: Map<String, VideoInfo>): AppStateHolder =
        AppStateHolder(probeVideo = { path -> infos.getValue(path.fileName.toString()) })

    private fun recordingBurner(failFile: String?, seen: MutableList<Path>): (VideoCodec) -> BatchBurner = {
        BatchBurner { job, _, _, _, _, _, _ ->
            seen.add(job.output)
            if (job.video.fileName.toString() == failFile) error("boom")
            BurnResult(job.output, "fake", hardware = false, framesWritten = 1, usedSoftwareFallback = false)
        }
    }

    private fun awaitNotBurning(holder: AppStateHolder) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            var burning = true
            SwingUtilities.invokeAndWait { burning = holder.state.isBurning }
            if (!burning) return
            Thread.sleep(20)
        }
        error("timed out waiting for burn")
    }

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
