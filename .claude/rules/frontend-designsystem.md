---
paths:
  - "frontend/**/*.kt"
---

# Frontend Design System

Material3 Expressive と clay テーマの扱い。

## Rule

- UI 基盤は **Material3 Expressive**(`1.10.0-alpha05`、`strictly` ピン)。alpha API の変動を feature に漏らさないため、**`designsystem/` 層に封じ込める**。
- **feature 層(`feature/**`)は `androidx.compose.material3.*` を直接 import しない**。`designsystem/atom/` の atom と `MindstockTheme` 経由でのみ UI を組む。**atom の正は `designsystem/atom/` のファイル一覧**(代表例: `AppText` / `AppIcon` / `PrimaryButton` / `StatusDot` / `StockLevelBar` / `SegmentedControl` / `Sheet` / `Stepper` / `MiniStepper` / `TextInput` / `CodeInput` / `SearchField` / `Toast` / `AddTile` / `Thumb` / `EmptyState` / `HouseholdPill` / `NavIconButton` / `WizardProgress` 等。逐一ここに列挙せずコードを参照)。必要な atom が無ければ designsystem に足してから使う。
- 例外: アプリ外枠(`app/shell/`)は独自シェル(`WideShell` / `BottomNav` / `AppShell`)を実装しており、その内部では `MaterialTheme` の基本 API(`Surface` / `Scaffold` 相当のレイアウト)や `currentWindowAdaptiveInfo`(幅 dispatcher)を直接使ってよい(feature 画面ではなくアプリ shell の chrome のため)。`NavigationSuiteScaffold` は使わない(独自実装に置き換え済み)。
- clay 配色は Material3 カスタム `ColorScheme` に写す。Material3 に無いモック固有トークン(status色 ok/low/out・影・独自半径)は `CompositionLocal`(`LocalMindstockTokens`)で供給。
- アイコンは `material-icons-extended` を `AppIcon` で 1 枚噛ませ、feature は semantic 名で参照(将来差し替え可能に)。
- **テーマ拡張群**(`designsystem/theme/`)は feature から使ってよい:
  - `LocalMindstockTokens`(`MindstockTokens.kt`、CompositionLocal)— status 色(ok/low/out)・影・独自半径などモック固有トークン。`LocalMindstockTokens.current` で参照
  - `MindstockType`(`MindstockType.kt`、object + `@Composable` 拡張)— 画面別タイポグラフィ(`screenTitle()` 等)
  - `Modifier.softShadow(level)`(`Shadow.kt`、`ShadowLevel` enum)— clay の柔らかい影
  - `avatarColorOf(key)`(`AvatarColor.kt`)— 利用者別アバター色(文字列ハッシュ → Color)

## Why

alpha ライブラリの API churn を 1 層に閉じ込め、feature の安定性を守る。テーマ変更を 1 箇所で完結させる。

## How to apply

✅ feature: `PrimaryButton(onClick = ...) { ... }` / `AppText(stringResource(...))`
❌ feature: `androidx.compose.material3.Button(...)` / `material3.Text(...)` を直接呼ぶ。

## 関連

- rule: [frontend-architecture](frontend-architecture.md) / [frontend-i18n-and-font](frontend-i18n-and-font.md)
- mock: `docs/ref/mindstock.zip`(`app/core.jsx` のトークン/atoms)
