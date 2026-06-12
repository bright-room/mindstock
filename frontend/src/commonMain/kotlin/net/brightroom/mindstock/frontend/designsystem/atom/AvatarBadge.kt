package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.avatarColorOf

/**
 * 利用者別色 + 白頭文字の円形アバター。メンバー行・履歴行など散在していた実装を統合。
 * 色は表示名から決定的に決まる([avatarColorOf])。サイズ・文字サイズ・ベーススタイルは呼び出し側が現物値で渡す。
 * 注意: 元実装は一部 tokens.onAccent(0xFFFFFBF4)を使っていたが、Color.White との微差のため White に統一。
 */
@Composable
fun AvatarBadge(
    name: String,
    size: Dp,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
    style: TextStyle = MindstockType.cardTitle(),
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(avatarColorOf(name)),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            name.take(1),
            style = style.copy(fontSize = textSize, lineHeight = textSize),
            color = Color.White,
        )
    }
}
