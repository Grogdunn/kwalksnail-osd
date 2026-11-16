package dev.grogdunn.kwalksnailosd.video

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.burn_both_encoders_failed
import dev.grogdunn.kwalksnailosd.generated.resources.burn_no_ffmpeg_detail
import dev.grogdunn.kwalksnailosd.generated.resources.export_encoder_amf
import dev.grogdunn.kwalksnailosd.generated.resources.export_encoder_none
import dev.grogdunn.kwalksnailosd.generated.resources.export_encoder_nvenc
import dev.grogdunn.kwalksnailosd.generated.resources.export_encoder_qsv
import dev.grogdunn.kwalksnailosd.generated.resources.export_encoder_unavailable
import dev.grogdunn.kwalksnailosd.generated.resources.export_encoder_vaapi
import dev.grogdunn.kwalksnailosd.generated.resources.export_encoder_videotoolbox
import dev.grogdunn.kwalksnailosd.uiText
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Encoder hardware del Burn, limitati a ciò che gli ffmpeg del percorso sanno davvero eseguire.
 *
 * Il binario JavaCV `org.bytedeco:ffmpeg:8.1.2-1.5.14` (build LGPL, senza il classifier `gpl`)
 * è stato controllato sui jar pubblicati:
 * - linux-x86_64, eseguito: `h264_nvenc`/`hevc_nvenc`, `h264_vaapi`/`hevc_vaapi` (libva nel jar),
 *   `h264_qsv`/`hevc_qsv` (libmfx, a runtime cerca `libmfxhw64.so.1`). Niente AMF né VideoToolbox.
 *   Niente `libx264`/`libx265`; H.264 software è `libopenh264`.
 * - windows-x86_64, stringhe in `avcodec-62.dll`: NVENC e QSV. Niente AMF, VAAPI, VideoToolbox.
 * - macosx-arm64, stringhe in `libavcodec.62.dylib`: `h264_videotoolbox`/`hevc_videotoolbox`.
 *   Niente NVENC, QSV, VAAPI, AMF.
 *
 * AMF (`h264_amf`/`hevc_amf`) non è nella build JavaCV né nell'ffmpeg di Ubuntu. Resta nel
 * selettore solo se l'ffmpeg scelto per il codec lo elenca e una prova di codifica riesce:
 * succede con un ffmpeg di terze parti nel PATH che ha anche `libx264`/`libx265`, perché
 * quello viene preferito al binario JavaCV. Su Linux AMD il percorso reale è VAAPI.
 * V4L2 mem2mem e Vulkan sono compilati nel binario Linux e non sono offerti: non sono
 * encoder desktop generali e non erano richiesti.
 *
 * Il nome nell'elenco `-encoders` non basta: senza driver o senza `/dev/dri` la prova fallisce
 * e l'encoder non compare. L'esito resta in cache per il processo.
 */

private val logger = KotlinLogging.logger {}

sealed class BurnEncoderPreference {
    data object Auto : BurnEncoderPreference()
    data object Software : BurnEncoderPreference()
    data class Hardware(val kind: HardwareKind) : BurnEncoderPreference()
}

enum class HardwareKind {
    Nvenc,
    Amf,
    Vaapi,
    Qsv,
    VideoToolbox,
    ;

    fun labelResource(): StringResource = when (this) {
        Nvenc -> Res.string.export_encoder_nvenc
        Amf -> Res.string.export_encoder_amf
        Vaapi -> Res.string.export_encoder_vaapi
        Qsv -> Res.string.export_encoder_qsv
        VideoToolbox -> Res.string.export_encoder_videotoolbox
    }

    fun label(): String = uiText(labelResource())

    fun ffmpegName(codec: VideoCodec): String = when (this) {
        Nvenc -> if (codec == VideoCodec.H264) "h264_nvenc" else "hevc_nvenc"
        Amf -> if (codec == VideoCodec.H264) "h264_amf" else "hevc_amf"
        Vaapi -> if (codec == VideoCodec.H264) "h264_vaapi" else "hevc_vaapi"
        Qsv -> if (codec == VideoCodec.H264) "h264_qsv" else "hevc_qsv"
        VideoToolbox -> if (codec == VideoCodec.H264) "h264_videotoolbox" else "hevc_videotoolbox"
    }
}

enum class RateControl {
    AverageBitrate,
    NvencVbr,
    VaapiVbr,
    QsvBitrate,
    AmfPeak,
    VideoToolboxBitrate,
}

data class EncoderRecipe(
    val name: String,
    val hardware: Boolean,
    val kind: HardwareKind?,
    val globalArgs: List<String> = emptyList(),
    val videoFilter: String? = null,
    val pixelFormat: String?,
    val rateControl: RateControl,
    val extraArgs: List<String> = emptyList(),
    val fileArgs: List<String> = emptyList(),
)

data class EncoderSelection(
    val chosen: EncoderRecipe,
    val softwareFallback: EncoderRecipe?,
    val fallbackNote: String?,
)

object EncoderProbeCache {
    private val values = ConcurrentHashMap<String, Boolean>()

    fun get(key: String): Boolean? = values[key]

    /** Restituisce il valore già presente, oppure null se [value] è stato inserito. */
    fun putIfAbsent(key: String, value: Boolean): Boolean? = values.putIfAbsent(key, value)

    fun clear() {
        values.clear()
    }
}

fun hardwareAutoOrder(osName: String): List<HardwareKind> {
    val os = osName.lowercase()
    return when {
        os.contains("mac") -> listOf(
            HardwareKind.VideoToolbox,
            HardwareKind.Nvenc,
            HardwareKind.Qsv,
            HardwareKind.Vaapi,
            HardwareKind.Amf,
        )
        os.contains("win") -> listOf(
            HardwareKind.Nvenc,
            HardwareKind.Qsv,
            HardwareKind.Amf,
            HardwareKind.Vaapi,
            HardwareKind.VideoToolbox,
        )
        else -> listOf(
            HardwareKind.Nvenc,
            HardwareKind.Vaapi,
            HardwareKind.Qsv,
            HardwareKind.Amf,
            HardwareKind.VideoToolbox,
        )
    }
}

fun rateArguments(rate: RateControl, bitrateMbps: Int): List<String> {
    val target = "${bitrateMbps}M"
    val buffer = "${bitrateMbps * 2}M"
    return when (rate) {
        RateControl.AverageBitrate -> listOf("-b:v", target)
        RateControl.NvencVbr -> listOf(
            "-rc", "vbr",
            "-b:v", target,
            "-maxrate", target,
            "-bufsize", buffer,
            "-preset", "p4",
            "-tune", "hq",
        )
        RateControl.VaapiVbr -> listOf("-rc_mode", "VBR", "-b:v", target, "-maxrate", target)
        RateControl.QsvBitrate -> listOf(
            "-b:v", target,
            "-maxrate", target,
            "-bufsize", buffer,
            "-preset", "medium",
        )
        RateControl.AmfPeak -> listOf(
            "-usage", "transcoding",
            "-quality", "balanced",
            "-rc", "vbr_peak",
            "-b:v", target,
            "-maxrate", target,
        )
        RateControl.VideoToolboxBitrate -> listOf(
            "-b:v", target,
            "-allow_sw", "0",
            "-constant_bit_rate", "1",
        )
    }
}

/** Argomenti di output, dal filtro fino al tag del contenitore. La prova non include [EncoderRecipe.fileArgs]. */
fun encoderOutputArgs(recipe: EncoderRecipe, bitrateMbps: Int, includeFileArgs: Boolean): List<String> {
    val args = mutableListOf<String>()
    if (recipe.videoFilter != null) {
        args += "-vf"
        args += recipe.videoFilter
    }
    args += "-c:v"
    args += recipe.name
    if (recipe.pixelFormat != null) {
        args += "-pix_fmt"
        args += recipe.pixelFormat
    }
    args += rateArguments(recipe.rateControl, bitrateMbps)
    args += recipe.extraArgs
    if (includeFileArgs) args += recipe.fileArgs
    return args
}

fun probeCommand(ffmpeg: Path, recipe: EncoderRecipe): List<String> = buildList {
    add(ffmpeg.toString())
    add("-nostdin")
    add("-hide_banner")
    add("-loglevel")
    add("error")
    addAll(recipe.globalArgs)
    add("-f")
    add("lavfi")
    add("-i")
    add("nullsrc=s=256x256:r=30:d=0.1")
    add("-frames:v")
    add("2")
    addAll(encoderOutputArgs(recipe, bitrateMbps = 1, includeFileArgs = false))
    add("-f")
    add("null")
    add("-")
}

fun hardwareCandidates(
    kind: HardwareKind,
    codec: VideoCodec,
    compiled: Set<String>,
    renderNodes: List<String>,
): List<EncoderRecipe> {
    val name = kind.ffmpegName(codec)
    if (name !in compiled) return emptyList()
    return when (kind) {
        HardwareKind.Nvenc -> listOf(nvencRecipe(codec, name))
        HardwareKind.Amf -> listOf(amfRecipe(codec, name))
        HardwareKind.VideoToolbox -> listOf(videoToolboxRecipe(codec, name))
        HardwareKind.Vaapi -> {
            val devices = renderNodes.ifEmpty { listOf("/dev/dri/renderD128") }
            devices.map { vaapiRecipe(codec, name, it) }
        }
        HardwareKind.Qsv -> {
            val devices = renderNodes.map { qsvDeviceRecipe(codec, name, it) }
            devices + qsvSystemMemoryRecipe(codec, name)
        }
    }
}

fun softwareRecipe(name: String): EncoderRecipe = EncoderRecipe(
    name = name,
    hardware = false,
    kind = null,
    pixelFormat = "yuv420p",
    rateControl = RateControl.AverageBitrate,
)

class HardwareEncoderCatalog(
    private val ffmpeg: Path,
    private val compiled: (Path) -> Set<String> = { path -> FfmpegEncoders.compiledNames(path) },
    private val probe: (EncoderRecipe) -> Boolean = { recipe -> runEncoderProbe(ffmpeg, recipe) },
    private val renderNodes: () -> List<String> = ::defaultRenderNodes,
    private val softwareName: (VideoCodec) -> String? = { codec -> FfmpegEncoders.firstSoftware(ffmpeg, codec) },
    private val osName: String = System.getProperty("os.name").orEmpty(),
) {
    fun working(codec: VideoCodec): List<EncoderRecipe> {
        val names = compiled(ffmpeg)
        val nodes = renderNodes()
        return hardwareAutoOrder(osName).mapNotNull { kind ->
            hardwareCandidates(kind, codec, names, nodes).firstOrNull { probeCached(it) }
        }
    }

    fun resolve(codec: VideoCodec, preference: BurnEncoderPreference): EncoderSelection {
        val software = softwareName(codec)?.let(::softwareRecipe)
            ?: throw VideoBurnException(noSoftwareEncoderMessage(codec))
        if (preference is BurnEncoderPreference.Software) {
            return EncoderSelection(software, softwareFallback = null, fallbackNote = null)
        }
        val available = working(codec)
        val chosen = when (preference) {
            BurnEncoderPreference.Auto -> available.firstOrNull()
            is BurnEncoderPreference.Hardware -> available.firstOrNull { it.kind == preference.kind }
            BurnEncoderPreference.Software -> null
        }
        return if (chosen != null) {
            EncoderSelection(chosen, softwareFallback = software, fallbackNote = null)
        } else {
            val note = when (preference) {
                is BurnEncoderPreference.Hardware -> uiText(Res.string.export_encoder_unavailable, preference.kind.label())
                else -> uiText(Res.string.export_encoder_none)
            }
            EncoderSelection(software, softwareFallback = null, fallbackNote = note)
        }
    }

    private fun probeCached(recipe: EncoderRecipe): Boolean {
        val key = probeKey(ffmpeg, recipe)
        EncoderProbeCache.get(key)?.let { return it }
        val result = try {
            probe(recipe)
        } catch (failure: Exception) {
            logger.info(failure) { "Probe of ${recipe.name} was not run" }
            false
        }
        return EncoderProbeCache.putIfAbsent(key, result) ?: result
    }
}

fun probeKey(ffmpeg: Path, recipe: EncoderRecipe): String = listOf(
    ffmpeg.toString(),
    recipe.name,
    recipe.globalArgs.joinToString("\u0000"),
    recipe.videoFilter.orEmpty(),
    recipe.pixelFormat.orEmpty(),
    recipe.rateControl.name,
    recipe.extraArgs.joinToString("\u0000"),
).joinToString("|")

fun <T> encodeWithFallback(
    primary: EncoderRecipe,
    fallback: EncoderRecipe?,
    encode: (EncoderRecipe) -> T,
    onFallback: (failed: EncoderRecipe, next: EncoderRecipe, error: Exception) -> Unit,
): T {
    try {
        return encode(primary)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (!primary.hardware || fallback == null) throw error
        onFallback(primary, fallback, error)
        try {
            return encode(fallback)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (softwareError: Exception) {
            throw VideoBurnException(
                uiText(
                    Res.string.burn_both_encoders_failed,
                    primary.name,
                    error.message ?: uiText(Res.string.burn_no_ffmpeg_detail),
                    fallback.name,
                    softwareError.message ?: uiText(Res.string.burn_no_ffmpeg_detail),
                ),
                softwareError,
            )
        }
    }
}

internal fun defaultRenderNodes(dri: Path = Path.of("/dev/dri")): List<String> {
    if (!Files.isDirectory(dri)) return emptyList()
    return try {
        Files.list(dri).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it.startsWith("renderD") }
                .sorted()
                .map { dri.resolve(it).toString() }
                .toList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun runEncoderProbe(ffmpeg: Path, recipe: EncoderRecipe): Boolean {
    val command = probeCommand(ffmpeg, recipe)
    val process = try {
        ProcessBuilder(command).forBundledFfmpeg(ffmpeg).redirectErrorStream(true).start()
    } catch (failure: Exception) {
        logger.info(failure) { "Probe of ${recipe.name} did not start" }
        return false
    }
    val output = StringBuilder()
    val reader = thread(isDaemon = true, name = "ffmpeg-probe") {
        try {
            process.inputStream.bufferedReader().use { stream ->
                val chunk = CharArray(1024)
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    if (output.length < 4_000) output.append(chunk, 0, read)
                }
            }
        } catch (_: Exception) {
        }
    }
    return try {
        val finished = process.waitFor(8, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            logger.info { "Probe of ${recipe.name} timed out" }
            false
        } else if (process.exitValue() == 0) {
            true
        } else {
            logger.info { "Probe of ${recipe.name} failed: ${output.toString().trim().take(500)}" }
            false
        }
    } catch (failure: Exception) {
        process.destroyForcibly()
        logger.info(failure) { "Probe of ${recipe.name} was interrupted" }
        false
    } finally {
        reader.join(500)
    }
}

private fun nvencRecipe(codec: VideoCodec, name: String) = EncoderRecipe(
    name = name,
    hardware = true,
    kind = HardwareKind.Nvenc,
    pixelFormat = "nv12",
    rateControl = RateControl.NvencVbr,
    extraArgs = h264Profile(codec),
    fileArgs = hevcFileArgs(codec),
)

private fun amfRecipe(codec: VideoCodec, name: String) = EncoderRecipe(
    name = name,
    hardware = true,
    kind = HardwareKind.Amf,
    pixelFormat = "nv12",
    rateControl = RateControl.AmfPeak,
    extraArgs = h264Profile(codec),
    fileArgs = hevcFileArgs(codec),
)

private fun videoToolboxRecipe(codec: VideoCodec, name: String) = EncoderRecipe(
    name = name,
    hardware = true,
    kind = HardwareKind.VideoToolbox,
    pixelFormat = "nv12",
    rateControl = RateControl.VideoToolboxBitrate,
    extraArgs = h264Profile(codec),
    fileArgs = hevcFileArgs(codec),
)

private fun vaapiRecipe(codec: VideoCodec, name: String, device: String) = EncoderRecipe(
    name = name,
    hardware = true,
    kind = HardwareKind.Vaapi,
    globalArgs = listOf("-vaapi_device", device),
    videoFilter = "format=nv12,hwupload=extra_hw_frames=64",
    pixelFormat = null,
    rateControl = RateControl.VaapiVbr,
    extraArgs = h264Profile(codec),
    fileArgs = hevcFileArgs(codec),
)

private fun qsvDeviceRecipe(codec: VideoCodec, name: String, device: String) = EncoderRecipe(
    name = name,
    hardware = true,
    kind = HardwareKind.Qsv,
    globalArgs = listOf(
        "-init_hw_device", "vaapi=va:$device",
        "-init_hw_device", "qsv=hw@va",
        "-filter_hw_device", "hw",
    ),
    videoFilter = "format=nv12,hwupload=extra_hw_frames=64",
    pixelFormat = null,
    rateControl = RateControl.QsvBitrate,
    extraArgs = h264Profile(codec),
    fileArgs = hevcFileArgs(codec),
)

private fun qsvSystemMemoryRecipe(codec: VideoCodec, name: String) = EncoderRecipe(
    name = name,
    hardware = true,
    kind = HardwareKind.Qsv,
    pixelFormat = "nv12",
    rateControl = RateControl.QsvBitrate,
    extraArgs = h264Profile(codec),
    fileArgs = hevcFileArgs(codec),
)

private fun h264Profile(codec: VideoCodec): List<String> =
    if (codec == VideoCodec.H264) listOf("-profile:v", "high") else emptyList()

private fun hevcFileArgs(codec: VideoCodec): List<String> =
    if (codec == VideoCodec.H265) listOf("-tag:v", "hvc1") else emptyList()
