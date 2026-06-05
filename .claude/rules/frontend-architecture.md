---
paths:
  - "frontend/**/*.kt"
---

# Frontend Architecture

`:frontend`(Compose Multiplatform)の層責務と依存方向。Jetpack Compose 推奨アーキに倣う。

## Rule

- **UI 層**: 画面ごとに `ViewModel`(`androidx.lifecycle.ViewModel`)+ `sealed interface XxxUiState`。状態は `StateFlow` で公開。Composable は state を購読しイベントを ViewModel に委譲するだけ(単方向データフロー)。ロジックを Composable に書かない。
- **data 層**: `Repository` が RPC を隠蔽する。**ViewModel / Composable は `*RpcService` を直接呼ばない**。Repository が `RpcClientProvider` 経由でサービスを呼び、結果を返す。
- パッケージは **feature 単位**(`feature/<ctx>/` に ui / data / viewmodel / uistate を同居)+ 横断基盤(`designsystem/` `core/`)。技術レイヤ別ではなく責務別に切る。
- `:domain` の集約 / VO、`:rpc` の契約型はそのまま UI 層まで使ってよい(薄いマッピングのみ)。frontend 固有の表示変換が要る箇所だけ feature 内に置く。
- **将来モジュール分割の継ぎ目**: `:frontend` は単一モジュールだが、`designsystem` / `core` / `feature:*` / `app` の単位でパッケージを切り、いつでも gradle モジュールに切り出せる形を保つ。分割の発動条件は「ビルド時間が無視できなくなる」「feature 独立ビルド/テストが欲しくなる」。それまでは分割しない(早すぎる儀式を避ける)。

## Why

- 単方向 + ViewModel + Repository は、状態の出所と変更経路を 1 本化し、画面増加時も追跡可能に保つ。
- feature 別パッケージは「一緒に変わるものを一緒に置く」原則。将来のモジュール分割もパッケージ境界がそのまま使える。

## How to apply

✅ `InventoryViewModel` が `InventoryRepository` を呼び、Repository が `RpcClientProvider` 経由で `ProductRpcService.list()` を呼ぶ。
❌ Composable / ViewModel が `rpcClient.withService<ProductRpcService>()` を直接呼ぶ。

## 関連

- spec: [docs/superpowers/specs/2026-06-05-p6-0-frontend-foundation-design.md](../../docs/superpowers/specs/2026-06-05-p6-0-frontend-foundation-design.md)
- rule: [frontend-rpc-and-error](frontend-rpc-and-error.md) / [frontend-kmp-structure](frontend-kmp-structure.md)
