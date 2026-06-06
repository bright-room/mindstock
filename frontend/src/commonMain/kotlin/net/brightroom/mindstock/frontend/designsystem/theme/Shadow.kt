package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** モックの影スケール(sm/md/lg/pop)を Compose の単層 shadow で近似する。 */
enum class ShadowLevel(
    val elevation: Dp,
) {
    Sm(2.dp),
    Md(8.dp),
    Lg(18.dp),
    Pop(26.dp),
}

private val ShadowTint = Color(0xFF28211C)

fun Modifier.softShadow(
    level: ShadowLevel,
    shape: Shape = RoundedCornerShape(22.dp),
): Modifier =
    this.shadow(
        elevation = level.elevation,
        shape = shape,
        clip = false,
        ambientColor = ShadowTint,
        spotColor = ShadowTint,
    )
