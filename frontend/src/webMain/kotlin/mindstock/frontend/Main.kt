@file:OptIn(ExperimentalComposeUiApi::class)

package mindstock.frontend

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

fun main() {
    ComposeViewport {
        App()
    }
}
