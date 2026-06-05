# P6-0: frontend 土台 + ルール 設計

フルリプレイス最終フェーズ **P6(`:frontend` / Compose Multiplatform Wasm)** の最初のサブプロジェクト。
画面モック（Claude Design ハンドオフ、`docs/ref/mindstock.zip`）を起点に、frontend の **アーキテクチャ土台**を縦に 1 本通し、そこから `.claude/rules/` の frontend ルールを確立する。

- 起点モック: `app/*.jsx`(shell/screens)・`app/core.jsx`(デザイントークン/atoms)・`app/data.jsx`(モック store)。iOS デバイス枠・tweaks パネル・トーン/角丸切替はプロトタイプ用の足場であり**製品には含めない**。
- backend は P0–P5c で完成済み（`:rpc` の `@Rpc` service、`:backend:api` の presentation Controller + Zitadel 認証配線）。本フェーズはその RPC を呼ぶ frontend を作る。
- 現状の `:frontend` は P0 で空スタブ化済み（`App.kt` が `Text("mindstock")` のみ）。build.gradle には Compose / lifecycle-viewmodel / adaptive / kotlinx-rpc client / NotoSansJP フォント / `generateAuthConfig` タスクが既に配線されている。

## スコープ

本サブプロジェクト **P6-0** は土台のみ。具体的な業務画面は後続サブプロジェクト（下記「P6 全体の分割」）。

P6-0 の成果物:

1. アーキテクチャ土台（構成 / 認証 / RPC クライアント / セッション / テーマ / 外枠 / ViewModel・エラー規約）
2. それを実証する**参照画面 1 枚**（在庫一覧 = StockHome）
3. `.claude/rules/` の frontend ルール **6 本**

## 確定済みの設計判断（ユーザ承認済み）

| 論点 | 決定 |
|---|---|
| 認証 | OIDC PKCE を正式に実装（土台に含める）。旧 frontend の PKCE は full-replace で削除済み・参照しない |
| テーマ | clay 単一トーン → Material3。iOS 枠・トーン/角丸切替・tweaks パネルは破棄 |
| デバイス | mobile（下部ナビ）+ desktop（サイドバー）両対応（material3-adaptive） |
| UI 基盤 | **Material3 Expressive** をベース採用（version `1.10.0-alpha05`）。clay はカスタム `ColorScheme` |
| パッケージ | Jetpack Compose 推奨アーキテクチャ（UI 層: 画面ごと ViewModel + UiState・単方向 / data 層: Repository が RPC 隠蔽） |
| マルチプラットフォーム | 将来 android / ios / desktop(JVM) も見据え、移植可能コードは `commonMain`、platform 依存は `expect/actual` |
| 大規模耐性 | `:frontend` 単一 gradle モジュール + 「継ぎ目」。モジュール分割は将来ステップ（発動条件付き） |
| ナビゲーション | Navigation Compose（型安全ルート・マルチプラットフォーム対応）。モーダルシートは UI 状態 |
| フォント / i18n | `commonMain/composeResources/font` の NotoSansJP を使用。文言は最初から Compose Resources string resources（まず `ja`） |
| ルールのタイミング | ルールは本 spec のアーキテクチャから**先に書き起こす**。土台 + 参照画面はルールを**検証・補正**する（ルールの種にはしない） |

---

## 1. 構成方針

### 1.1 単一モジュール + 継ぎ目

`:frontend` は単一 gradle モジュールのまま、将来モジュール分割できる**継ぎ目**を最初から入れる。境界はパッケージ構成と frontend ルールで担保する。

- 画面ゼロの現時点でマルチモジュール gradle グラフを組むのは早すぎる儀式。最初の画面実装を遅らせるだけで、境界はパッケージ + ルールで足りる。
- マルチモジュール分割は**将来ステップ**として記録し、発動条件を明示: 「ビルド時間が無視できなくなる」「feature 数が増えて feature 間の独立ビルド/テストが欲しくなる」。今はやらない。
- 分割時に切り出す単位: `designsystem` / `core`（rpc・session・auth・navigation）/ `feature:*` / `app`。この単位でパッケージを切っておく。

### 1.2 層

Jetpack Compose 推奨に倣う。

- **UI 層**: 画面ごとに `ViewModel`（`org.jetbrains.androidx.lifecycle`）+ `sealed UiState`。単方向データフロー（UI は state を購読し、イベントを ViewModel に送るだけ）。
- **data 層**: `Repository` が RPC を隠蔽する。**ViewModel は RPC サービス（`*RpcService`）を直接触らない**。Repository が `RpcClientProvider` 経由でサービスを呼び、`RpcResult` を domain 型 or アプリ内結果型に変換して返す。
- domain 型（`:domain` の集約 / VO）と RPC 契約型（`:rpc`）はそのまま UI 層まで使ってよい（薄いマッピングのみ）。frontend 固有の表示変換が要る箇所だけ feature 内に置く。

### 1.3 KMP-ready は「構造的意図」（検証済みではない）

率直に記録する: 現状のターゲットは `js(IR)` と `wasmJs` の **web 2 種のみ**。両方 web なので、`commonMain` に web 前提コードを書いてもコンパイルが通ってしまい、非移植性に気づけない。

→ 「`commonMain` に置けば移植可能」と過信しない。**唯一の実効的な移植境界は `expect/actual` の規律**。platform 依存は必ず `expect/actual` に切り出す:

- PKCE: authorize への redirect / callback パラメータ取得 / `code_verifier`・`code_challenge`（SHA-256）生成 / token 保存（web = sessionStorage）
- RPC クライアントの Ktor HttpClient engine（web = Js engine、将来は OkHttp/Darwin 等）

将来ターゲット追加は「ターゲット + 各 `actual` を足す」追加作業で済む構造にする（書き直しを避ける）が、実際の移植性は将来ターゲット追加時に初めて検証される。

---

## 2. ソース / パッケージ構成

```
frontend/src/
  commonMain/
    kotlin/net/brightroom/mindstock/frontend/
      designsystem/      Material3 Expressive + clay ColorScheme + LocalMindstockTokens(status/影/半径)
                         atoms: Btn, Thumb, StatusDot, StockBar, Seg, Stepper, RoundBtn, Sheet, AppIcon …
                         theme: MindstockTheme(), Typography(NotoSansJP)
      core/
        rpc/             RpcClientProvider, RpcResult→結果ヘルパ, RpcError ハンドリング
        session/         AppSession(residentId/displayName/households/activeHouseholdId)
        auth/            PKCE フロー本体（platform 部は expect: AuthRedirector, PkceGenerator, TokenStore, CallbackReader）
        navigation/      型安全 Route 定義 + AppNavHost
      feature/
        inventory/       ui/(StockHomeScreen) + InventoryViewModel + InventoryUiState + data/(InventoryRepository)
        （household/ catalog/ … は P6-1〜3 で追加）
      app/               App() ルート, AppViewModel(起動シーケンス/phase/活動世帯), shell/(adaptive 外枠)
      i18n/              文言アクセスの薄いラッパ（必要なら）
    composeResources/
      font/              NotoSansJP-*.ttf（既存）
      values/            strings.xml（ja。将来 values-en/ 追加で多言語化）
  webMain/
    kotlin/…             PKCE actual（redirect/callback/crypto/sessionStorage）, Ktor Js engine 提供
  jsMain / wasmJsMain    web 2 ターゲット固有（最小）
  （将来: androidMain / iosMain / jvmMain）
```

> `commonMain` は現状 build.gradle で依存宣言済み。`webMain` は compose-web convention が `js`/`wasmJs` の共通親として生成する中間 source set。

---

## 3. 認証（OIDC PKCE）— フルページリロード前提

PKCE は authorize エンドポイントへ **ページごと遷移**し、callback で**まっさらに再起動**する。状態はリロードを跨がない前提で起動シーケンスを設計する。

### 3.1 起動シーケンス

```
App 起動（AppViewModel.boot()）
 └ 現在 URL は /auth/callback か?
     ├ Yes: sessionStorage の code_verifier で token 交換 → token 保存 → "/" へ置換遷移
     └ No : 保存済みトークンある?（有効性チェック）
              ├ 無 or 失効 → authorize へ redirect（code_verifier 生成 → sessionStorage 保存 → code_challenge 付与）
              └ 有       → resident.me() を呼ぶ
                            ├ Ok(Resident)                  → AppSession 構築 → app（在庫）へ
                            ├ Err(NotFound)/未登録          → オンボーディング（表示名登録 → 世帯作成）へ
                            └ Err(Unauthorized)             → token 破棄 → authorize へ redirect
```

- backend は「JWT 有効だが Resident 未登録」を `MindstockSession.Unregistered` で表し、`/resident/register` ルートのみ通す。frontend は `resident.me()` の結果で登録/未登録を判定する（`bootstrap()` RPC は存在しない）。

### 3.2 expect/actual に切り出す platform 部

| expect | web actual |
|---|---|
| `AuthRedirector.toAuthorize(challenge, state)` / `toLogout()` | `window.location` 遷移 |
| `CallbackReader.read()` → code/state | `window.location.search` パース |
| `PkceGenerator.generate()` → verifier + S256 challenge | Web Crypto `crypto.subtle.digest` |
| `TokenStore`（save/load/clear） | `sessionStorage` |

`AuthConfig`（issuer / clientId / redirectUri / audience / …）は build.gradle の `generateAuthConfig` が生成済み。これを使う。

### 3.3 token の RPC への載せ方

検証済みの backend 仕様（`backend/api/configuration/auth/`）に従う:

- WS ハンドシェイクで `Sec-WebSocket-Protocol: mindstock.v1, mindstock.bearer.<base64url(jwt)>` を提示。
- backend は `mindstock.v1` だけを echo（bearer は echo しない）、`mindstock.bearer.` から JWT を復元して検証。
- ブラウザは `Authorization` ヘッダや任意 `Sec-` ヘッダを WebSocket に付けられないため、この subprotocol 方式が唯一の手段。

---

## 4. RPC クライアント + セッション

### 4.1 RpcClientProvider（core/rpc）

- 認証トークンを持って `KtorRpcClient` を 1 本張る（WebSocket、`/api/v1`、subprotocol `[mindstock.v1, mindstock.bearer.<jwt>]`）。
- 各 `@Rpc` サービスを lazy 公開: `resident` / `residentRegister` / `household` / `householdRegister` / `catalog` / `product` / `productRegister` / `stock` / `stockRegister`。backend のルートが `/api/v1/<ctx>` と `/api/v1/<ctx>/register` に分かれている点に対応。
- `KrpcJson`（`:shared`）でシリアライズ。HttpClient engine は `expect`（web = Js engine）。
- トークン失効・`Unauthorized` 受信時は token 破棄 → 再 authorize redirect（3.1 と一貫）。

### 4.2 AppSession（core/session）

- `StateFlow` で公開: `residentId` / `displayName` / `households: Households` / `activeHouseholdId: HouseholdId`。
- アクティブ世帯は `household.list()` から決定（初期は先頭、世帯切替で更新）。
- 各 feature の Repository は `householdId` を AppSession から取得する（画面が個別に持ち回らない）。

---

## 5. テーマ / デザインシステム（designsystem）

### 5.1 Material3 Expressive の封じ込め

- `1.10.0-alpha05` の Expressive API（`MaterialExpressiveTheme`、expressive motion/shape）はベース採用するが **alpha・`strictly` ピン**。API 変動を feature 層に漏らさないため、**designsystem 層に封じ込める**。
- **feature は Material3 を直接 import しない**。designsystem の atoms（`Btn` / `Sheet` / `StockBar` 等）と `MindstockTheme` 経由でのみ UI を組む。→ ルール化。

### 5.2 clay トークン → Material3

- `core.jsx` の clay oklch 値を Material3 カスタム `ColorScheme`（light）に写す。
- Material3 に無いモック固有トークン（status色 ok/low/out・影 sm/md/lg/pop・独自半径 sm/md/lg/xl）は `CompositionLocal`（`LocalMindstockTokens`）で供給。
- モックのバウンド感（Stepper のポップ、Sheet のスプリング）は Expressive の motion/shape で表現。

### 5.3 フォント / Typography

- `commonMain/composeResources/font` の NotoSansJP（9 ウェイト）を Compose Resources `Font()` で `FontFamily` 化し、Material3 `Typography` の各 style に適用。

### 5.4 i18n

- 文言は最初から Compose Resources の string resources（`composeResources/values/strings.xml`、まず `ja`）に置く。**コードへの文言直書きは禁止**（ルール化）。将来 `values-en/` 等を足すだけで多言語化。

### 5.5 アイコン

- モックの自前 SVG ラインアイコン群は、`material-icons-extended`（依存済み）で近いものに対応づける。designsystem に `AppIcon` を 1 枚噛ませ、feature からはアイコン名（semantic）でのみ参照する（将来差し替え可能に）。

---

## 6. アプリ外枠（app/shell）

- `material3-adaptive-navigation-suite` で **mobile = 下部ナビ / desktop = サイドバー**を画面幅により自動切替（モック `app.jsx` の `BottomNav` / `DesktopChrome` 相当）。
- 共通 chrome: 中央「追加」アクション・世帯切替・お知らせ（将来機能は OFF 固定・操作不可）・ユーザ表示。
- ナビ項目: 在庫 / 買い物 / 履歴 / 設定（モックの 5 タブから中央 FAB を除いた 4 つ + 追加アクション）。

---

## 7. ViewModel / UiState / エラー規約

### 7.1 単方向 + UiState

- 画面ごと `ViewModel`。状態は `sealed interface XxxUiState { Loading; Content(...); Error(...) }` を `StateFlow` で公開。
- UI（Composable）は state を購読し、ユーザ操作を ViewModel のメソッドに委譲するのみ。ロジックを Composable に書かない。

### 7.2 RpcResult / RpcError の一本化

- `RpcResult<T, RpcError>` の処理は core/rpc の共通ヘルパに集約。`Ok` → 成功、`Err` → `UiState.Error` or トースト。
- `RpcError` の variant を `when` で**網羅**（新 variant 追加にコンパイルで気づく）:
  - `Unauthorized` → token 破棄 → 再 authorize（§3.1）
  - `NotFound` → 文脈に応じ Error 表示 or 空状態
  - `BadRequest(field, reason)` → 入力エラーとしてフォームに表示
  - `Conflict(reason)` → トースト（重複登録など）
  - `Internal(reason)` → 汎用エラートースト（詳細は出さない）
- トーストはモックの `Toast` 相当を Snackbar/独自オーバーレイで実装。
- frontend でも `nullable 戻り値原則禁止`（`error-handling.md`）を踏襲。不在は `RpcError` / sealed で表す。

---

## 8. 参照画面（土台の実証）

**在庫一覧（StockHome）** を参照画面として 1 枚作り、§1〜7 を縦に貫いて土台を実証する:

- `InventoryRepository`（`product.list(householdId)` 隠蔽）→ `InventoryViewModel`（`StockHome 用 UiState`）→ `StockHomeScreen`（designsystem atoms で grid/list 表示）の経路を構築。
- `product.list` 成功/失敗 → `RpcOutcome` → UiState の分岐、補充/消費（`stockRegister.replenish`/`consume`）を `InventoryRepository` に実装。
- `StockStatus` → status 色（`LocalMindstockTokens`）のマッピング、`SegmentedControl` でのビュー切替。

**P6-0 における「実証」の範囲（2026-06-05 確定）**: 上記の Repository / ViewModel / atoms / `RpcOutcome` 変換 / PKCE boot シーケンス（callback / token / `me()` の 3 分岐）を **commonTest の単体テスト** で検証し、`App()` の **boot→shell 配線**（`AuthViewModel` → `AppShell`）をコンパイル疎通で実証する。

`App()` が `StockHomeScreen` を**実画面として live レンダリングする配線**（実 `householdId` の取得=`household.list()`/世帯選択を要し、live 検証には backend=Zitadel/Postgres の起動を要する）は **P6-1 の最初のタスク**に送る。P6-0 はこの段階までを土台の完了点とする。

これは**ルールの検証用**であり、ルールの出所ではない（§9）。

---

## 9. ルール（`.claude/rules/`、frontend 6 本）

ルールは**本 spec のアーキテクチャから先に書き起こす**（画面実装の前）。土台 + 参照画面は書いたルールを検証・補正する。既存ルールの書式（`paths:` フロントマター・Rule/Why/How to apply・末尾「関連」リンク）に倣う。命名はプレフィックス統一（`frontend-`）。

| ルール | `paths:` | 内容 |
|---|---|---|
| `frontend-architecture.md` | `frontend/**/*.kt` | 層境界（UI/data）・feature パッケージ・ViewModel+UiState・単方向・Repository が RPC 隠蔽・将来モジュール分割の継ぎ目と発動条件 |
| `frontend-kmp-structure.md` | `frontend/**/*.kt` | commonMain 原則・platform 依存は `expect/actual` のみ・web 前提を commonMain に書かない・「web2種のみで通る」落とし穴の明記 |
| `frontend-rpc-and-error.md` | `frontend/**/*.kt` | RpcClientProvider 経由・Repository 隠蔽・`RpcResult`/`RpcError` 網羅処理・トースト規約・nullable 禁止の踏襲 |
| `frontend-designsystem.md` | `frontend/**/*.kt` | Material3 Expressive を designsystem 層に封じ込め・clay トークン・atoms 経由・**feature からの直 Material3 import 禁止** |
| `frontend-i18n-and-font.md` | `frontend/**/*.kt` | 文言は string resources・**コード直書き禁止**・NotoSansJP / Typography |
| `frontend-compose-conventions.md` | `frontend/**/*.kt` | Composable 命名 / state hoisting / preview・テスト方針（commonTest は Kotest FunSpec 不可、`kotlin.test.@Test` + Kotest assertions） |

既存の横断ルール（`error-handling.md` = `**/*.kt`、`testing.md` = `**/*Test.kt`）は frontend にも自動適用される。frontend ルールはそれと矛盾しない範囲で frontend 固有事項のみ書く。

---

## 10. ビルド / テスト留意

- frontend の WasmJs フルビルド（`wasmJsBrowserDistribution` / `ProductionWebpack`）はローカルで OOM しうる（既知）。コンパイル検証は **`:frontend:compileKotlinWasmJs`** 中心とし、配布生成は CI に委ねる。
- commonTest は **Kotest FunSpec 不可**。`kotlin.test.@Test` + Kotest assertions を使う。
- 検証可能なロジック（PKCE の verifier/challenge 整合、RpcResult→UiState 変換、起動シーケンスの分岐、token 載せ替え）は commonTest でテストする。UI 描画の網羅テストは追わない。

---

## 11. P6 全体の分割（後続サブプロジェクト）

| # | サブプロジェクト | 範囲 |
|---|---|---|
| **P6-0**（本 spec） | 土台 + ルール | 認証 / RPC / セッション / テーマ / 外枠 / 規約 + 参照画面（在庫一覧）+ ルール 6 本 |
| **P6-1** | 在庫まわり | 在庫ホーム（grid/list）・商品詳細・補充/消費シート・履歴タブ（`product.list`/`shoppingList`、`stock.history`/`activity`、`stockRegister.*`、`product.setWanted`） |
| **P6-2** | 商品 & マスタ | 商品追加（`catalog.search`/`lookupByJan`、`productRegister.adopt`/`addCustom`）・単位/最低在庫/画像設定（`changeUnit/Image/Minimum`）・アーカイブ（`archive`/`unarchive`/`listArchived`） |
| **P6-3** | 世帯 & オンボーディング | ログイン/オンボーディング・世帯切替/作成（`household.list`/`householdRegister.create`/`rename`/`leave`）・招待発行/参加（`createInvite`/`revokeInvite`/`previewInvite`/`join`）・メンバー管理（`changeRole`/`removeMember`）・設定（`residentRegister.rename`） |

各サブプロジェクトは P6-0 のルール確立後、それぞれ spec → plan → 実装のサイクルで進める。

---

## 関連

- spec（親）: [docs/superpowers/specs/2026-05-31-mindstock-full-replace-design/00-index.md](2026-05-31-mindstock-full-replace-design/00-index.md) — フルリプレイス全体設計
- spec（前段）: [docs/superpowers/specs/2026-06-04-p5c-presentation-and-wiring-design.md](2026-06-04-p5c-presentation-and-wiring-design.md) — presentation / 認証配線
- mock: `docs/ref/mindstock.zip`（`app/*.jsx`, `app/core.jsx`, `app/data.jsx`）
- backend 認証実装: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/`(WS subprotocol / JWT)
- rule（横断・既適用）: `error-handling.md` / `testing.md`
- rule（本 spec で新設）: `frontend-architecture.md` / `frontend-kmp-structure.md` / `frontend-rpc-and-error.md` / `frontend-designsystem.md` / `frontend-i18n-and-font.md` / `frontend-compose-conventions.md`
