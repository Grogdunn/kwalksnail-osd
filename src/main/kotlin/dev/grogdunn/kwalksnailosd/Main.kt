package dev.grogdunn.kwalksnailosd

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

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
    val selectedMp4 = remember { mutableStateOf(null as Path?) }
    val selectedOsd = remember { mutableStateOf(null as Path?) }
    val selectedSrt = remember { mutableStateOf(null as Path?) }

    val onFilePickerClose: (Path?) -> Unit = {
        logger.info { "onCloseRequest called with: $it" }
        if (it != null) {
            val lookupFiles = lookupFiles(it)
            if (selectedMp4.value == null) {
                selectedMp4.value = lookupFiles.mp4Path
            }
            if (selectedOsd.value == null) {
                selectedOsd.value = lookupFiles.osdPath
            }
            if (selectedSrt.value == null) {
                selectedSrt.value = lookupFiles.srtPath
            }
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
                Text(selectedMp4.value?.fileName?.toString() ?: "No file selected")
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("mp4")) { onFilePickerClose(it) }
                }) {
                    Text("Select MP4 File")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Select OSD File")
                Text(selectedOsd.value?.fileName?.toString() ?: "No file selected")
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("osd")) { onFilePickerClose(it) }
                }) {
                    Text("Select OSD File")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Select SRT File")
                Text(selectedSrt.value?.fileName?.toString() ?: "No file selected")
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("srt")) { onFilePickerClose(it) }
                }) {
                    Text("Select SRT File")
                }
            }
            Column(
                modifier = Modifier.fillMaxSize()
            ) { }
        }
    }

}