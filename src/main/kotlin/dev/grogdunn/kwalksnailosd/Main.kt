package dev.grogdunn.kwalksnailosd

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.grogdunn.kwalksnailosd.generated.resources.*
import dev.grogdunn.kwalksnailosd.osd.osdPlaybackSpeedFactor
import dev.grogdunn.kwalksnailosd.video.BurnEncoderPreference
import dev.grogdunn.kwalksnailosd.video.VideoCodec
import dev.grogdunn.kwalksnailosd.video.outputNameFor
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.Duration
import kotlin.math.roundToInt

fun main() = application {
    val stateHolder = remember { AppStateHolder() }
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

    DisposableEffect(stateHolder) {
        onDispose {
            stateHolder.close()
        }
    }

    Window(
        onCloseRequest = {
            stateHolder.close()
            exitApplication()
        },
        title = stringResource(Res.string.window_title),
        state = windowState
    ) {
        val openVideoTitle = stringResource(Res.string.dialog_open_video)
        val openOsdTitle = stringResource(Res.string.dialog_open_osd)
        val openSrtTitle = stringResource(Res.string.dialog_open_srt)
        val openFontTitle = stringResource(Res.string.dialog_open_font)
        MenuBar {
            Menu(
                stringResource(Res.string.menu_file),
                mnemonic = stringResource(Res.string.menu_file_mnemonic).single(),
            ) {
                Item(stringResource(Res.string.menu_open_video), onClick = {
                    desktopFileDialog(openVideoTitle, allowedExtensions = listOf("mp4"), onCloseRequest = stateHolder::onFilePickerClosed)
                })
                Item(stringResource(Res.string.menu_open_osd), onClick = {
                    desktopFileDialog(openOsdTitle, allowedExtensions = listOf("osd"), onCloseRequest = stateHolder::onFilePickerClosed)
                })
                Item(stringResource(Res.string.menu_open_srt), onClick = {
                    desktopFileDialog(openSrtTitle, allowedExtensions = listOf("srt"), onCloseRequest = stateHolder::onFilePickerClosed)
                })
                Item(stringResource(Res.string.menu_open_font), onClick = {
                    desktopFileDialog(openFontTitle, allowedExtensions = listOf("png"), onCloseRequest = stateHolder::onFontFilePickerClosed)
                })
                Separator()
                Item(stringResource(Res.string.menu_exit), onClick = {
                    stateHolder.close()
                    exitApplication()
                })
            }
        }

        App(stateHolder = stateHolder)
    }
}

@Composable
fun App(stateHolder: AppStateHolder = remember { AppStateHolder() }) {
    val state = stateHolder.state

    LaunchedEffect(state.selectedOsd) {
        state.selectedOsd?.let { stateHolder.parseSelectedOsd() }
    }

    LaunchedEffect(state.selectedMp4) {
        state.selectedMp4?.let { stateHolder.parseSelectedMp4() }
    }

    LaunchedEffect(state.selectedFont) {
        state.selectedFont?.let { stateHolder.parseSelectedFont() }
    }

    LaunchedEffect(state.selectedSrt) {
        state.selectedSrt?.let { stateHolder.parseSelectedSrt() }
    }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Area Principale (Sidebar + Preview e Slider)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Barra laterale per la gestione dei file e informazioni
                LeftSidebarPanel(
                    stateHolder = stateHolder,
                    modifier = Modifier.width(320.dp).fillMaxHeight()
                )

                Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

                // Area Preview Video con lo Slider posizionato sempre sotto
                VideoPreviewAndControls(
                    stateHolder = stateHolder,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Divider(modifier = Modifier.fillMaxHeight().width(1.dp))
                RightSidebarPanel(
                    stateHolder = stateHolder,
                    modifier = Modifier.width(320.dp).fillMaxHeight()
                )
            }

            Divider(modifier = Modifier.fillMaxWidth().height(1.dp))

            // Barra di stato in basso
            StatusBar(
                stateHolder = stateHolder,
                modifier = Modifier.fillMaxWidth().height(30.dp)
            )
        }
    }
}

@Composable
private fun LeftSidebarPanel(
    stateHolder: AppStateHolder,
    modifier: Modifier = Modifier
) {
    val state = stateHolder.state
    val scrollState = rememberScrollState()
    val openVideoTitle = stringResource(Res.string.dialog_open_video)
    val openOsdTitle = stringResource(Res.string.dialog_open_osd)
    val openSrtTitle = stringResource(Res.string.dialog_open_srt)
    val openFontTitle = stringResource(Res.string.dialog_open_font)

    Column(
        modifier = modifier
            .background(MaterialTheme.colors.background)
            .padding(12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.sidebar_source_files),
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold
        )

        FileSelectorCard(
            title = stringResource(Res.string.file_video_mp4),
            fileName = state.selectedMp4?.fileName?.toString(),
            errorMessage = state.videoError,
            onSelectClick = {
                desktopFileDialog(openVideoTitle, allowedExtensions = listOf("mp4"), onCloseRequest = stateHolder::onFilePickerClosed)
            }
        )

        FileSelectorCard(
            title = stringResource(Res.string.file_osd_data),
            fileName = state.selectedOsd?.fileName?.toString(),
            isLoading = state.isParsingOsd,
            errorMessage = state.osdError,
            onSelectClick = {
                desktopFileDialog(openOsdTitle, allowedExtensions = listOf("osd"), onCloseRequest = stateHolder::onFilePickerClosed)
            }
        )

        FileSelectorCard(
            title = stringResource(Res.string.file_srt),
            fileName = state.selectedSrt?.fileName?.toString(),
            isLoading = state.isParsingSrt,
            errorMessage = state.srtError,
            onSelectClick = {
                desktopFileDialog(openSrtTitle, allowedExtensions = listOf("srt"), onCloseRequest = stateHolder::onFilePickerClosed)
            }
        )

        FileSelectorCard(
            title = stringResource(Res.string.file_font),
            fileName = state.selectedFont?.fileName?.toString(),
            errorMessage = state.fontError,
            onSelectClick = {
                desktopFileDialog(openFontTitle, allowedExtensions = listOf("png"), onCloseRequest = stateHolder::onFontFilePickerClosed)
            }
        )
    }
}


@Composable
private fun RightSidebarPanel(
    stateHolder: AppStateHolder,
    modifier: Modifier = Modifier
) {
    val state = stateHolder.state
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(MaterialTheme.colors.background)
            .padding(12.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.details_title),
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )

            if (state.videoInfo != null) {
                val codec = state.videoInfo.codec
                val codecLabel = if (codec == "unknown") stringResource(Res.string.codec_unknown) else codec
                Card(
                    elevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colors.surface
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.details_video), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.caption)
                        Text(stringResource(Res.string.details_resolution, state.videoInfo.width, state.videoInfo.height), style = MaterialTheme.typography.body2)
                        Text(stringResource(Res.string.details_frame_rate, "%.2f".format(state.videoInfo.frameRate)), style = MaterialTheme.typography.body2)
                        Text(stringResource(Res.string.details_total_frames, state.videoInfo.frameNumber), style = MaterialTheme.typography.body2)
                        Text(stringResource(Res.string.details_duration, formatDuration(state.videoInfo.duration)), style = MaterialTheme.typography.body2)
                        Text(stringResource(Res.string.details_codec, codecLabel), style = MaterialTheme.typography.body2)
                    }
                }
            }

            if (state.osd != null) {
                Card(
                    elevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colors.surface
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.details_osd), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.caption)
                        Text(stringResource(Res.string.details_firmware, state.osd.fcFirmware), style = MaterialTheme.typography.body2)
                        Text(stringResource(Res.string.details_recorded_frames, state.osd.frames.size), style = MaterialTheme.typography.body2)
                        Text(stringResource(Res.string.details_osd_duration, formatDuration(state.osd.duration)), style = MaterialTheme.typography.body2)
                    }
                }
            }

            if (state.videoInfo == null && state.osd == null) {
                Text(
                    text = stringResource(Res.string.details_empty),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            SrtOverlayOptions(stateHolder = stateHolder)
            BurnOptions(stateHolder = stateHolder)
        }

        Spacer(modifier = Modifier.height(12.dp))

        BurnStatusAndButton(stateHolder = stateHolder)
    }
}

@Composable
private fun SrtOverlayOptions(stateHolder: AppStateHolder) {
    val state = stateHolder.state
    val options = state.srtOptions
    val enabled = !state.isBurning && state.srt != null
    val distanceEnabled = enabled && state.srt.hasDistance
    val debugEnabled = enabled && state.srt.hasDebugFields

    Text(stringResource(Res.string.srt_title), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
    Text(
        text = if (state.srt == null) {
            stringResource(Res.string.srt_hint)
        } else if (state.srt.hasDistance) {
            pluralStringResource(Res.plurals.srt_cues_with_distance, state.srt.cues.size, state.srt.cues.size)
        } else {
            pluralStringResource(Res.plurals.srt_cues_without_distance, state.srt.cues.size, state.srt.cues.size)
        },
        style = MaterialTheme.typography.caption,
        color = Color.Gray
    )
    Text(stringResource(Res.string.srt_position_x, "%.1f".format(options.xPercent)), style = MaterialTheme.typography.caption)
    Slider(
        value = options.xPercent,
        onValueChange = { stateHolder.setSrtOptions(options.copy(xPercent = it)) },
        valueRange = 0f..100f,
        enabled = enabled,
    )
    Text(stringResource(Res.string.srt_position_y, "%.1f".format(options.yPercent)), style = MaterialTheme.typography.caption)
    Slider(
        value = options.yPercent,
        onValueChange = { stateHolder.setSrtOptions(options.copy(yPercent = it)) },
        valueRange = 0f..100f,
        enabled = enabled,
    )
    Text(stringResource(Res.string.srt_scale, "%.0f".format(options.scale)), style = MaterialTheme.typography.caption)
    Slider(
        value = options.scale,
        onValueChange = { stateHolder.setSrtOptions(options.copy(scale = it)) },
        valueRange = 10f..60f,
        enabled = enabled,
    )
    OutlinedButton(onClick = stateHolder::resetSrtPlacement, enabled = enabled) {
        Text(stringResource(Res.string.srt_reset_position), fontSize = 12.sp)
    }
    SrtFieldCheckbox(stringResource(Res.string.srt_field_time), options.showTime, enabled) { stateHolder.setSrtOptions(options.copy(showTime = it)) }
    SrtFieldCheckbox(stringResource(Res.string.srt_field_sky_battery), options.showSkyBattery, enabled) { stateHolder.setSrtOptions(options.copy(showSkyBattery = it)) }
    SrtFieldCheckbox(stringResource(Res.string.srt_field_ground_battery), options.showGroundBattery, enabled) { stateHolder.setSrtOptions(options.copy(showGroundBattery = it)) }
    SrtFieldCheckbox(stringResource(Res.string.srt_field_signal), options.showSignal, enabled) { stateHolder.setSrtOptions(options.copy(showSignal = it)) }
    SrtFieldCheckbox(stringResource(Res.string.srt_field_latency), options.showLatency, enabled) { stateHolder.setSrtOptions(options.copy(showLatency = it)) }
    SrtFieldCheckbox(stringResource(Res.string.srt_field_bitrate), options.showBitrate, enabled) { stateHolder.setSrtOptions(options.copy(showBitrate = it)) }
    SrtFieldCheckbox(stringResource(Res.string.srt_field_distance), options.showDistance, distanceEnabled) {
        stateHolder.setSrtOptions(options.copy(showDistance = it))
    }
    SrtFieldCheckbox(stringResource(Res.string.srt_field_mcs), options.showMcs, debugEnabled) {
        stateHolder.setSrtOptions(options.copy(showMcs = it))
    }
    SrtFieldCheckbox(stringResource(Res.string.srt_field_snr), options.showSnr, debugEnabled) {
        stateHolder.setSrtOptions(options.copy(showSnr = it))
    }
    SrtFieldCheckbox(stringResource(Res.string.srt_field_temp), options.showTemp, debugEnabled) {
        stateHolder.setSrtOptions(options.copy(showTemp = it))
    }
}

@Composable
private fun SrtFieldCheckbox(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.body2)
    }
}

@Composable
private fun BurnOptions(stateHolder: AppStateHolder) {
    val state = stateHolder.state
    val controlsEnabled = !state.isBurning && state.videoInfo != null && state.osd != null && state.font != null
    val speedFactor = if (state.videoInfo != null && state.osd != null) {
        osdPlaybackSpeedFactor(state.videoInfo.duration, state.osd.duration, state.adjustOsdPlaybackSpeed)
    } else {
        1.0
    }

    Text(
        text = stringResource(Res.string.export_title),
        style = MaterialTheme.typography.subtitle1,
        fontWeight = FontWeight.Bold
    )

    Text(stringResource(Res.string.export_offset_x, state.osdOffsetX), style = MaterialTheme.typography.caption)
    Slider(
        value = state.osdOffsetX.toFloat(),
        onValueChange = { stateHolder.setOsdOffset(it.roundToInt(), state.osdOffsetY) },
        valueRange = -200f..700f,
        enabled = controlsEnabled,
    )
    Text(stringResource(Res.string.export_offset_y, state.osdOffsetY), style = MaterialTheme.typography.caption)
    Slider(
        value = state.osdOffsetY.toFloat(),
        onValueChange = { stateHolder.setOsdOffset(state.osdOffsetX, it.roundToInt()) },
        valueRange = -200f..700f,
        enabled = controlsEnabled,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = stateHolder::centerOsd, enabled = controlsEnabled) {
            Text(stringResource(Res.string.export_center), fontSize = 12.sp)
        }
        OutlinedButton(onClick = stateHolder::resetOsdOffset, enabled = controlsEnabled) {
            Text(stringResource(Res.string.export_reset), fontSize = 12.sp)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.adjustOsdPlaybackSpeed,
            onCheckedChange = stateHolder::setAdjustOsdPlaybackSpeed,
            enabled = controlsEnabled,
        )
        Text(stringResource(Res.string.export_correct_speed), style = MaterialTheme.typography.body2)
    }
    Text(
        text = stringResource(Res.string.export_speed_hint, "%.3f".format(speedFactor)),
        style = MaterialTheme.typography.caption,
        color = Color.Gray
    )

    Text(stringResource(Res.string.export_codec), style = MaterialTheme.typography.caption)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VideoCodec.entries.forEach { codec ->
            OutlinedButton(
                onClick = { stateHolder.setBurnCodec(codec) },
                enabled = controlsEnabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = if (state.burnCodec == codec) MaterialTheme.colors.primary.copy(alpha = 0.2f) else Color.Transparent
                )
            ) {
                Text(codec.label, fontSize = 12.sp)
            }
        }
    }

    Text(stringResource(Res.string.export_bitrate, state.burnBitrateMbps), style = MaterialTheme.typography.caption)
    Slider(
        value = state.burnBitrateMbps.toFloat(),
        onValueChange = { stateHolder.setBurnBitrateMbps(it.roundToInt()) },
        valueRange = 1f..80f,
        enabled = controlsEnabled,
    )

    LaunchedEffect(state.burnCodec, state.videoInfo != null) {
        if (state.videoInfo != null) stateHolder.refreshBurnEncoders()
    }
    EncoderPicker(stateHolder, controlsEnabled)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EncoderPicker(stateHolder: AppStateHolder, enabled: Boolean) {
    val state = stateHolder.state
    Text(stringResource(Res.string.export_encoder), style = MaterialTheme.typography.caption)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EncoderChoiceButton(
            label = stringResource(Res.string.export_encoder_auto),
            selected = state.burnEncoder is BurnEncoderPreference.Auto,
            enabled = enabled,
            onClick = { stateHolder.setBurnEncoder(BurnEncoderPreference.Auto) },
        )
        EncoderChoiceButton(
            label = stringResource(Res.string.export_encoder_software),
            selected = state.burnEncoder is BurnEncoderPreference.Software,
            enabled = enabled,
            onClick = { stateHolder.setBurnEncoder(BurnEncoderPreference.Software) },
        )
        state.burnHardwareOptions.forEach { kind ->
            EncoderChoiceButton(
                label = stringResource(kind.labelResource()),
                selected = (state.burnEncoder as? BurnEncoderPreference.Hardware)?.kind == kind,
                enabled = enabled,
                onClick = { stateHolder.setBurnEncoder(BurnEncoderPreference.Hardware(kind)) },
            )
        }
    }
    Text(
        text = stringResource(Res.string.export_encoder_hint),
        style = MaterialTheme.typography.caption,
        color = Color.Gray,
    )
    if (state.burnHardwareProbePending) {
        Text(stringResource(Res.string.export_encoder_probing), style = MaterialTheme.typography.caption, color = Color.Gray)
    } else {
        state.burnHardwareNote?.let { note ->
            Text(note, style = MaterialTheme.typography.caption, color = Color.Gray)
        }
    }
}

@Composable
private fun EncoderChoiceButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = if (selected) MaterialTheme.colors.primary.copy(alpha = 0.2f) else Color.Transparent,
        ),
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun BurnStatusAndButton(stateHolder: AppStateHolder) {
    val state = stateHolder.state
    val canBurn = state.osd != null && state.videoInfo != null && state.font != null && state.selectedMp4 != null
    val saveTitle = stringResource(Res.string.dialog_save_video)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isBurning || state.burnMessage != null) {
            LinearProgressIndicator(
                progress = state.burnProgress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth()
            )
        }
        state.burnMessage?.let { message ->
            Text(text = message, style = MaterialTheme.typography.caption, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        state.burnError?.let { error ->
            Text(text = error, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (state.isBurning) {
                OutlinedButton(onClick = stateHolder::cancelBurn) {
                    Text(stringResource(Res.string.export_cancel), fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val input = state.selectedMp4 ?: return@OutlinedButton
                        desktopSaveDialog(outputNameFor(input), saveTitle) { path ->
                            if (path != null) stateHolder.startBurn(path)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Red,
                        contentColor = Color.Black
                    ),
                    enabled = canBurn
                ) {
                    Text(stringResource(Res.string.export_burn), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun FileSelectorCard(
    title: String,
    fileName: String?,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onSelectClick: () -> Unit
) {
    Card(
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.SemiBold
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp)
                }
            }

            Text(
                text = fileName ?: stringResource(Res.string.file_none_selected),
                style = MaterialTheme.typography.body2,
                color = if (fileName != null) MaterialTheme.colors.onSurface else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (errorMessage != null) {
                Text(
                    text = stringResource(Res.string.file_error, errorMessage),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.error
                )
            }

            OutlinedButton(
                onClick = onSelectClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Text(stringResource(Res.string.file_select), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun VideoPreviewAndControls(
    stateHolder: AppStateHolder,
    modifier: Modifier = Modifier
) {
    val state = stateHolder.state
    val maxFrames = (state.videoInfo?.frameNumber?.toFloat() ?: 1.0f).coerceAtLeast(1.0f)
    val isControlsEnabled = state.videoInfo != null && state.osd != null && state.font != null

    Column(
        modifier = modifier
            .background(Color(0xFF141414))
            .padding(12.dp)
    ) {
        // Area di visualizzazione della preview del video (espandibile)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (state.bitmap != null) {
                Image(
                    bitmap = state.bitmap,
                    contentDescription = stringResource(Res.string.preview_content_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.preview_no_frame),
                        style = MaterialTheme.typography.h6,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (!isControlsEnabled) {
                            stringResource(Res.string.preview_need_files)
                        } else {
                            stringResource(Res.string.preview_move_slider)
                        },
                        style = MaterialTheme.typography.body2,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Zona controlli: Slider sempre posizionato sotto la preview
        Surface(
            modifier = Modifier.fillMaxWidth(),
            elevation = 3.dp,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Info riga frame e tempo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentFrameInt = state.selectedFrame.roundToInt()
                    val totalFrames = state.videoInfo?.frameNumber ?: 0
                    val fps = state.videoInfo?.frameRate ?: 30.0
                    val currentSeconds = currentFrameInt / fps

                    Text(
                        text = stringResource(Res.string.preview_frame, currentFrameInt, totalFrames),
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = stringResource(Res.string.preview_time, formatTimestamp(currentSeconds)),
                        style = MaterialTheme.typography.body2,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colors.onSurface
                    )
                }

                // Slider navigazione video
                Slider(
                    value = state.selectedFrame.coerceIn(0f, maxFrames),
                    onValueChange = { stateHolder.onFrameSelected(it) },
                    valueRange = 0.0f..maxFrames,
                    enabled = isControlsEnabled,
                    modifier = Modifier.fillMaxWidth()
                )

                // Pulsanti per regolazione fine del frame
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val next = (state.selectedFrame - 10f).coerceAtLeast(0f)
                            stateHolder.onFrameSelected(next)
                        },
                        enabled = isControlsEnabled && state.selectedFrame > 0,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text("-10", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val next = (state.selectedFrame - 1f).coerceAtLeast(0f)
                            stateHolder.onFrameSelected(next)
                        },
                        enabled = isControlsEnabled && state.selectedFrame > 0,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text("-1", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedButton(
                        onClick = {
                            val next = (state.selectedFrame + 1f).coerceAtMost(maxFrames)
                            stateHolder.onFrameSelected(next)
                        },
                        enabled = isControlsEnabled && state.selectedFrame < maxFrames,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text("+1", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val next = (state.selectedFrame + 10f).coerceAtMost(maxFrames)
                            stateHolder.onFrameSelected(next)
                        },
                        enabled = isControlsEnabled && state.selectedFrame < maxFrames,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text("+10", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(
    stateHolder: AppStateHolder,
    modifier: Modifier = Modifier
) {
    val state = stateHolder.state

    Surface(
        modifier = modifier,
        color = MaterialTheme.colors.surface,
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Stato operativo a sinistra
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    state.isBurning -> {
                        CircularProgressIndicator(modifier = Modifier.height(12.dp).width(12.dp), strokeWidth = 2.dp)
                        Text(state.burnMessage ?: stringResource(Res.string.status_exporting), style = MaterialTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    state.burnError != null -> {
                        Text(stringResource(Res.string.status_export_error, state.burnError), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    state.isParsingOsd -> {
                        CircularProgressIndicator(modifier = Modifier.height(12.dp).width(12.dp), strokeWidth = 2.dp)
                        Text(stringResource(Res.string.status_parsing_osd), style = MaterialTheme.typography.caption)
                    }

                    state.osdError != null -> {
                        Text(stringResource(Res.string.status_osd_error, state.osdError), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error)
                    }

                    state.fontError != null -> {
                        Text(
                            stringResource(Res.string.status_font_error, state.fontError),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    state.videoError != null -> {
                        Text(
                            stringResource(Res.string.status_video_error, state.videoError),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    state.selectedMp4 != null -> {
                        Text(stringResource(Res.string.status_video_loaded, state.selectedMp4.fileName), style = MaterialTheme.typography.caption)
                    }

                    else -> {
                        Text(stringResource(Res.string.status_ready), style = MaterialTheme.typography.caption, color = Color.Gray)
                    }
                }
            }

            // Indicatori sintetici a destra
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val videoStatus = state.videoInfo?.let {
                    stringResource(Res.string.status_video_summary, it.width, it.height, "%.0f".format(it.frameRate))
                } ?: stringResource(Res.string.status_no_video)
                val osdStatus = state.osd?.fcFirmware?.toString() ?: stringResource(Res.string.status_no_osd)
                val fontStatus = when {
                    state.fontError != null -> stringResource(Res.string.status_font_error_short)
                    state.font != null -> stringResource(Res.string.status_font_ok)
                    else -> stringResource(Res.string.status_no_font)
                }

                Text(stringResource(Res.string.status_video, videoStatus), style = MaterialTheme.typography.caption, color = Color.Gray)
                Text(stringResource(Res.string.status_osd, osdStatus), style = MaterialTheme.typography.caption, color = Color.Gray)
                Text(
                    stringResource(Res.string.status_font, fontStatus),
                    style = MaterialTheme.typography.caption,
                    color = if (state.fontError != null) MaterialTheme.colors.error else Color.Gray
                )
                Text(stringResource(Res.string.status_frame, state.selectedFrame.roundToInt()), style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatTimestamp(secondsDouble: Double): String {
    val totalMillis = (secondsDouble * 1000).toLong().coerceAtLeast(0L)
    val minutes = (totalMillis / 60000)
    val seconds = (totalMillis % 60000) / 1000
    val millis = totalMillis % 1000
    return "%02d:%02d.%03d".format(minutes, seconds, millis)
}