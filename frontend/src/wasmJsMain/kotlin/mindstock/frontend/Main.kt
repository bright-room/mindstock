@file:OptIn(ExperimentalComposeUiApi::class)

package mindstock.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.w3c.dom.events.Event

fun main() {
    document.addEventListener("DOMContentLoaded", { _: Event ->
        ComposeViewport(document.body!!) {
            App()
        }
    })
}

@Composable
fun App() {
    MaterialTheme {
        Text("mindstock")
    }
}
