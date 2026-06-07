package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens

/** セグメント進捗バー。total 個のうち current(1始まり)未満を accent で塗る。 */
@Composable
fun WizardProgress(
    total: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(total) { i ->
            val filled = i < current
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (filled) tokens.accent else tokens.line),
            )
        }
    }
}
