package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * モックの SVG path を移植した、商品カテゴリ / ロゴ用の stroke スタイル ImageVector 群。
 * tint は AppIcon 側の Material3 Icon(tint=...) が上書きするため、ここでの stroke 色は黒固定でよい。
 */
object MindstockGlyphs {
    private fun stroke(
        name: String,
        pathData: String,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                addPath(
                    pathData = PathParser().parsePathString(pathData).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.7f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    pathFillType = PathFillType.NonZero,
                )
            }.build()

    val Drop = stroke("Drop", "M12 3.5C12 3.5 5.5 10 5.5 14.5a6.5 6.5 0 0 0 13 0C18.5 10 12 3.5 12 3.5z")
    val Egg = stroke("Egg", "M12 3c3.5 0 6 5 6 9a6 6 0 0 1-12 0c0-4 2.5-9 6-9z")
    val Bottle =
        stroke("Bottle", "M10 3h4v2.5l1.2 2.4a3 3 0 0 1 .3 1.3V19a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2V9.2a3 3 0 0 1 .3-1.3L10 5.5V3zM9 12.5h6")
    val Salt = stroke("Salt", "M8 9h8l-1 11H9L8 9zM9 9V6a3 3 0 0 1 6 0v3M11 4.5h2")
    val Bolt = stroke("Bolt", "M13 3L5 13h5l-1 8 8-10h-5l1-8z")
    val Leaf = stroke("Leaf", "M5 19C5 11 11 5 20 5c0 9-6 15-14 15a5 5 0 0 1-1-7M9 15c3-3 6-4 9-5")

    // Paper: モックは円+十字だが path のみでは完全表現できないため、十字 stroke で近似。
    val Paper = stroke("Paper", "M12 3.5v5M12 15.5v5M3.5 12h5M15.5 12h5")
}
