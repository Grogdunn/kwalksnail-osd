package dev.grogdunn.kwalksnailosd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
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
import dev.grogdunn.kwalksnailosd.osd.OsdParser
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}


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
    val osdParser = OsdParser()

    LaunchedEffect(state.selectedOsd) {
        if (state.selectedOsd != null) {
            logger.info { "Selected OSD: ${state.selectedOsd.fileName}" }
            val parse = osdParser.parse(state.selectedOsd)
            stateHolder.saveOsd(parse)
        }
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
            }
            Column(
                modifier = Modifier.fillMaxSize().background(Color.DarkGray)
            ) {

            }
        }
    }

}