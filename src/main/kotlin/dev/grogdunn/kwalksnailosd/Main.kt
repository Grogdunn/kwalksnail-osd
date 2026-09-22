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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.nio.file.Path

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
    var selectedMp4 by remember { mutableStateOf(null as Path?) }
    var selectedOsd by remember { mutableStateOf(null as Path?) }
    var selectedSrt by remember { mutableStateOf(null as Path?) }
    val onCloseRequest: (Path?) -> Unit = {
        println("onCloseRequest called with: $it")
        if (it != null) {
            val lookupFiles = lookupFiles(it)
            selectedMp4 = lookupFiles.mp4Path
            selectedSrt = lookupFiles.srtPath
            selectedOsd = lookupFiles.osdPath
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
                Text(selectedMp4?.fileName?.toString() ?: "No file selected")
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("mp4")) { onCloseRequest(it) }
                }) {
                    Text("Select MP4 File")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Select OSD File")
                Text(selectedOsd?.fileName?.toString() ?: "No file selected")
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("osd")) { onCloseRequest(it) }
                }) {
                    Text("Select OSD File")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Select SRT File")
                Text(selectedSrt?.fileName?.toString() ?: "No file selected")
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("srt")) { onCloseRequest(it) }
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