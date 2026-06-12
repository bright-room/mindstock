package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * モック core.jsx の `font: WEIGHT SIZEpx/LINEHEIGHT var(--f)` + letterSpacing を Compose に写したプリセット。
 * AppText(style = MindstockType.xxx()) で使う。各スタイルは「最も代表的なモック要素」の実数値で、
 * 画面ごとに異なる要素は呼び出し側で `.copy(...)` で上書きする（モックは要素ごとに font を直書きしており
 * 共通スケールを持たないため、ここは baseline）。
 */
object MindstockType {
    // CSS の line-height:N は字面の中央に行ボックスを置き上下余白を均す。Compose 既定は
    // フォントメトリクス由来の leading を上に偏らせるので、trim=Both + Center で CSS に寄せる。
    private val trimmed =
        LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        )

    @Composable
    private fun style(
        weight: FontWeight,
        size: Float,
        lineHeight: Float,
        letterSpacingEm: Float = 0f,
        features: String? = null,
    ) = TextStyle(
        fontFamily = LocalNotoSansJpFamily.current,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
        lineHeightStyle = trimmed,
        letterSpacing = letterSpacingEm.em,
        fontFeatureSettings = features,
    )

    /** 画面タイトル「在庫」等。mock `800 25px/1.1` ls -0.02em。 */
    @Composable fun screenTitle() = style(FontWeight.ExtraBold, 25f, 1.1f, -0.02f)

    /** 挨拶「こんにちは、〇〇」。mock `500 13px/1`。 */
    @Composable fun greeting() = style(FontWeight.Medium, 13f, 1.0f)

    /** 商品名(ProductCard)。mock `600 15.5px/1.35`。CompactCard 等は `.copy(fontSize=)` で。 */
    @Composable fun cardTitle() = style(FontWeight.SemiBold, 15.5f, 1.35f)

    /** 在庫数(ProductCard)。mock `700 30px/0.9` tnum。詳細46/Stepper52 は呼び出し側で上書き。 */
    @Composable fun bigQty() = style(FontWeight.Bold, 30f, 0.9f, features = "tnum")

    /** 単位キャプション。mock `500 11.5px/1`。 */
    @Composable fun unitCaption() = style(FontWeight.Medium, 11.5f, 1.0f)

    /** ステータスラベル。mock(StatusDot) `600 12.5px/1`。 */
    @Composable fun statusLabel() = style(FontWeight.SemiBold, 12.5f, 1.0f)

    /** バナー見出し。mock `700 16px/1.2`。 */
    @Composable fun summaryTitle() = style(FontWeight.Bold, 16f, 1.2f)

    /** バナー副文・補足。mock `500 12.5px/1.3`。 */
    @Composable fun summarySub() = style(FontWeight.Medium, 12.5f, 1.3f)

    /** セクションのメタ「すべて · X点」。mock `600 13px/1`。 */
    @Composable fun sectionMeta() = style(FontWeight.SemiBold, 13f, 1.0f)

    /** ボタンラベル。mock(Btn md) `600 15.5px/1` ls 0.01em。 */
    @Composable fun button() = style(FontWeight.SemiBold, 15.5f, 1.0f, 0.01f)
}
