package dev.grogdunn.kwalksnailosd

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application


fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Walksnail OSD",
    ) {
        App()
    }
}

@Composable
@Preview
fun App() {
    val stateHolder = remember { AppStateHolder() }
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
        Row(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select MP4 File")
                Text(state.selectedMp4?.fileName?.toString() ?: "No file selected")
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("mp4"), onCloseRequest = stateHolder::onFilePickerClosed)
                }) {
                    Text("Select MP4 File")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Select OSD File")
                Text(state.selectedOsd?.fileName?.toString() ?: "No file selected")
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("osd"), onCloseRequest = stateHolder::onFilePickerClosed)
                }) {
                    Text("Select OSD File")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Select SRT File")
                Text(state.selectedSrt?.fileName?.toString() ?: "No file selected")
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("srt"), onCloseRequest = stateHolder::onFilePickerClosed)
                }) {
                    Text("Select SRT File")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Select font File")
                Text(state.selectedFont?.fileName?.toString() ?: "No file selected")
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("png"), onCloseRequest = stateHolder::onFontFilePickerClosed)
                }) {
                    Text("Select font File")
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
//                Row {
//                    when {
//                        state.isParsingOsd -> Text("Parsing OSD file...")
//                        state.osdError != null -> Text("OSD parsing failed: ${state.osdError}")
//                        state.osd != null -> Text("OSD: ${state.osd.fcFirmware} Frames#: ${state.osd.frames.size} Duration: ${state.osd.duration}")
//                        else -> Text("No OSD file selected")
//                    }
//                    Text("Video info: ${state.videoInfo}")
//                }
                Row (modifier = Modifier.background(Color.LightGray).fillMaxHeight(.9f).fillMaxWidth().padding(5.dp)){
                    when {
                        state.bitmap == null -> Text("No frame available")
                        else -> Image(bitmap = state.bitmap, modifier = Modifier.fillMaxSize(), contentDescription = "Frame")
                    }
                }
                Column(modifier = Modifier.height(50.dp).fillMaxWidth().padding(top = 1.dp, bottom = 1.dp)) {
                    when {
                        state.videoInfo == null -> Slider(value = state.selectedFrame, onValueChange = { stateHolder.onFrameSelected(it) }, valueRange = 0.0f..1.0f)
                        else -> Slider(value = state.selectedFrame, onValueChange = { stateHolder.onFrameSelected(it) }, valueRange = 0.0f..state.videoInfo.frameNumber.toFloat())
                    }
                }

            }
        }
    }

}