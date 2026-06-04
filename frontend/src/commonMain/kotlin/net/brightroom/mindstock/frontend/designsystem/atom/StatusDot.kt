package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** ok/low/out の 3 区分。color は呼び出し側が MindstockTokens から渡す。 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(color = color, shape = CircleShape, modifier = modifier.size(8.dp)) {}
}
