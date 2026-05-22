@file:OptIn(ExperimentalComposeUiApi::class)

package mindstock.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

fun main() {
    ComposeViewport {
        App()
    }
}

@Composable
fun App() {
    MaterialTheme {
        Text("mindstock")
    }
}
