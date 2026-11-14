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
        FfmpegEncoders.clearFailure()
        val candidates = runnableCandidates()
        if (candidates.isEmpty()) {
            val detail = FfmpegEncoders.lastFailure?.let { " Dettaglio: $it" }.orEmpty()
            throw FfmpegNotFoundException(FFMPEG_NOT_FOUND_MESSAGE + detail)
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
            FfmpegEncoders.recordFailure("avvio di $path: ${failure.message}")
            return false
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                FfmpegEncoders.recordFailure("timeout su $path -version")
                false
            } else if (process.exitValue() == 0) {
                true
            } else {
                FfmpegEncoders.recordFailure("exit ${process.exitValue()} su $path: ${output.take(500)}")
                false
            }
        } catch (failure: Exception) {
            process.destroyForcibly()
            FfmpegEncoders.recordFailure("avvio di $path: ${failure.message}")
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
                lastLoadError = "estratto ma non è un file: $path"
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
                            .ifBlank { "encoder $encoder assente" }
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
