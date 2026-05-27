package net.brightroom.mindstock.frontend.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import mindstock.frontend.generated.resources.NotoSansJP_Black
import mindstock.frontend.generated.resources.NotoSansJP_Bold
import mindstock.frontend.generated.resources.NotoSansJP_ExtraBold
import mindstock.frontend.generated.resources.NotoSansJP_ExtraLight
import mindstock.frontend.generated.resources.NotoSansJP_Light
import mindstock.frontend.generated.resources.NotoSansJP_Medium
import mindstock.frontend.generated.resources.NotoSansJP_Regular
import mindstock.frontend.generated.resources.NotoSansJP_SemiBold
import mindstock.frontend.generated.resources.NotoSansJP_Thin
import mindstock.frontend.generated.resources.Res
import org.jetbrains.compose.resources.Font

@Composable
fun notoSansJpFamily(): FontFamily =
    FontFamily(
        Font(Res.font.NotoSansJP_Thin, FontWeight.Thin, FontStyle.Normal),
        Font(Res.font.NotoSansJP_ExtraLight, FontWeight.ExtraLight, FontStyle.Normal),
        Font(Res.font.NotoSansJP_Light, FontWeight.Light, FontStyle.Normal),
        Font(Res.font.NotoSansJP_Regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.NotoSansJP_Medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.NotoSansJP_SemiBold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.NotoSansJP_Bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.NotoSansJP_ExtraBold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(Res.font.NotoSansJP_Black, FontWeight.Black, FontStyle.Normal),
    )

@Composable
fun appTypography(): Typography {
    val fontFamily = notoSansJpFamily()
    val default = Typography()
    return Typography(
        displayLarge = default.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = default.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = default.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = default.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = default.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = default.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = default.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = default.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = default.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = default.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = default.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = default.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = default.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = default.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = default.labelSmall.copy(fontFamily = fontFamily),
    )
}
