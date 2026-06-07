package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 利用者ごとのアバター色。モック `data.jsx` の `USERS[*].color`(利用者別の clay 調和色)に倣い、
 * 安定キー(表示名)から決定的に clay と調和するパレットへ写す。
 * mock の特定2ユーザ(me=テラコッタ/yui=インディゴ)の hue 完全一致は世帯データ依存で不可能だが、
 * 「人ごとに安定した別色がつく」という挙動を再現する。先頭はアクセント(自分相当)。
 */
private val avatarPalette =
    listOf(
        Color(0xFFC76743), // terracotta(accent)  oklch(0.62 0.13 41)
        Color(0xFF5E6FA8), // indigo               oklch(0.58 0.12 268)
        Color(0xFF3F8068), // pine                 oklch(0.55 0.10 158)
        Color(0xFFA65C7B), // plum                 oklch(0.55 0.12 350)
        Color(0xFFB07B3C), // amber                oklch(0.63 0.11 70)
        Color(0xFF4E8A8C), // teal                 oklch(0.58 0.07 200)
    )

fun avatarColorOf(key: String): Color {
    if (key.isEmpty()) return avatarPalette[0]
    var hash = 0
    for (c in key) hash = (hash * 31 + c.code) and 0x7fffffff
    return avatarPalette[hash % avatarPalette.size]
}
