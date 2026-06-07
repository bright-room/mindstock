package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material3 ColorScheme に無いモック固有トークン(status / 半径)。 */
data class MindstockTokens(
    val statusOk: Color,
    val statusOkSoft: Color,
    val statusLow: Color,
    val statusLowSoft: Color,
    val statusOut: Color,
    val statusOutSoft: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val sub: Color,
    val line: Color,
    val lineSoft: Color,
    val faint: Color,
    val radiusSm: Dp = 12.dp,
    val radiusMd: Dp = 16.dp,
    val radiusLg: Dp = 22.dp,
    val radiusXl: Dp = 28.dp,
)

/** clay の status 値(oklch→sRGB 変換済み・STATUS in core.jsx)。 */
val clayTokens =
    MindstockTokens(
        statusOk = Color(0xFF4E8872), // oklch(0.58 0.07 168)
        statusOkSoft = Color(0xFFDBF4E9), // oklch(0.945 0.03 168)
        statusLow = Color(0xFFCC9140), // oklch(0.70 0.12 72)
        statusLowSoft = Color(0xFFFFECCD), // oklch(0.95 0.045 80)
        statusOut = Color(0xFFC94D42), // oklch(0.585 0.16 28)
        statusOutSoft = Color(0xFFFFE4DD), // oklch(0.945 0.038 32)
        accent = Color(0xFFC76743),
        onAccent = Color(0xFFFFFBF4),
        accentSoft = Color(0xFFFFE3D3),
        bg = Color(0xFFF6F2ED), // colorScheme.background と同値(モック T.bg)
        surface = Color(0xFFFFFDFA),
        surface2 = Color(0xFFFBF7F3),
        ink = Color(0xFF2B2520),
        sub = Color(0xFF69625C),
        line = Color(0xFFE1DDD8),
        lineSoft = Color(0xFFEAE7E4),
        faint = Color(0xFFA59C94),
    )

val LocalMindstockTokens = staticCompositionLocalOf { clayTokens }
