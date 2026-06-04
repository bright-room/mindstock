---
paths:
  - "frontend/**/*.kt"
---

# Frontend Compose Conventions

Composable とテストの慣行。

## Rule

- Composable 関数は PascalCase・戻り値 `Unit`・副作用は `LaunchedEffect` 等の effect に閉じる。
- state hoisting: 状態は ViewModel(画面)かローカル `remember`(一時 UI)に持ち、子 Composable は引数 + コールバックで受ける(状態を子に隠さない)。
- `@Composable @Preview` は任意で付けてよい。
- テスト: commonTest は **Kotest FunSpec 不可**。`kotlin.test.@Test` + Kotest assertions(`io.kotest.matchers.*`)を使う。検証可能なロジック(PKCE 整合 / RpcOutcome 変換 / boot 分岐 / トークン載せ替え)をテストし、UI 描画網羅は追わない。

## Why

単方向と state hoisting で再利用とテスト容易性を確保。FunSpec 不可は KMP commonTest の制約。

## 関連

- rule: [frontend-architecture](frontend-architecture.md) / [testing](testing.md)(横断)
