---
paths:
  - "frontend/**/*.kt"
---

# Frontend Architecture

`:frontend`(Compose Multiplatform)の層責務と依存方向。Jetpack Compose 推奨アーキに倣う。

## Rule

- **UI 層**: 画面ごとに `ViewModel`(`androidx.lifecycle.ViewModel`)+ `sealed interface XxxUiState`。状態は `StateFlow` で公開。Composable は state を購読しイベントを ViewModel に委譲するだけ(単方向データフロー)。ロジックを Composable に書かない。
- **data 層**: `Repository` が RPC を隠蔽する。**ViewModel / Composable は `*RpcService` を直接呼ばない**。ViewModel へは Repository インスタンスでなく **そのメソッド参照(関数型)** を渡す(テスト時にラムダで差し替え可能)。Repository が `RpcClientProvider` 経由でサービスを呼び、結果を返す。
- パッケージは **feature 単位**(`feature/<ctx>/` に ui / data / viewmodel / uistate を同居)+ 横断基盤(`designsystem/` `core/`)。技術レイヤ別ではなく責務別に切る。
- `:domain` の集約 / VO、`:rpc` の契約型はそのまま UI 層まで使ってよい(薄いマッピングのみ)。frontend 固有の表示変換が要る箇所だけ feature 内に置く。
- **将来モジュール分割の継ぎ目**: `:frontend` は単一モジュールだが、`designsystem` / `core` / `feature:*` / `app` の単位でパッケージを切り、いつでも gradle モジュールに切り出せる形を保つ。分割の発動条件は「ビルド時間が無視できなくなる」「feature 独立ビルド/テストが欲しくなる」。それまでは分割しない(早すぎる儀式を避ける)。
- **`app/` 層の位置付け**: `app/` は「配線ホスト」。`App.kt`(`webMain`)が AuthState に応じて VM を生成し、Repository メソッド参照を inject し、world-cross(世帯横断)な画面(オーバーレイ・シート・shell)を配置する。feature をまたぐ wiring と世帯横断 UI は `app/` に置く(個々の機能画面は `feature/<ctx>/`)。`app/shell/` はアプリ外枠(`AppShell` / `WideShell` / `BottomNav`)。
- **`ReadyContent`(認証後の本体)**: `App.kt` の `AuthState.Ready` 分岐は、`householdId`(= `sessionState.activeHouseholdId`)を起点に各タブ VM(在庫/買い物/活動/設定)を `remember(householdId)` で生成し、4 タブシェル本体(モックの AppContent/ScreenSwitch 相当)を `ReadyContent` として描画する。world-cross 状態 — 商品詳細オーバーレイ(`opened`)/ カタログオーバーレイ(`catalogOverlay`)/ 世帯シート(`settingsSheet`)— は呼び出し側(`Ready` 分岐)で `remember { mutableStateOf(...) }` して hoist し、`ReadyContent`(と `HouseholdSheets`)へ引数で渡す。複数の合成関数で共有する VM(`settingsVm` は設定タブと世帯スイッチャ、`productMasterVm` は Master/Settings 両オーバーレイで共有)も同様に `remember(householdId)` で呼び出し側に生成し、二重生成を避けて引数で渡す。`householdId` が変わると `remember(householdId)` の各 VM が作り直され、シートは `LaunchedEffect(householdId)` で閉じる。`selectedTab`(選択タブ)も他の world-cross 状態と同じ層に hoist し、`ReadyContent` へ `selectedTab` / `onSelectTab` で渡す(wide/compact シェル切替でも選択タブを保持する)。
- **Controller 三点セット**(`app/` が生成し `App.kt` で配線する横断コントローラ):
  - `ReauthController`(`core/auth/`)— `Unauthorized` 起点の再認証シグナル(`App.kt` が購読し RPC を閉じて authorize へ)
  - `ToastController`(`core/ui/`)— mutation 失敗等のトースト表示
  - `InventoryRefreshController`(`core/ui/`)— 在庫変更の波及シグナル(複数画面の再読込トリガー)。**refresh シグナルの購読は UI 層の `LoadWithRefresh`(`App.kt`)で一元化する**(各画面 VM は `load()` を提供するだけで、自前で refresh を collect しない)
  いずれも `App.kt` で `remember { }` し、`LaunchedEffect` で `collectLatest` 購読する。
- **`webMain` ソースセット**: `js` と `wasmJs` の web 2 ターゲット共通のブラウザコード置き場(`frontend/src/webMain/`)。`kotlinx.browser.window` 等の web API はここで使ってよい(`commonMain` には書かない — `frontend-kmp-structure.md` 参照)。現状 `Main.kt`(entry point)/ `App.kt`(配線)/ `WebAuthDeps.kt`。
- **`?preview` ハーネス(忠実化検証用)**: 画面を単体描画して確認するためのハーネスは `webMain` に `PreviewHarness.kt` を置き、`Main.kt` に `?preview=<screen>` クエリ分岐を足して使う(**現状リポジトリには未コミット**。必要時に追加し、撤去は `git checkout` + `rm`)。検証ループの手順は memory `fidelity-verify-loop-mechanics` を参照。
- **永続化の使い分け**: 認証トークン等の **セッション限定値は `SessionStorage`**(タブ終了で破棄。`auth/TokenStore.kt`)、ユーザ設定等の **永続値は `PreferenceStore`**(localStorage backed。タブ/リロードをまたいで保持。`core/preference/PreferenceStore.kt`。例: アクティブ世帯 ID)に保存する。新規の永続化はこの基準で選ぶ(秘匿性が低くタブ寿命でよいものは Session、デバイスに残したい設定は Preference)。

## Why

- 単方向 + ViewModel + Repository は、状態の出所と変更経路を 1 本化し、画面増加時も追跡可能に保つ。
- feature 別パッケージは「一緒に変わるものを一緒に置く」原則。将来のモジュール分割もパッケージ境界がそのまま使える。

## How to apply

✅ `InventoryViewModel` は **Repository のメソッド参照(suspend 関数型)を inject** して受ける(例: `loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>`)。配線側(`App.kt`)が `loadStocks = repository::list` のように関数参照を渡す。Repository は `RpcClientProvider` 経由でサービスを呼ぶ。VM は具象 Repository 型に依存せず、関数シグネチャだけに依存する。
❌ Composable / ViewModel が `rpcClient.withService<ProductRpcService>()` を直接呼ぶ。

## 関連

- spec: [docs/superpowers/specs/2026-06-05-p6-0-frontend-foundation-design.md](../../docs/superpowers/specs/2026-06-05-p6-0-frontend-foundation-design.md)
- rule: [frontend-rpc-and-error](frontend-rpc-and-error.md) / [frontend-kmp-structure](frontend-kmp-structure.md)
