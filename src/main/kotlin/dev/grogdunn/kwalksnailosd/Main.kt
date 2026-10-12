package dev.grogdunn.kwalksnailosd

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
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
        title = "Walksnail OSD",
        state = windowState
    ) {
        MenuBar {
            Menu("File", mnemonic = 'F') {
                Item("Apri Video MP4...", onClick = {
                    desktopFileDialog(allowedExtensions = listOf("mp4"), onCloseRequest = stateHolder::onFilePickerClosed)
                })
                Item("Apri File OSD...", onClick = {
                    desktopFileDialog(allowedExtensions = listOf("osd"), onCloseRequest = stateHolder::onFilePickerClosed)
                })
                Item("Apri Sottotitoli SRT...", onClick = {
                    desktopFileDialog(allowedExtensions = listOf("srt"), onCloseRequest = stateHolder::onFilePickerClosed)
                })
                Item("Apri Font OSD...", onClick = {
                    desktopFileDialog(allowedExtensions = listOf("png"), onCloseRequest = stateHolder::onFontFilePickerClosed)
                })
                Separator()
                Item("Esci", onClick = {
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

    Column(
        modifier = modifier
            .background(MaterialTheme.colors.background)
            .padding(12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "File Sorgente",
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold
        )

        FileSelectorCard(
            title = "Video MP4",
            fileName = state.selectedMp4?.fileName?.toString(),
            onSelectClick = {
                desktopFileDialog(allowedExtensions = listOf("mp4"), onCloseRequest = stateHolder::onFilePickerClosed)
            }
        )

        FileSelectorCard(
            title = "Dati OSD (.osd)",
            fileName = state.selectedOsd?.fileName?.toString(),
            isLoading = state.isParsingOsd,
            errorMessage = state.osdError,
            onSelectClick = {
                desktopFileDialog(allowedExtensions = listOf("osd"), onCloseRequest = stateHolder::onFilePickerClosed)
            }
        )

        FileSelectorCard(
            title = "Sottotitoli (.srt)",
            fileName = state.selectedSrt?.fileName?.toString(),
            onSelectClick = {
                desktopFileDialog(allowedExtensions = listOf("srt"), onCloseRequest = stateHolder::onFilePickerClosed)
            }
        )

        FileSelectorCard(
            title = "Font OSD (.png)",
            fileName = state.selectedFont?.fileName?.toString(),
            onSelectClick = {
                desktopFileDialog(allowedExtensions = listOf("png"), onCloseRequest = stateHolder::onFontFilePickerClosed)
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
                text = "Dettagli Video & OSD",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )

            if (state.videoInfo != null) {
                Card(
                    elevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colors.surface
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Video:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.caption)
                        Text("Risoluzione: ${state.videoInfo.width}x${state.videoInfo.height}", style = MaterialTheme.typography.body2)
                        Text("Frame Rate: ${"%.2f".format(state.videoInfo.frameRate)} fps", style = MaterialTheme.typography.body2)
                        Text("Totale Frame: ${state.videoInfo.frameNumber}", style = MaterialTheme.typography.body2)
                        Text("Durata: ${formatDuration(state.videoInfo.duration)}", style = MaterialTheme.typography.body2)
                        Text("Codec: ${state.videoInfo.codec}", style = MaterialTheme.typography.body2)
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
                        Text("OSD:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.caption)
                        Text("Firmware: ${state.osd.fcFirmware}", style = MaterialTheme.typography.body2)
                        Text("Frame registrati: ${state.osd.frames.size}", style = MaterialTheme.typography.body2)
                        Text("Durata OSD: ${formatDuration(state.osd.duration)}", style = MaterialTheme.typography.body2)
                    }
                }
            }

            if (state.videoInfo == null && state.osd == null) {
                Text(
                    text = "Carica i file sorgente per visualizzare i dettagli tecnici.",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    TODO()
                },
                enabled = state.osd != null && state.selectedMp4 != null && state.selectedFont != null && state.selectedSrt != null
            ) {
                Text("Burn", fontSize = 12.sp)
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
                text = fileName ?: "Nessun file selezionato",
                style = MaterialTheme.typography.body2,
                color = if (fileName != null) MaterialTheme.colors.onSurface else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (errorMessage != null) {
                Text(
                    text = "Errore: $errorMessage",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.error
                )
            }

            OutlinedButton(
                onClick = onSelectClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Text("Seleziona...", fontSize = 12.sp)
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
                    contentDescription = "Anteprima Video con OSD",
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
                        text = "Nessun frame disponibile",
                        style = MaterialTheme.typography.h6,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (!isControlsEnabled) {
                            "Assicurati di aver caricato Video MP4, File OSD e Font per abilitare l'anteprima e la navigazione."
                        } else {
                            "Muovi lo slider sottostante per renderizzare un frame."
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
                        text = "Frame: $currentFrameInt / $totalFrames",
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Tempo: ${formatTimestamp(currentSeconds)}",
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
                    state.isParsingOsd -> {
                        CircularProgressIndicator(modifier = Modifier.height(12.dp).width(12.dp), strokeWidth = 2.dp)
                        Text("Analisi OSD in corso...", style = MaterialTheme.typography.caption)
                    }

                    state.osdError != null -> {
                        Text("Errore OSD: ${state.osdError}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error)
                    }

                    state.selectedMp4 != null -> {
                        Text("Video caricato: ${state.selectedMp4.fileName}", style = MaterialTheme.typography.caption)
                    }

                    else -> {
                        Text("Pronto - Nessun video caricato", style = MaterialTheme.typography.caption, color = Color.Gray)
                    }
                }
            }

            // Indicatori sintetici a destra
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val videoStatus = state.videoInfo?.let { "${it.width}x${it.height} @ ${"%.0f".format(it.frameRate)}fps" } ?: "Nessun video"
                val osdStatus = state.osd?.let { "${it.fcFirmware}" } ?: "Nessun OSD"
                val fontStatus = if (state.font != null) "Font OK" else "Nessun font"

                Text("Video: $videoStatus", style = MaterialTheme.typography.caption, color = Color.Gray)
                Text("OSD: $osdStatus", style = MaterialTheme.typography.caption, color = Color.Gray)
                Text("Font: $fontStatus", style = MaterialTheme.typography.caption, color = Color.Gray)
                Text("Frame: ${state.selectedFrame.roundToInt()}", style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
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