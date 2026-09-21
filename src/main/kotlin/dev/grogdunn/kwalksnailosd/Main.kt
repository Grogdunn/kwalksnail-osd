package dev.grogdunn.kwalksnailosd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
    var selectedFile by remember { mutableStateOf(null as Path?) }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(selectedFile?.toString() ?: "No file selected", modifier = Modifier.background(Color.Red))
                Button(onClick = {
                    desktopFileDialog(allowedExtensions = listOf("osd")) { selectedFile = it }
                }) {
                    Text("Select OSD File")
                }
            }
            Column(
                modifier = Modifier.fillMaxSize()
            ) { }
        }

    }
}