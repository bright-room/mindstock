package net.brightroom.mindstock.frontend.designsystem.theme

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
    val f = notoSansJpFamily()
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = f),
        displayMedium = d.displayMedium.copy(fontFamily = f),
        displaySmall = d.displaySmall.copy(fontFamily = f),
        headlineLarge = d.headlineLarge.copy(fontFamily = f),
        headlineMedium = d.headlineMedium.copy(fontFamily = f),
        headlineSmall = d.headlineSmall.copy(fontFamily = f),
        titleLarge = d.titleLarge.copy(fontFamily = f),
        titleMedium = d.titleMedium.copy(fontFamily = f),
        titleSmall = d.titleSmall.copy(fontFamily = f),
        bodyLarge = d.bodyLarge.copy(fontFamily = f),
        bodyMedium = d.bodyMedium.copy(fontFamily = f),
        bodySmall = d.bodySmall.copy(fontFamily = f),
        labelLarge = d.labelLarge.copy(fontFamily = f),
        labelMedium = d.labelMedium.copy(fontFamily = f),
        labelSmall = d.labelSmall.copy(fontFamily = f),
    )
}
