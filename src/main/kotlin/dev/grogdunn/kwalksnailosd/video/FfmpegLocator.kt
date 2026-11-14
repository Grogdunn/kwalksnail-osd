package dev.grogdunn.kwalksnailosd.video

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
        val candidates = runnableCandidates()
        if (candidates.isEmpty()) throw FfmpegNotFoundException(FFMPEG_NOT_FOUND_MESSAGE)
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
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (_: Exception) {
            return false
        }
        return try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Exception) {
            process.destroyForcibly()
            false
        }
    }

    companion object {
        fun bundledExecutable(): Path? {
            val path = loadBundledOrNull() ?: return null
            return if (Files.isRegularFile(path)) path else null
        }

        private fun loadBundledOrNull(): Path? = try {
            Path.of(org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.ffmpeg::class.java))
        } catch (_: Throwable) {
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
    private val cache = mutableMapOf<Pair<String, String>, Boolean>()

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
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                text.lineSequence().any { line -> line.trim().split(Regex("\\s+")).getOrNull(1) == encoder }
            }
        } catch (_: Exception) {
            false
        }
        synchronized(cache) { cache[key] = found }
        return found
    }
}

fun softwareEncoderNames(codec: VideoCodec): List<String> = FfmpegEncoders.softwareNames(codec)

fun noSoftwareEncoderMessage(codec: VideoCodec): String = when (codec) {
    VideoCodec.H264 ->
        "Nessun encoder H.264 software. L'ffmpeg delle librerie JavaCV include libopenh264; " +
            "in alternativa serve un ffmpeg con libx264 nel PATH."
    VideoCodec.H265 ->
        "Nessun encoder H.265 software. L'ffmpeg delle librerie JavaCV, quello dei pacchetti, " +
            "non include libx265 (è una build LGPL e per H.264 usa libopenh264). " +
            "Installa ffmpeg con libx265, oppure esporta in H.264."
}

class FfmpegNotFoundException(message: String) : RuntimeException(message)

const val FFMPEG_NOT_FOUND_MESSAGE =
    "ffmpeg non è disponibile. L'export cerca prima il binario incluso nelle librerie native JavaCV " +
        "(lo stesso dei pacchetti dell'app) e poi il comando ffmpeg nel PATH. " +
        "Installa ffmpeg, oppure avvia l'app dal pacchetto che include quelle librerie."
