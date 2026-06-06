package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/** モーダルボトムシート。open=false の間は何も描かない。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(
    open: Boolean,
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
            AppText(text = title, style = MindstockType.summaryTitle(), modifier = Modifier.padding(bottom = 18.dp))
            content()
        }
    }
}
