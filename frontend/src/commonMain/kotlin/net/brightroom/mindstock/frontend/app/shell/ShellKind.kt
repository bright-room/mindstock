package net.brightroom.mindstock.frontend.app.shell

import androidx.compose.runtime.staticCompositionLocalOf

/** アプリ外枠の幅レイアウト種別。 */
enum class ShellKind {
    Compact,
    Wide,
}

/** Material3 Expanded 標準閾値。840dp 以上でデスクトップ・サイドバー、それ未満で浮遊ボトムナビ。 */
fun shellKindFor(widthDp: Int): ShellKind = if (widthDp >= 840) ShellKind.Wide else ShellKind.Compact

/**
 * 現在の shell が Wide(デスクトップ・サイドバー)かどうかを feature 層へ伝える。
 * AppShell が提供する。feature が `currentWindowAdaptiveInfo()` を直接触らないための seam。
 */
val LocalIsWideShell = staticCompositionLocalOf { false }
