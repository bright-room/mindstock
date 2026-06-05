package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class SegOption(
    val key: String,
    val label: String,
)

@Composable
fun SegmentedControl(
    options: List<SegOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o.key == selectedKey,
                onClick = { onSelect(o.key) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(o.label) }
        }
    }
}
