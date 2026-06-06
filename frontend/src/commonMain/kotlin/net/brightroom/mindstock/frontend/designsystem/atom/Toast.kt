package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/** 単発トースト。message=null の間は何も描かない。ink 背景 + 角丸。 */
@Composable
fun Toast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    Snackbar(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.colorScheme.surface,
    ) {
        AppText(text = message, style = MindstockType.summarySub(), color = MaterialTheme.colorScheme.surface)
    }
}
