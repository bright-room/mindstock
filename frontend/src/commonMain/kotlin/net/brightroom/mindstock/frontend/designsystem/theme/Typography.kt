package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
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

// 毎回再構築を避けるため LocalNotoSansJpFamily で 1 度だけ構築して供給する。
// MindstockTheme が provide し、MindstockType.style() が .current で参照する。
internal val LocalNotoSansJpFamily =
    compositionLocalOf<FontFamily> { error("LocalNotoSansJpFamily not provided") }

// 注: Compose Resources の Font() は @Composable のため remember {} 内では呼べない
// (androidx の非 Composable Font() とは異なる)。Font() は @Composable 文脈の本体直下に
// 出し、FontFamily(...) の組み立てだけを remember で memoize する。
// remember のキーは **各 Font を列挙する**: Font() は非同期ロードで、バイト到着時に
// 返す Font の参照が更新される。キー無し remember だとロード前の Font で組んだ family を
// 作り直さず、フォントが永遠に適用されない(全文字が豆腐になる)。Font をキーにすることで
// 「無関係な再コンポーズでは再構築しない」かつ「ロード完了時には作り直す」を両立する。
@Composable
internal fun notoSansJpFamily(): FontFamily {
    val thin = Font(Res.font.NotoSansJP_Thin, FontWeight.Thin, FontStyle.Normal)
    val extraLight = Font(Res.font.NotoSansJP_ExtraLight, FontWeight.ExtraLight, FontStyle.Normal)
    val light = Font(Res.font.NotoSansJP_Light, FontWeight.Light, FontStyle.Normal)
    val regular = Font(Res.font.NotoSansJP_Regular, FontWeight.Normal, FontStyle.Normal)
    val medium = Font(Res.font.NotoSansJP_Medium, FontWeight.Medium, FontStyle.Normal)
    val semiBold = Font(Res.font.NotoSansJP_SemiBold, FontWeight.SemiBold, FontStyle.Normal)
    val bold = Font(Res.font.NotoSansJP_Bold, FontWeight.Bold, FontStyle.Normal)
    val extraBold = Font(Res.font.NotoSansJP_ExtraBold, FontWeight.ExtraBold, FontStyle.Normal)
    val black = Font(Res.font.NotoSansJP_Black, FontWeight.Black, FontStyle.Normal)
    return remember(thin, extraLight, light, regular, medium, semiBold, bold, extraBold, black) {
        FontFamily(thin, extraLight, light, regular, medium, semiBold, bold, extraBold, black)
    }
}

@Composable
fun appTypography(): Typography {
    val f = LocalNotoSansJpFamily.current
    return remember(f) {
        val d = Typography()
        Typography(
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
}
