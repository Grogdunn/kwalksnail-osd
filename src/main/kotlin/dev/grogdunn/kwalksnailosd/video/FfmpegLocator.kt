package dev.grogdunn.kwalksnailosd.video

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.ffmpeg_no_h264
import dev.grogdunn.kwalksnailosd.generated.resources.ffmpeg_no_h265
import dev.grogdunn.kwalksnailosd.generated.resources.ffmpeg_not_found
import dev.grogdunn.kwalksnailosd.generated.resources.ffmpeg_not_found_with_detail
import dev.grogdunn.kwalksnailosd.uiText
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.isExecutable

/**
 * Prima il binario ffmpeg delle librerie native JavaCV (`org.bytedeco:ffmpeg`),
 * lo stesso degli app image. Poi `ffmpeg` nel PATH.
 *
 * La build JavaCV è LGPL: ha `libopenh264` e non ha `libx264` né `libx265`.
 * Se un binario più avanti nel percorso ha `libx264` o `libx265`, viene preferito
 * perché è l'encoder del tool originale. Altrimenti H.264 usa `libopenh264`.
 */
class FfmpegLocator(
    private val bundled: () -> Path? = { loadBundledOrNull() },
    private val onPath: () -> Path? = { findOnPath() },
) {
    fun locateFor(codec: VideoCodec): Path {
        FfmpegEncoders.clearFailure()
        val candidates = runnableCandidates()
        if (candidates.isEmpty()) {
            val failure = FfmpegEncoders.lastFailure
            val message = if (failure == null) {
                uiText(Res.string.ffmpeg_not_found)
            } else {
                uiText(Res.string.ffmpeg_not_found_with_detail, failure)
            }
            throw FfmpegNotFoundException(message)
        }
        for (encoder in softwareEncoderNames(codec)) {
            for (candidate in candidates) {
                if (FfmpegEncoders.supports(candidate, encoder)) return candidate
            }
        }
        throw FfmpegNotFoundException(noSoftwareEncoderMessage(codec))
    }

    private fun runnableCandidates(): List<Path> =
        listOfNotNull(runCatching { bundled() }.getOrNull(), runCatching { onPath() }.getOrNull())
            .distinct()
            .filter { runs(it) }

    private fun runs(path: Path): Boolean {
        if (!Files.isRegularFile(path)) return false
        val process = try {
            ProcessBuilder(path.toString(), "-nostdin", "-hide_banner", "-version")
                .forBundledFfmpeg(path)
                .redirectErrorStream(true)
                .start()
        } catch (failure: Exception) {
            FfmpegEncoders.recordFailure("failed to start $path: ${failure.message}")
            return false
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                FfmpegEncoders.recordFailure("timeout running $path -version")
                false
            } else if (process.exitValue() == 0) {
                true
            } else {
                FfmpegEncoders.recordFailure("exit ${process.exitValue()} from $path: ${output.take(500)}")
                false
            }
        } catch (failure: Exception) {
            process.destroyForcibly()
            FfmpegEncoders.recordFailure("failed to start $path: ${failure.message}")
            false
        }
    }

    companion object {
        fun bundledExecutable(): Path? {
            val path = loadBundledOrNull() ?: return null
            return if (Files.isRegularFile(path)) path else null
        }

        @Volatile
        var lastLoadError: String? = null
            private set

        private fun loadBundledOrNull(): Path? = try {
            val path = Path.of(org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.ffmpeg::class.java))
            if (Files.isRegularFile(path)) {
                lastLoadError = null
                path
            } else {
                lastLoadError = "extracted but not a file: $path"
                null
            }
        } catch (failure: Throwable) {
            lastLoadError = failure.javaClass.simpleName + ": " + (failure.message ?: failure.toString())
            null
        }

        private fun findOnPath(): Path? {
            val fileName = if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) "ffmpeg.exe" else "ffmpeg"
            val pathEnv = System.getenv("PATH") ?: return null
            return pathEnv.split(java.io.File.pathSeparator)
                .asSequence()
                .filter { it.isNotEmpty() }
                .map { Path.of(it).resolve(fileName) }
                .firstOrNull { it.isExecutable() }
        }
    }
}

object FfmpegEncoders {
    var lastFailure: String? = null
        private set

    fun clearFailure() {
        lastFailure = null
    }

    fun recordFailure(message: String) {
        lastFailure = message
    }

    private val cache = mutableMapOf<Pair<String, String>, Boolean>()
    private val compiledCache = mutableMapOf<String, Set<String>>()

    fun compiledNames(ffmpeg: Path): Set<String> {
        val key = ffmpeg.toString()
        synchronized(compiledCache) {
            compiledCache[key]?.let { return it }
        }
        val names = readCompiledNames(ffmpeg)
        synchronized(compiledCache) { compiledCache[key] = names }
        return names
    }

    private fun readCompiledNames(ffmpeg: Path): Set<String> = try {
        val process = ProcessBuilder(ffmpeg.toString(), "-nostdin", "-hide_banner", "-encoders")
            .forBundledFfmpeg(ffmpeg)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            emptySet()
        } else if (process.exitValue() == 0) {
            encoderNamesFromListing(text)
        } else {
            emptySet()
        }
    } catch (failure: Exception) {
        recordFailure(failure.message ?: failure.toString())
        emptySet()
    }

    fun softwareNames(codec: VideoCodec): List<String> = when (codec) {
        VideoCodec.H264 -> listOf("libx264", "libopenh264")
        VideoCodec.H265 -> listOf("libx265")
    }

    fun firstSoftware(ffmpeg: Path, codec: VideoCodec): String? =
        softwareNames(codec).firstOrNull { supports(ffmpeg, it) }

    fun supports(ffmpeg: Path, encoder: String): Boolean {
        val key = ffmpeg.toString() to encoder
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val found = try {
            val process = ProcessBuilder(ffmpeg.toString(), "-nostdin", "-hide_banner", "-encoders")
                .forBundledFfmpeg(ffmpeg)
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            val listed = finished && process.exitValue() == 0 &&
                text.lineSequence().any { line -> line.trim().split(Regex("\\s+")).getOrNull(1) == encoder }
            if (!listed) {
                recordFailure(
                    if (!finished || process.exitValue() != 0) {
                        "exit=${if (finished) process.exitValue() else "timeout"} ${text.take(800)}"
                    } else {
                        text.lineSequence().filter { line -> line.contains("264") || line.contains("265") }.take(6)
                            .joinToString(" | ")
                            .ifBlank { "encoder $encoder missing" }
                    },
                )
            }
            listed
        } catch (failure: Exception) {
            lastFailure = failure.message
            false
        }
        synchronized(cache) { cache[key] = found }
        return found
    }
}

fun encoderNamesFromListing(text: String): Set<String> =
    text.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        val flags = parts.getOrNull(0) ?: return@mapNotNull null
        val name = parts.getOrNull(1) ?: return@mapNotNull null
        if (flags.length == 6 && flags.all { it == '.' || it.isLetter() } && name.all { it.isLetterOrDigit() || it == '_' }) {
            name
        } else {
            null
        }
    }.toSet()

fun softwareEncoderNames(codec: VideoCodec): List<String> = FfmpegEncoders.softwareNames(codec)

fun noSoftwareEncoderMessage(codec: VideoCodec): String = when (codec) {
    VideoCodec.H264 -> uiText(Res.string.ffmpeg_no_h264)
    VideoCodec.H265 -> uiText(Res.string.ffmpeg_no_h265)
}

class FfmpegNotFoundException(message: String) : RuntimeException(message)

/**
 * Il binario JavaCV ha RUNPATH `$ORIGIN`. `LD_LIBRARY_PATH` del processo (per esempio
 * quello di un runner) viene cercato prima e può caricare un'altra libav*.
 * Sul Linux lo sostituiamo con la directory del binario, così le librerie estratte vincono
 * e le librerie di sistema (PulseAudio, ALSA) restano nel percorso predefinito.
 */
fun ProcessBuilder.forBundledFfmpeg(binary: Path): ProcessBuilder = apply {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    if (!os.contains("linux") && !os.contains("nux")) return@apply
    val origin = binary.parent?.toAbsolutePath()?.toString() ?: return@apply
    environment().apply {
        remove("LD_PRELOAD")
        this["LD_LIBRARY_PATH"] = origin
    }
}
