package dev.grogdunn.kwalksnailosd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.grogdunn.kwalksnailosd.osd.TelemetryOverlayOptions

@Composable
fun TelemetryOptionsPanel(stateHolder: AppStateHolder) {
    val state = stateHolder.state
    val track = state.telemetry ?: return
    val options = state.telemetryOptions

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Telemetria SRT",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.SemiBold
        )
        val skipped = if (track.skippedCues > 0) ", ${track.skippedCues} ignorati" else ""
        Text(
            text = "${track.cues.size} campioni$skipped",
            style = MaterialTheme.typography.caption,
            color = Color.Gray
        )

        FieldCheckbox("Segnale", options.showSignal, track.hasSignal) {
            stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(showSignal = it))
        }
        FieldCheckbox("Canale", options.showChannel, track.hasChannel) {
            stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(showChannel = it))
        }
        FieldCheckbox("Latenza", options.showLatency, track.hasLatency) {
            stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(showLatency = it))
        }
        FieldCheckbox("Bitrate", options.showBitrate, track.hasBitrate) {
            stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(showBitrate = it))
        }
        FieldCheckbox("Tensione VTx", options.showVtxVoltage, track.hasVtxVoltage) {
            stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(showVtxVoltage = it))
        }
        FieldCheckbox("Tensione occhiali", options.showGoggleVoltage, track.hasGoggleVoltage) {
            stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(showGoggleVoltage = it))
        }
        FieldCheckbox("Temperatura VTx", options.showVtxTemperature, track.hasVtxTemperature) {
            stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(showVtxTemperature = it))
        }
        FieldCheckbox("Temperatura occhiali", options.showGoggleTemperature, track.hasGoggleTemperature) {
            stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(showGoggleTemperature = it))
        }
        FieldCheckbox("Tempo di volo", options.showFlightTime, track.hasFlightTime) {
            stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(showFlightTime = it))
        }
        FieldCheckbox("Distanza", options.showDistance, track.hasDistance) {
            stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(showDistance = it))
        }

        PercentSlider(
            label = "Posizione orizzontale",
            value = options.xPercent,
            onChange = { stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(xPercent = it)) }
        )
        PercentSlider(
            label = "Posizione verticale",
            value = options.yPercent,
            onChange = { stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(yPercent = it)) }
        )
        PercentSlider(
            label = "Dimensione",
            value = options.scale,
            valueRange = 10f..60f,
            suffix = "",
            onChange = { stateHolder.updateTelemetryOptions(stateHolder.state.telemetryOptions.copy(scale = it)) }
        )

        OutlinedButton(
            onClick = {
                val current = stateHolder.state.telemetryOptions
                stateHolder.updateTelemetryOptions(
                    current.copy(
                        xPercent = TelemetryOverlayOptions.DEFAULT_X,
                        yPercent = TelemetryOverlayOptions.DEFAULT_Y,
                        scale = TelemetryOverlayOptions.DEFAULT_SCALE,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reimposta posizione", fontSize = 12.sp)
        }
    }
}

@Composable
private fun FieldCheckbox(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = if (enabled) MaterialTheme.colors.onSurface else Color.Gray
        )
    }
}

@Composable
private fun PercentSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    suffix: String = "%",
) {
    Text(
        text = "$label ${"%.1f".format(value)}$suffix",
        style = MaterialTheme.typography.caption
    )
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onChange,
        valueRange = valueRange,
        modifier = Modifier.fillMaxWidth()
    )
}
