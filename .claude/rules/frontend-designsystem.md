---
paths:
  - "frontend/**/*.kt"
---

# Frontend Design System

Material3 Expressive と clay テーマの扱い。

## Rule

- UI 基盤は **Material3 Expressive**(`1.10.0-alpha05`、`strictly` ピン)。alpha API の変動を feature に漏らさないため、**`designsystem/` 層に封じ込める**。
- **feature 層(`feature/**`)は `androidx.compose.material3.*` を直接 import しない**。`designsystem/atom/` の atom(現状: `AppIcon` / `AppText` / `PrimaryButton` / `StatusDot` / `StockLevelBar` / `SegmentedControl`。Sheet/Stepper 等は P6-1 で追加)と `MindstockTheme` 経由でのみ UI を組む。必要な atom が無ければ designsystem に足してから使う。
- 例外: アプリ外枠(`app/shell/`)は adaptive ナビゲーション(`material3.adaptive.navigationsuite.NavigationSuiteScaffold`)等の chrome を直接使ってよい(feature 画面ではなくアプリ shell のため)。
- clay 配色は Material3 カスタム `ColorScheme` に写す。Material3 に無いモック固有トークン(status色 ok/low/out・影・独自半径)は `CompositionLocal`(`LocalMindstockTokens`)で供給。
- アイコンは `material-icons-extended` を `AppIcon` で 1 枚噛ませ、feature は semantic 名で参照(将来差し替え可能に)。

## Why

alpha ライブラリの API churn を 1 層に閉じ込め、feature の安定性を守る。テーマ変更を 1 箇所で完結させる。

## How to apply

✅ feature: `PrimaryButton(onClick = ...) { ... }` / `AppText(stringResource(...))`
❌ feature: `androidx.compose.material3.Button(...)` / `material3.Text(...)` を直接呼ぶ。

## 関連

- rule: [frontend-architecture](frontend-architecture.md) / [frontend-i18n-and-font](frontend-i18n-and-font.md)
- mock: `docs/ref/mindstock.zip`(`app/core.jsx` のトークン/atoms)
