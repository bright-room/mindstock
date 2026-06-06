package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 単発トースト表示。message=null の間は何も描かない。 */
@Composable
fun Toast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    Snackbar(modifier = modifier) { Text(message) }
}
