package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** clay の TONES.clay(oklch→sRGB 変換済み)。 */
private val ClayColorScheme =
    lightColorScheme(
        primary = Color(0xFFC76743), // accent   oklch(0.62 0.132 41)
        onPrimary = Color(0xFFFFFBF4), // onAccent oklch(0.99 0.01 80)
        primaryContainer = Color(0xFFFFE3D3), // accentSoft oklch(0.935 0.038 52)
        background = Color(0xFFF6F2ED), // bg oklch(0.964 0.008 74)
        surface = Color(0xFFFFFDFA), // surface oklch(0.995 0.004 78)
        surfaceVariant = Color(0xFFFBF7F3), // surface2 oklch(0.978 0.007 76)
        onSurface = Color(0xFF2B2520), // ink oklch(0.27 0.013 58)
        onSurfaceVariant = Color(0xFF69625C), // sub oklch(0.50 0.013 58)
        outline = Color(0xFFE1DDD8), // line oklch(0.90 0.008 70)
        outlineVariant = Color(0xFFEAE7E4), // lineSoft oklch(0.93 0.006 70)
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MindstockTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMindstockTokens provides clayTokens) {
        MaterialExpressiveTheme(
            colorScheme = ClayColorScheme,
            typography = appTypography(),
            content = content,
        )
    }
}
