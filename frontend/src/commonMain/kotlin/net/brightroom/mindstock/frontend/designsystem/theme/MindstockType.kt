package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** モック core.jsx の font 指定をプリセット化。AppText(style = MindstockType.xxx()) で使う。 */
object MindstockType {
    private fun base(family: FontFamily) = TextStyle(fontFamily = family)

    @Composable fun screenTitle() =
        base(
            notoSansJpFamily(),
        ).copy(fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, letterSpacing = (-0.02).em)

    @Composable fun greeting() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.Medium, fontSize = 13.sp)

    @Composable fun cardTitle() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.SemiBold, fontSize = 15.5f.sp)

    @Composable fun bigQty() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, fontFeatureSettings = "tnum")

    @Composable fun unitCaption() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.Medium, fontSize = 11.5f.sp)

    @Composable fun statusLabel() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5f.sp)

    @Composable fun summaryTitle() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)

    @Composable fun summarySub() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.Medium, fontSize = 12.5f.sp)

    @Composable fun sectionMeta() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

    @Composable fun button() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.SemiBold, fontSize = 15.5f.sp)
}
