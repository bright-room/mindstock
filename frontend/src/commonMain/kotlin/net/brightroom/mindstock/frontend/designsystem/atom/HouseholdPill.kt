package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow

/**
 * 世帯名 + 人数 + シェブロンの丸ピル（在庫ヘッダ）。
 * モック `screens-household.jsx:HouseholdPill` 準拠:
 * gap8 / padding 7px 10px 7px 8px / radius99 / surface / border line / shadow.sm / maxWidth220 /
 * アイコン箱24 r8 accentSoft・home14 / 名前 `700 13.5px/1` ink ellipsis / 人数 `600 11px/1` faint / chevD15 faint。
 */
@Composable
fun HouseholdPill(
    name: String,
    memberCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(99.dp)
    Row(
        modifier =
            modifier
                .widthIn(max = 220.dp)
                .softShadow(ShadowLevel.Sm, shape)
                .clip(shape)
                .background(tokens.surface)
                .border(1.dp, tokens.line, shape)
                .clickable(onClick = onClick)
                .padding(PaddingValues(start = 8.dp, top = 7.dp, end = 10.dp, bottom = 7.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(tokens.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconName.Home, contentDescription = null, size = 14.dp, tint = tokens.accent)
        }
        AppText(
            name,
            style = MindstockType.sectionMeta().copy(fontWeight = FontWeight.Bold, fontSize = 13.5f.sp, lineHeight = 13.5f.sp),
            color = tokens.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        AppText(
            "$memberCount 人",
            style = MindstockType.statusLabel().copy(fontSize = 11f.sp, lineHeight = 11f.sp),
            color = tokens.faint,
        )
        AppIcon(AppIconName.ChevronDown, contentDescription = null, size = 15.dp, tint = tokens.faint)
    }
}
