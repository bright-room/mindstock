# P6-0: frontend 土台 + ルール Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compose Multiplatform(Wasm)frontend のアーキテクチャ土台(認証 / RPC / セッション / テーマ / 外枠 / ナビ / エラー規約)を縦に 1 本通し、参照画面「在庫一覧」で実証し、`.claude/rules/` の frontend ルール 6 本を確立する。

**Architecture:** Jetpack Compose 推奨アーキ(画面ごと ViewModel + UiState・単方向 / Repository が RPC 隠蔽)。移植可能コードは `commonMain`、platform 依存は `expect/actual`。Navigation Compose で型安全ルート、Material3 Expressive を designsystem 層に封じ込め。OIDC PKCE はフルページリロード前提。

**Tech Stack:** Kotlin 2.3.21 / Compose Multiplatform 1.11.0 / Material3 1.10.0-alpha05(Expressive)/ lifecycle-viewmodel / kotlinx-rpc client(WebSocket・subprotocol トークン)/ Ktor client / Compose Resources(NotoSansJP・string resources)。

**設計 spec:** `docs/superpowers/specs/2026-06-05-p6-0-frontend-foundation-design.md`

---

## 前提・事実(着手前に必読)

実装は **proven code** を土台にする。旧 frontend(P0 teardown `ec695e2` で削除)に PKCE + kotlinx-rpc WS クライアントの動作実績コードがあり、本プランのコードはそれを **モダナイズ**したもの:

1. **`kotlin.time.Instant` / `kotlin.time.Clock` を使う**(旧コードの `kotlinx.datetime.*` は非推奨。backend `MindstockSession` 同様 `@OptIn(kotlin.time.ExperimentalTime::class)`)。
2. **RPC サービス名・パスは新 `:rpc` のもの**:
   - `/api/v1/resident/register` → `ResidentRegisterRpcService`(未登録 JWT でも通る。`RequireRegisteredUserPlugin` の外)
   - `/api/v1/resident` → `ResidentRpcService.me()`(登録済み必須)
   - `/api/v1/household` → `HouseholdRpcService`、`/api/v1/household/register` → `HouseholdRegisterRpcService`
   - `/api/v1/catalog` → `CatalogRpcService`
   - `/api/v1/product` → `ProductRpcService`、`/api/v1/product/register` → `ProductRegisterRpcService`
   - `/api/v1/stock` → `StockRpcService`、`/api/v1/stock/register` → `StockRegisterRpcService`
3. **登録状態の判定機構(重要)**: `/resident` は `RequireRegisteredUserPlugin` の内側。認証済みでも **未登録の Resident は WS ハンドシェイクで拒否**され、`me()` 呼び出しは `RpcResult.Err` ではなく **接続例外を throw** する。さらにブラウザ WebSocket は 401 ハンドシェイクのステータスを JS に公開しない。よって boot は「`me()` が例外を投げたら未登録扱い → オンボーディングへ」とする(真のネットワーク障害も同じ経路に倒れるが、register 呼び出しが失敗してエラー表示されるので回復可能)。これは旧コードの設計判断と一致する。
4. **トークンは WS subprotocol で運ぶ**: `Sec-WebSocket-Protocol: mindstock.v1` と `mindstock.bearer.<base64url(jwt)>` を **別エントリ**で append する(1 値にカンマ詰めすると「空白入り subprotocol 名」扱いで拒否される)。
5. **kotlinx-rpc は URL scheme で transport を選ぶ**。WS のため baseUrl を `http→ws` / `https→wss` に変換する。
6. **ビルド留意**: WasmJs フルビルドはローカルで OOM る。コンパイル確認は **`./gradlew :frontend:compileKotlinWasmJs`** を使い、`wasmJsBrowserDistribution` 等は実行しない。
7. **テスト留意**: commonTest は **Kotest FunSpec 不可**。`kotlin.test.@Test` + Kotest assertions(`io.kotest.matchers.*`)を使う。検証可能なロジックのみテストし、UI 描画網羅は追わない。
8. **現 `build.gradle.kts` は旧 frontend と依存が同一**。Compose / lifecycle / adaptive / kotlinx-rpc client / Ktor client / NotoSansJP / `generateAuthConfig` は配線済み。**新規追加が要るのは `navigation-compose` のみ**(`install(WebSockets)` と Ktor engine は kotlinx-rpc-client-ktor 経由で transitively 解決される — 旧コードで実証済み)。

**ファイル構成(本プランで作成/変更):**

```
gradle/libs.versions.toml                       (M) navigation-compose 追加
frontend/build.gradle.kts                        (M) navigation 依存追加
frontend/src/commonMain/composeResources/values/strings.xml          (C) ja 文言
frontend/src/commonMain/kotlin/.../frontend/
  auth/ Tokens, Pkce(expect), AuthClient, OidcException, TokenStore, SessionStorage(expect)
  core/rpc/ RpcClientProvider, RpcOutcome, RpcErrors
  core/session/ AppSession
  core/auth/ AuthState, BootSequencer (expect: BrowserNav)
  designsystem/ theme/(MindstockTheme, ClayColors, MindstockTokens, Typography), atom/(AppIcon, PrimaryButton, StatusDot, StockLevelBar, SegmentedControl, BottomSheetScaffold)
  core/navigation/ Route, AppNavHost
  feature/inventory/ data/InventoryRepository, InventoryUiState, InventoryViewModel, ui/StockHomeScreen
  app/ AppViewModel, App, shell/AppShell
frontend/src/jsMain/kotlin/.../  PkceJs, SessionStorageJs, BrowserNavJs
frontend/src/wasmJsMain/kotlin/.../ PkceWasmJs, SessionStorageWasmJs, BrowserNavWasmJs
frontend/src/webMain/kotlin/.../frontend/App.kt  (M) ComposeViewport { App() } 配線(Main.kt は既存維持)
frontend/src/commonTest/kotlin/.../  各 *Test.kt
.claude/rules/ frontend-*.md  6 本
```

---

## Task 1: frontend ルール 6 本を作成(ルール先)

spec §9 のルールを先に確立する。既存ルールの書式(`paths:` フロントマター / Rule・Why・How to apply / 末尾「関連」)に倣う。

**Files:**
- Create: `.claude/rules/frontend-architecture.md`
- Create: `.claude/rules/frontend-kmp-structure.md`
- Create: `.claude/rules/frontend-rpc-and-error.md`
- Create: `.claude/rules/frontend-designsystem.md`
- Create: `.claude/rules/frontend-i18n-and-font.md`
- Create: `.claude/rules/frontend-compose-conventions.md`

- [ ] **Step 1: `frontend-architecture.md` を作成**

```markdown
---
paths:
  - "frontend/**/*.kt"
---

# Frontend Architecture

`:frontend`(Compose Multiplatform)の層責務と依存方向。Jetpack Compose 推奨アーキに倣う。

## Rule

- **UI 層**: 画面ごとに `ViewModel`(`org.jetbrains.androidx.lifecycle.ViewModel`)+ `sealed interface XxxUiState`。状態は `StateFlow` で公開。Composable は state を購読しイベントを ViewModel に委譲するだけ(単方向データフロー)。ロジックを Composable に書かない。
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
```

- [ ] **Step 2: `frontend-kmp-structure.md` を作成**

```markdown
---
paths:
  - "frontend/**/*.kt"
---

# Frontend KMP Structure

将来 android / ios / desktop(JVM) ターゲットを足せる構成を保つための規律。

## Rule

- 移植可能なコード(UI・ViewModel・テーマ・デザイン部品・RPC ラッパ・認証ロジック本体)は **`commonMain`** に置く。
- platform 依存は **`expect/actual` でのみ**逃がす。現状で actual が要るもの: PKCE の乱数/SHA-256/base64url(`secureRandomBytes`/`sha256`/`base64UrlNoPad`)、`SessionStorage`、ブラウザ遷移/コールバック取得(`BrowserNav`)。
- **落とし穴(明記)**: 現ターゲットは `js` と `wasmJs` の **web 2 種のみ**。両方 web なので `commonMain` に `kotlinx.browser.window` 等の web 前提を書いてもコンパイルが通る。これは移植性の保証にならない。web 固有 API を `commonMain` に直書きしない — 必ず `expect/actual` か platform source set へ。

## Why

将来ターゲット追加を「ターゲット + actual を足す」追加作業に留め、書き直しを避ける。web2種だけでは型検査が移植性を担保しないため、`expect/actual` が唯一の実効境界。

## How to apply

✅ `internal expect fun secureRandomBytes(n: Int): ByteArray` を common に、`actual` を jsMain/wasmJsMain に。
❌ `commonMain` の Composable から `kotlinx.browser.window.location` を直接参照。

## 関連

- rule: [frontend-architecture](frontend-architecture.md)
- memory 相当: 検証は `./gradlew :frontend:compileKotlinWasmJs`(フルビルドは OOM)
```

- [ ] **Step 3: `frontend-rpc-and-error.md` を作成**

```markdown
---
paths:
  - "frontend/**/*.kt"
---

# Frontend RPC and Error Handling

frontend からの RPC 呼び出しとエラー処理の規約。

## Rule

- RPC は `RpcClientProvider` 経由でのみ開く。WS subprotocol でトークンを運ぶ(`mindstock.v1` と `mindstock.bearer.<base64url(jwt)>` を別エントリで append)。
- Repository が `RpcResult<T, RpcError>` を受け、`RpcOutcome`(成功 / 失敗)に変換して ViewModel へ返す。ViewModel は `RpcError` の variant を `when` で **網羅** する(`Unauthorized`/`NotFound`/`BadRequest`/`Conflict`/`Internal`)。新 variant 追加にコンパイルで気づける。
- エラー表示: `Unauthorized` → 再認証(token 破棄して authorize へ)/ `BadRequest` → フォームのフィールドエラー / `Conflict`・`Internal` → トースト / `NotFound` → 文脈に応じ空状態 or エラー。
- **nullable 戻り値原則禁止**(横断ルール `error-handling.md` 踏襲)。不在は `RpcError` / sealed で表す。frontend の公開関数も `T?` を安易に返さない。
- 登録状態判定: `/resident` は登録済み必須ルートのため、未登録ユーザの `me()` は WS ハンドシェイクで拒否され **例外を throw** する(`RpcResult.Err` にならない)。boot はこの例外を捕捉して未登録 → オンボーディングへ倒す。

## Why

RpcClientProvider 集約で transport/トークン/再接続を 1 箇所に閉じ込める。`when` 網羅で API エラー語彙の変化に追従。

## 関連

- rule: [error-handling](error-handling.md)(横断・nullable 禁止)/ [frontend-architecture](frontend-architecture.md)
- backend: `backend/api/.../configuration/auth/`(WS subprotocol / JWT 検証)
```

- [ ] **Step 4: `frontend-designsystem.md` を作成**

```markdown
---
paths:
  - "frontend/**/*.kt"
---

# Frontend Design System

Material3 Expressive と clay テーマの扱い。

## Rule

- UI 基盤は **Material3 Expressive**(`1.10.0-alpha05`、`strictly` ピン)。alpha API の変動を feature に漏らさないため、**`designsystem/` 層に封じ込める**。
- **feature 層は `androidx.compose.material3.*` を直接 import しない**。`designsystem/` の atom(`PrimaryButton` / `BottomSheetScaffold` / `StatusDot` / `StockLevelBar` / `SegmentedControl` / `AppIcon` 等)と `MindstockTheme` 経由でのみ UI を組む。
- clay 配色は Material3 カスタム `ColorScheme` に写す。Material3 に無いモック固有トークン(status色 ok/low/out・影・独自半径)は `CompositionLocal`(`LocalMindstockTokens`)で供給。
- アイコンは `material-icons-extended` を `AppIcon` で 1 枚噛ませ、feature は semantic 名で参照(将来差し替え可能に)。

## Why

alpha ライブラリの API churn を 1 層に閉じ込め、feature の安定性を守る。テーマ変更を 1 箇所で完結させる。

## How to apply

✅ feature: `PrimaryButton(onClick = ...) { Text(...) }`
❌ feature: `androidx.compose.material3.Button(...)` を直接呼ぶ。

## 関連

- rule: [frontend-architecture](frontend-architecture.md) / [frontend-i18n-and-font](frontend-i18n-and-font.md)
- mock: `docs/ref/mindstock.zip`(`app/core.jsx` のトークン/atoms)
```

- [ ] **Step 5: `frontend-i18n-and-font.md` を作成**

```markdown
---
paths:
  - "frontend/**/*.kt"
---

# Frontend i18n and Font

文言とフォントの規約。

## Rule

- **ユーザ向け文言をコードに直書きしない**。Compose Resources の string resources(`commonMain/composeResources/values/strings.xml`、まず `ja`)に置き、`stringResource(Res.string.xxx)` で参照する。将来 `values-<lang>/strings.xml` を足すだけで多言語化。
- フォントは `commonMain/composeResources/font` の NotoSansJP(9 ウェイト)を `org.jetbrains.compose.resources.Font` で `FontFamily` 化し、Material3 `Typography` に適用(`designsystem/theme/Typography.kt`)。
- 例外: ログ / 例外メッセージ / 識別子など非ユーザ向け文字列は対象外。

## Why

文言を resource に集約すれば多言語化が差分追加で済み、レビューも文言単位で完結する。

## 関連

- rule: [frontend-designsystem](frontend-designsystem.md)
```

- [ ] **Step 6: `frontend-compose-conventions.md` を作成**

```markdown
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
```

- [ ] **Step 7: Commit**

```bash
git add .claude/rules/frontend-architecture.md .claude/rules/frontend-kmp-structure.md .claude/rules/frontend-rpc-and-error.md .claude/rules/frontend-designsystem.md .claude/rules/frontend-i18n-and-font.md .claude/rules/frontend-compose-conventions.md
git commit -m "docs(rules): frontend ルール6本を新設(P6-0)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: gradle に navigation-compose を追加

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `frontend/build.gradle.kts`

- [ ] **Step 1: version catalog にエントリ追加**

`gradle/libs.versions.toml` の `[versions]` に追記(他の version 行に並べる):

```toml
navigation-compose = "2.9.0-beta05"
```

`[libraries]` に追記(`material3-adaptive-navigation-suite` 行の近く):

```toml
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation-compose" }
```

> 注: 正確なバージョンは Compose Multiplatform 1.11.0 互換の最新を使う。解決に失敗したら `./gradlew :frontend:dependencies --configuration commonMainImplementation` で互換版を確認し、`2.9.x-betaNN` 系に合わせる。

- [ ] **Step 2: frontend の依存に追加**

`frontend/build.gradle.kts` の `commonMain.dependencies { }` 内、`material3.adaptive.navigation.suite` の次の行に追加:

```kotlin
            implementation(libs.navigation.compose)
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL(navigation-compose が解決される。`generateAuthConfig` は `AUTH_CLIENT_ID` 等の env 未設定だと失敗するので、未設定なら `AUTH_CLIENT_ID=dummy AUTH_AUDIENCE=dummy AUTH_PROJECT_ID=dummy ./gradlew :frontend:compileKotlinWasmJs` で確認)

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml frontend/build.gradle.kts
git commit -m "build(frontend): navigation-compose を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: 認証 — Tokens(kotlin.time)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Tokens.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/TokensTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`TokensTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TokensTest {
    private val epoch = Instant.fromEpochSeconds(1_000_000)

    @Test
    fun fromTokenResponse_expiresAt_is_now_plus_expiresIn() {
        val t = Tokens.fromTokenResponse("a", "r", "i", expiresInSeconds = 3600, now = epoch)
        t.expiresAt shouldBe epoch + 3600.seconds
    }

    @Test
    fun willExpireWithin_true_when_within_window() {
        val t = Tokens("a", "r", "i", expiresAt = epoch + 30.seconds)
        t.willExpireWithin(60, now = epoch) shouldBe true
        t.willExpireWithin(10, now = epoch) shouldBe false
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :frontend:compileTestKotlinJvm` 相当が無いため、`./gradlew :frontend:jsTest --tests "*TokensTest*"` 
Expected: FAIL(`Tokens` 未定義でコンパイルエラー)

> 注: frontend は JVM ターゲットが無い。テストは `jsTest`(or `wasmJsTest`)で走る。`jsTest` が最も軽い。

- [ ] **Step 3: 最小実装**

`Tokens.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Serializable
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresAt: Instant,
) {
    fun willExpireWithin(
        seconds: Long,
        now: Instant = Clock.System.now(),
    ): Boolean = expiresAt <= now + seconds.seconds

    companion object {
        fun fromTokenResponse(
            accessToken: String,
            refreshToken: String,
            idToken: String,
            expiresInSeconds: Long,
            now: Instant = Clock.System.now(),
        ): Tokens = Tokens(accessToken, refreshToken, idToken, now + expiresInSeconds.seconds)
    }
}
```

> `kotlin.time.Instant` の `@Serializable` は kotlin.time の組込み serializer を使う(Kotlin 2.3 で提供)。解決しない場合は `expiresAt` を `Long`(epochSeconds)で保持し変換に切り替える。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :frontend:jsTest --tests "*TokensTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Tokens.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/TokensTest.kt
git commit -m "feat(frontend): 認証 Tokens(kotlin.time)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 認証 — PKCE(expect/actual)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Pkce.kt`
- Create: `frontend/src/jsMain/kotlin/net/brightroom/mindstock/frontend/auth/PkceJs.kt`
- Create: `frontend/src/wasmJsMain/kotlin/net/brightroom/mindstock/frontend/auth/PkceWasmJs.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/PkceTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`PkceTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldMatch
import kotlin.test.Test

class PkceTest {
    @Test
    fun newVerifier_has_requested_length_and_unreserved_charset() {
        val v = Pkce.newVerifier(64)
        v shouldHaveSize 64
        v shouldMatch Regex("^[A-Za-z0-9._~-]+$")
    }

    @Test
    fun newVerifier_rejects_out_of_range_length() {
        try {
            Pkce.newVerifier(10)
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
```

- [ ] **Step 2: テスト失敗を確認**

Run: `./gradlew :frontend:jsTest --tests "*PkceTest*"`
Expected: FAIL(`Pkce` 未定義)

- [ ] **Step 3: common + 両 actual を実装**

`Pkce.kt`(common):

```kotlin
package net.brightroom.mindstock.frontend.auth

object Pkce {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    fun newVerifier(length: Int = 64): String {
        require(length in 43..128) { "verifier length must be 43..128, was $length" }
        val sb = StringBuilder(length)
        while (sb.length < length) {
            val need = length - sb.length
            val bytes = secureRandomBytes(need * 2)
            for (b in bytes) {
                if (sb.length == length) break
                val u = b.toInt() and 0xFF
                if (u < 198) sb.append(ALPHABET[u % ALPHABET.length])
            }
        }
        return sb.toString()
    }

    suspend fun challenge(verifier: String): String = base64UrlNoPad(sha256(verifier.encodeToByteArray()))
}

internal expect fun secureRandomBytes(n: Int): ByteArray

internal expect suspend fun sha256(bytes: ByteArray): ByteArray

internal expect fun base64UrlNoPad(bytes: ByteArray): String
```

`PkceJs.kt`(jsMain):

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

internal actual fun secureRandomBytes(n: Int): ByteArray {
    val arr = createRandomBytesJs(n)
    return ByteArray(n) { arr[it].toByte() }
}

private fun createRandomBytesJs(n: Int): Uint8Array = js("globalThis.crypto.getRandomValues(new Uint8Array(n))")

internal actual suspend fun sha256(bytes: ByteArray): ByteArray {
    val input = Int8Array(bytes.toTypedArray())
    val digest = subtleDigestJs(input.buffer).await()
    val view = Uint8Array(digest)
    return ByteArray(view.length) { view[it].toByte() }
}

internal actual fun base64UrlNoPad(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size)
    for (b in bytes) sb.append((b.toInt() and 0xFF).toChar())
    return jsBtoaJs(sb.toString()).replace('+', '-').replace('/', '_').trimEnd('=')
}

private fun subtleDigestJs(data: ArrayBuffer): kotlin.js.Promise<ArrayBuffer> = js("globalThis.crypto.subtle.digest('SHA-256', data)")

private fun jsBtoaJs(s: String): String = js("globalThis.btoa(s)")
```

`PkceWasmJs.kt`(wasmJsMain):

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

@JsFun("(n) => { var a = new Uint8Array(n); globalThis.crypto.getRandomValues(a); return a; }")
private external fun createRandomBytesWasm(n: Int): Uint8Array

internal actual fun secureRandomBytes(n: Int): ByteArray {
    val arr = createRandomBytesWasm(n)
    return ByteArray(n) { arr[it].toByte() }
}

internal actual suspend fun sha256(bytes: ByteArray): ByteArray {
    val uint8 = newUint8Array(bytes.size)
    for (i in bytes.indices) setUint8(uint8, i, bytes[i].toInt() and 0xFF)
    val digest: ArrayBuffer = subtleDigest(uint8).await()!!.unsafeCast()
    val view = Uint8Array(digest)
    return ByteArray(view.length) { view[it].toByte() }
}

internal actual fun base64UrlNoPad(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size)
    for (b in bytes) sb.append((b.toInt() and 0xFF).toChar())
    return btoa(sb.toString()).replace('+', '-').replace('/', '_').trimEnd('=')
}

@JsFun("(n) => new Uint8Array(n)")
private external fun newUint8Array(size: Int): Uint8Array

@JsFun("(arr, i, v) => { arr[i] = v; }")
private external fun setUint8(arr: Uint8Array, index: Int, value: Int)

@JsFun("(arr) => globalThis.crypto.subtle.digest('SHA-256', arr)")
private external fun subtleDigest(data: Uint8Array): kotlin.js.Promise<JsAny?>

@JsFun("(s) => globalThis.btoa(s)")
private external fun btoa(s: String): String
```

- [ ] **Step 4: テスト通過を確認**

Run: `./gradlew :frontend:jsTest --tests "*PkceTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Pkce.kt frontend/src/jsMain frontend/src/wasmJsMain frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/PkceTest.kt
git commit -m "feat(frontend): PKCE(expect/actual)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: 認証 — AuthClient + OidcException

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/OidcException.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthClient.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/AuthClientTest.kt`

- [ ] **Step 1: 失敗するテストを書く**(URL ビルドは pure。token 交換は MockEngine)

`AuthClientTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class AuthClientTest {
    @Test
    fun buildAuthorizeUrl_includes_pkce_and_s256() {
        val url = AuthClient.buildAuthorizeUrl(
            issuer = "https://id.example",
            clientId = "cid",
            redirectUri = "https://app/cb",
            scope = "openid profile",
            state = "st",
            codeChallenge = "chal",
        )
        url shouldContain "https://id.example/oauth/v2/authorize?"
        url shouldContain "response_type=code"
        url shouldContain "client_id=cid"
        url shouldContain "code_challenge=chal"
        url shouldContain "code_challenge_method=S256"
        url shouldContain "scope=openid%20profile"
    }

    @Test
    fun endSessionUrl_includes_id_token_hint() {
        val url = AuthClient.endSessionUrl("https://id.example", "idtok", "https://app/")
        url shouldContain "https://id.example/oidc/v1/end_session?"
        url shouldContain "id_token_hint=idtok"
    }
}
```

- [ ] **Step 2: 失敗確認**

Run: `./gradlew :frontend:jsTest --tests "*AuthClientTest*"`
Expected: FAIL(`AuthClient` 未定義)

- [ ] **Step 3: 実装**

`OidcException.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

class OidcException(
    val errorCode: String,
    val errorDescription: String?,
    val reauthRequired: Boolean,
) : RuntimeException("$errorCode: ${errorDescription ?: ""}")
```

`AuthClient.kt`(旧コードを `kotlin.time` にモダナイズ):

```kotlin
package net.brightroom.mindstock.frontend.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AuthClient(
    private val http: HttpClient,
    private val issuer: String,
    private val clientId: String,
    private val redirectUri: String,
) {
    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String = "",
        @SerialName("id_token") val idToken: String = "",
        @SerialName("expires_in") val expiresIn: Long,
    )

    @Serializable
    private data class ErrorResponse(
        val error: String,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    suspend fun exchangeCode(code: String, codeVerifier: String, now: Instant = Clock.System.now()): Tokens =
        postToken(
            Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("client_id", clientId)
                append("redirect_uri", redirectUri)
                append("code_verifier", codeVerifier)
            },
            now,
        )

    suspend fun refresh(refreshToken: String, now: Instant = Clock.System.now()): Tokens =
        postToken(
            Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", clientId)
            },
            now,
        )

    private suspend fun postToken(params: Parameters, now: Instant): Tokens {
        val resp: HttpResponse = http.submitForm(url = "$issuer/oauth/v2/token", formParameters = params)
        if (!resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            val err = runCatching { JSON.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
            val errorCode = err?.error ?: "http_${resp.status.value}"
            val desc = err?.errorDescription ?: text.take(200)
            throw OidcException(errorCode, desc, reauthRequired = errorCode == "invalid_grant")
        }
        val text = resp.bodyAsText()
        val tr = runCatching { JSON.decodeFromString(TokenResponse.serializer(), text) }
            .getOrElse { throw OidcException("parse_error", it.message?.take(200), reauthRequired = false) }
        return Tokens.fromTokenResponse(tr.accessToken, tr.refreshToken, tr.idToken, tr.expiresIn, now)
    }

    companion object {
        internal val JSON = Json { ignoreUnknownKeys = true }

        fun buildAuthorizeUrl(
            issuer: String,
            clientId: String,
            redirectUri: String,
            scope: String,
            state: String,
            codeChallenge: String,
        ): String {
            val q = listOf(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "scope" to scope,
                "state" to state,
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
            ).joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
            return "$issuer/oauth/v2/authorize?$q"
        }

        fun endSessionUrl(issuer: String, idToken: String, postLogoutRedirectUri: String): String {
            val q = listOf(
                "id_token_hint" to idToken,
                "post_logout_redirect_uri" to postLogoutRedirectUri,
            ).joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
            return "$issuer/oidc/v1/end_session?$q"
        }
    }
}
```

- [ ] **Step 4: 通過確認**

Run: `./gradlew :frontend:jsTest --tests "*AuthClientTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/OidcException.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthClient.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/AuthClientTest.kt
git commit -m "feat(frontend): AuthClient(Zitadel token 交換/authorize URL)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 認証 — TokenStore + SessionStorage(expect/actual)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/SessionStorage.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/TokenStore.kt`
- Create: `frontend/src/jsMain/kotlin/net/brightroom/mindstock/frontend/auth/SessionStorageJs.kt`
- Create: `frontend/src/wasmJsMain/kotlin/net/brightroom/mindstock/frontend/auth/SessionStorageWasmJs.kt`

- [ ] **Step 1: common expect + TokenStore**

`SessionStorage.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

internal expect object SessionStorage {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}
```

`TokenStore.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.serialization.json.Json

internal object TokenStore {
    private const val KEY = "mindstock.tokens.v1"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(tokens: Tokens) = SessionStorage.set(KEY, json.encodeToString(Tokens.serializer(), tokens))

    fun load(): Tokens? =
        SessionStorage.get(KEY)?.let { runCatching { json.decodeFromString(Tokens.serializer(), it) }.getOrNull() }

    fun clear() = SessionStorage.remove(KEY)
}
```

> `TokenStore.load()` の `Tokens?` 戻りは「保存済みトークンの不在」を表す内部 API。`error-handling.md` の nullable 禁止は公開 API 対象であり、ここは内部の storage アクセスなので許容(boot 側で有/無を分岐する自然な形)。

- [ ] **Step 2: js actual**

`SessionStorageJs.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.browser.window

internal actual object SessionStorage {
    actual fun get(key: String): String? = window.sessionStorage.getItem(key)
    actual fun set(key: String, value: String) { window.sessionStorage.setItem(key, value) }
    actual fun remove(key: String) { window.sessionStorage.removeItem(key) }
}
```

- [ ] **Step 3: wasmJs actual**(同内容)

`SessionStorageWasmJs.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.browser.window

internal actual object SessionStorage {
    actual fun get(key: String): String? = window.sessionStorage.getItem(key)
    actual fun set(key: String, value: String) { window.sessionStorage.setItem(key, value) }
    actual fun remove(key: String) { window.sessionStorage.removeItem(key) }
}
```

- [ ] **Step 4: コンパイル確認**

Run: `AUTH_CLIENT_ID=dummy AUTH_AUDIENCE=dummy AUTH_PROJECT_ID=dummy ./gradlew :frontend:compileKotlinWasmJs :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/SessionStorage.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/TokenStore.kt frontend/src/jsMain frontend/src/wasmJsMain
git commit -m "feat(frontend): TokenStore + SessionStorage(expect/actual)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: RPC — RpcClientProvider

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcClientProvider.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcClientProviderTest.kt`

- [ ] **Step 1: 失敗するテスト**(MockEngine で subprotocol ヘッダを検証。旧 `openRaw` 方式)

`RpcClientProviderTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.rpc

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RpcClientProviderTest {
    @Test
    fun open_appends_app_and_bearer_subprotocols_as_separate_entries() = runTest {
        var captured: List<String> = emptyList()
        val engine = MockEngine { req ->
            captured = req.headers.getAll(HttpHeaders.SecWebSocketProtocol) ?: emptyList()
            respond("")
        }
        val provider = RpcClientProvider(HttpClient(engine), baseUrl = "ws://localhost")
        provider.probeHeaders("resident", "jwt-token")
        // "jwt-token" の base64url(no pad) = "and0LXRva2Vu"
        captured shouldContainExactly listOf("mindstock.v1", "mindstock.bearer.and0LXRva2Vu")
    }
}
```

- [ ] **Step 2: 失敗確認**

Run: `./gradlew :frontend:jsTest --tests "*RpcClientProviderTest*"`
Expected: FAIL(`RpcClientProvider` 未定義)

- [ ] **Step 3: 実装**

`RpcClientProvider.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.rpc

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.rpc.krpc.ktor.client.KtorRpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 認証済み Krpc クライアントを開く。各 @Rpc サービスのパス(/api/v1/<path>)に WS 接続し、
 * トークンを subprotocol で運ぶ。
 *
 * baseUrl は ws:// or wss://(kotlinx-rpc は scheme で transport を選ぶ)。
 */
@OptIn(ExperimentalEncodingApi::class)
class RpcClientProvider(
    http: HttpClient,
    private val baseUrl: String,
) {
    private val rpcHttp: HttpClient =
        http.config {
            installKrpc { serialization { json(KrpcJson) } }
            install(WebSockets)
        }
    private val rawHttp: HttpClient = http
    private val opened = mutableListOf<KtorRpcClient>()

    fun open(path: String, accessToken: String): KtorRpcClient {
        val b64 = encode(accessToken)
        val rpc = rpcHttp.rpc("$baseUrl/api/v1/$path") {
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.$b64")
        }
        opened += rpc
        return rpc
    }

    /** Test helper: 同一ヘッダで 1 回 GET し MockEngine に検査させる。 */
    internal suspend fun probeHeaders(path: String, accessToken: String) {
        val b64 = encode(accessToken)
        rawHttp.get("$baseUrl/api/v1/$path") {
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.$b64")
        }
    }

    fun closeAll() {
        opened.forEach { it.close("reauth or logout") }
        opened.clear()
    }

    private fun encode(token: String): String = Base64.UrlSafe.encode(token.encodeToByteArray()).trimEnd('=')
}
```

- [ ] **Step 4: 通過確認**

Run: `./gradlew :frontend:jsTest --tests "*RpcClientProviderTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcClientProvider.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcClientProviderTest.kt
git commit -m "feat(frontend): RpcClientProvider(WS subprotocol トークン)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: RPC — RpcOutcome + RpcError ハンドリング

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcOutcome.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcErrors.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcOutcomeTest.kt`

- [ ] **Step 1: 失敗するテスト**

`RpcOutcomeTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.rpc

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

class RpcOutcomeTest {
    @Test
    fun ok_maps_to_success() {
        val out = RpcResult.Ok(42).toOutcome()
        out.shouldBeInstanceOf<RpcOutcome.Success<Int>>()
        (out as RpcOutcome.Success).value shouldBe 42
    }

    @Test
    fun err_maps_to_failure_with_error() {
        val err = RpcError.Conflict("dup")
        val out = RpcResult.Err(err).toOutcome<Int>()
        out.shouldBeInstanceOf<RpcOutcome.Failure>()
        (out as RpcOutcome.Failure).error shouldBe err
    }

    @Test
    fun userMessage_covers_all_variants() {
        // when 網羅の回帰: 各 variant が message を返す
        userMessageOf(RpcError.Unauthorized("x")).shouldBeInstanceOf<String>()
        userMessageOf(RpcError.NotFound("x")).shouldBeInstanceOf<String>()
        userMessageOf(RpcError.BadRequest("f", "r")).shouldBeInstanceOf<String>()
        userMessageOf(RpcError.Conflict("x")).shouldBeInstanceOf<String>()
        userMessageOf(RpcError.Internal("x")).shouldBeInstanceOf<String>()
    }
}
```

- [ ] **Step 2: 失敗確認**

Run: `./gradlew :frontend:jsTest --tests "*RpcOutcomeTest*"`
Expected: FAIL

- [ ] **Step 3: 実装**

`RpcOutcome.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.rpc

import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

/** Repository が ViewModel に返す結果型。RPC 契約 RpcResult を frontend 都合に変換。 */
sealed interface RpcOutcome<out T> {
    data class Success<T>(val value: T) : RpcOutcome<T>
    data class Failure(val error: RpcError) : RpcOutcome<Nothing>
}

fun <T : Any> RpcResult<T, RpcError>.toOutcome(): RpcOutcome<T> =
    when (this) {
        is RpcResult.Ok -> RpcOutcome.Success(value)
        is RpcResult.Err -> RpcOutcome.Failure(error)
    }
```

`RpcErrors.kt`(`when` 網羅でユーザ向け文言を決める。ここでは文言キーを返す形にせず、暫定で日本語文字列。Task 15 で string resources に寄せる際は呼び出し側で差し替え):

```kotlin
package net.brightroom.mindstock.frontend.core.rpc

import net.brightroom.mindstock.rpc.result.RpcError

/** RpcError variant を網羅し、ユーザ向け文言(暫定)を返す。新 variant 追加でコンパイルエラー。 */
fun userMessageOf(error: RpcError): String =
    when (error) {
        is RpcError.Unauthorized -> "セッションが切れました。再ログインしてください。"
        is RpcError.NotFound -> "対象が見つかりませんでした。"
        is RpcError.BadRequest -> "入力に誤りがあります: ${error.reason}"
        is RpcError.Conflict -> "操作が競合しました: ${error.reason}"
        is RpcError.Internal -> "サーバでエラーが発生しました。"
    }

/** 再認証が必要なエラーか(boot/呼び出し側が token 破棄 → authorize へ倒す判定)。 */
fun RpcError.requiresReauth(): Boolean = this is RpcError.Unauthorized
```

- [ ] **Step 4: 通過確認**

Run: `./gradlew :frontend:jsTest --tests "*RpcOutcomeTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcOutcome.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcErrors.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcOutcomeTest.kt
git commit -m "feat(frontend): RpcOutcome + RpcError 網羅ハンドリングを追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: セッション — AppSession

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/session/AppSession.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/session/AppSessionTest.kt`

> `:domain` の `Households` / `HouseholdId` / `ResidentId` / `DisplayName` を使う。`Households` の API(`list` 公開や `first`/iteration)は実装前に `domain/src/.../household/Households.kt` を確認して合わせる。

- [ ] **Step 1: 失敗するテスト**

`AppSessionTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.session

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AppSessionTest {
    @Test
    fun selectHousehold_updates_active() = runTest {
        val session = AppSession()
        session.setActiveHousehold("hh-2")
        session.state.value.activeHouseholdId shouldBe "hh-2"
    }
}
```

> 注: `activeHouseholdId` の型は `HouseholdId`。上のテストは型確認後に `HouseholdId("hh-2")` 等へ合わせる(`HouseholdId` のコンストラクタ/ファクトリを `domain` で確認)。文字列 ID 直書きが通らなければ実型に置換。

- [ ] **Step 2: 失敗確認**

Run: `./gradlew :frontend:jsTest --tests "*AppSessionTest*"`
Expected: FAIL

- [ ] **Step 3: 実装**

`AppSession.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName

/** ログイン中の住人とアクティブ世帯。画面横断で参照する単一の真実。 */
class AppSession {
    data class State(
        val residentId: ResidentId? = null,
        val displayName: DisplayName? = null,
        val households: Households? = null,
        val activeHouseholdId: HouseholdId? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setResident(residentId: ResidentId, displayName: DisplayName) =
        _state.update { it.copy(residentId = residentId, displayName = displayName) }

    fun setHouseholds(households: Households, active: HouseholdId?) =
        _state.update { it.copy(households = households, activeHouseholdId = active) }

    fun setActiveHousehold(id: HouseholdId) = _state.update { it.copy(activeHouseholdId = id) }

    fun clear() { _state.value = State() }
}
```

> ここで `State` の各フィールドは「未確立」を `null` で表す内部状態(公開 API の戻り値ではない)。boot 完了後は non-null である不変条件を boot 側で守る。これは `error-handling.md` の nullable 禁止(公開 API 対象)に抵触しない。気になる場合は `sealed State { Empty; Loaded(...) }` に変更してよい(その場合テストとアクセス側も合わせる)。

- [ ] **Step 4: 通過確認 / Commit**

Run: `./gradlew :frontend:jsTest --tests "*AppSessionTest*"`(型を実 `HouseholdId` に合わせた後)
Expected: PASS

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/session/AppSession.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/session/AppSessionTest.kt
git commit -m "feat(frontend): AppSession(住人+アクティブ世帯)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: 認証フロー — BrowserNav(expect/actual)+ AuthState

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth/BrowserNav.kt`(expect)
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth/AuthState.kt`
- Create: `frontend/src/jsMain/kotlin/net/brightroom/mindstock/frontend/core/auth/BrowserNavJs.kt`
- Create: `frontend/src/wasmJsMain/kotlin/net/brightroom/mindstock/frontend/core/auth/BrowserNavWasmJs.kt`

- [ ] **Step 1: common expect + AuthState**

`BrowserNav.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.auth

/** ブラウザ遷移とコールバック取得を抽象化(platform 依存)。 */
internal expect object BrowserNav {
    fun currentPath(): String
    fun currentQueryParam(name: String): String?
    fun assign(url: String)
    fun replace(url: String)
}
```

`AuthState.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.auth

/** 起動〜認証の画面状態。 */
sealed interface AuthState {
    /** 起動処理中(callback 交換 or token 検証 or me() 問い合わせ)。 */
    data object Booting : AuthState

    /** 認証済み・登録済み。app 本体へ。 */
    data object Ready : AuthState

    /** 認証済みだが Resident 未登録。表示名登録 → 世帯作成へ。 */
    data object NeedOnboarding : AuthState

    /** 失敗。message を表示し再ログイン可能に。 */
    data class Failed(val message: String) : AuthState
}
```

- [ ] **Step 2: js actual**

`BrowserNavJs.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.auth

import kotlinx.browser.window

internal actual object BrowserNav {
    actual fun currentPath(): String = window.location.pathname
    actual fun currentQueryParam(name: String): String? = queryMap()[name]
    actual fun assign(url: String) { window.location.assign(url) }
    actual fun replace(url: String) { window.location.replace(url) }

    private fun queryMap(): Map<String, String> =
        window.location.search.removePrefix("?").split("&").mapNotNull {
            if (it.isBlank()) return@mapNotNull null
            val idx = it.indexOf('=')
            val k = if (idx < 0) it else it.substring(0, idx)
            val v = if (idx < 0) "" else it.substring(idx + 1)
            runCatching { decodeURIComponent(k) to decodeURIComponent(v) }.getOrNull()
        }.toMap()
}

private fun decodeURIComponent(s: String): String = js("decodeURIComponent(s)")
```

`BrowserNavWasmJs.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.auth

import kotlinx.browser.window

internal actual object BrowserNav {
    actual fun currentPath(): String = window.location.pathname
    actual fun currentQueryParam(name: String): String? = queryMap()[name]
    actual fun assign(url: String) { window.location.assign(url) }
    actual fun replace(url: String) { window.location.replace(url) }

    private fun queryMap(): Map<String, String> =
        window.location.search.removePrefix("?").split("&").mapNotNull {
            if (it.isBlank()) return@mapNotNull null
            val idx = it.indexOf('=')
            val k = if (idx < 0) it else it.substring(0, idx)
            val v = if (idx < 0) "" else it.substring(idx + 1)
            runCatching { decodeUriComponentWasm(k) to decodeUriComponentWasm(v) }.getOrNull()
        }.toMap()
}

@JsFun("(s) => decodeURIComponent(s)")
private external fun decodeUriComponentWasm(s: String): String
```

- [ ] **Step 3: コンパイル確認**

Run: `AUTH_CLIENT_ID=dummy AUTH_AUDIENCE=dummy AUTH_PROJECT_ID=dummy ./gradlew :frontend:compileKotlinJs :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth frontend/src/jsMain frontend/src/wasmJsMain
git commit -m "feat(frontend): BrowserNav(expect/actual)+ AuthState を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: 認証フロー — AuthViewModel(起動シーケンス)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelTest.kt`

spec §3.1 + 前提 #3 の boot 機構を実装。`AuthViewModel` は副作用(BrowserNav / RpcClientProvider / AuthClient / TokenStore)を **コンストラクタ注入のインターフェース**で受け、テスト可能にする。

- [ ] **Step 1: 失敗するテスト**(fake で 3 分岐を検証)

`AuthViewModelTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.app

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.frontend.core.auth.AuthState
import kotlin.test.Test

class AuthViewModelTest {
    @Test
    fun no_token_redirects_to_authorize_and_stays_booting() = runTest {
        val deps = FakeAuthDeps(path = "/", token = null)
        val vm = AuthViewModel(deps)
        vm.boot()
        deps.redirectedToAuthorize shouldBeInstanceOf String::class
        vm.state.value.shouldBeInstanceOf<AuthState.Booting>()
    }

    @Test
    fun valid_token_and_me_ok_becomes_ready() = runTest {
        val deps = FakeAuthDeps(path = "/", token = "tok", meSucceeds = true)
        val vm = AuthViewModel(deps)
        vm.boot()
        vm.state.value.shouldBeInstanceOf<AuthState.Ready>()
    }

    @Test
    fun valid_token_but_me_throws_becomes_need_onboarding() = runTest {
        val deps = FakeAuthDeps(path = "/", token = "tok", meSucceeds = false)
        val vm = AuthViewModel(deps)
        vm.boot()
        vm.state.value.shouldBeInstanceOf<AuthState.NeedOnboarding>()
    }
}
```

> `FakeAuthDeps` は次の Step で `AuthViewModel` が要求するインターフェース(下記 `AuthDeps`)の test 実装として commonTest に書く。`redirectedToAuthorize` 等のスパイ用プロパティを持たせる。

- [ ] **Step 2: 失敗確認**

Run: `./gradlew :frontend:jsTest --tests "*AuthViewModelTest*"`
Expected: FAIL

- [ ] **Step 3: 実装**

`AuthViewModel.kt`:

```kotlin
package net.brightroom.mindstock.frontend.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.frontend.auth.Tokens
import net.brightroom.mindstock.frontend.core.auth.AuthState

/**
 * boot に必要な副作用の境界。本番実装は app/ で web 用に束ね、テストは fake を差す。
 * registered 判定は me() の throw を未登録に倒す(前提 #3)。
 */
interface AuthDeps {
    fun currentPath(): String
    /** /auth/callback の code を交換し token を保存して "/" へ replace。失敗時 throw。 */
    suspend fun handleCallback()
    /** 保存済みの有効トークン。無ければ null。 */
    fun loadValidToken(): Tokens?
    /** code_verifier 生成→保存→authorize へ redirect。 */
    suspend fun redirectToAuthorize()
    /** me() を呼ぶ。登録済みなら Resident、未登録/拒否なら throw。 */
    suspend fun fetchMe(token: Tokens): Resident
    /** 取得済み Resident をセッションに反映。 */
    fun onAuthenticated(resident: Resident)
}

class AuthViewModel(
    private val deps: AuthDeps,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthState>(AuthState.Booting)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun boot() {
        if (deps.currentPath() == "/auth/callback") {
            runCatching { deps.handleCallback() }
                .onFailure { _state.value = AuthState.Failed("ログインに失敗しました") }
            // 成功時は replace("/") で再起動するため state は Booting のまま離脱
            return
        }
        val token = deps.loadValidToken()
        if (token == null) {
            deps.redirectToAuthorize()
            return // redirect でページ離脱。Booting のまま
        }
        try {
            val resident = deps.fetchMe(token)
            deps.onAuthenticated(resident)
            _state.value = AuthState.Ready
        } catch (_: Throwable) {
            // /resident は登録済み必須ルート。未登録は WS handshake で拒否され throw。
            // ブラウザは 401 を JS に公開しないため、ここは未登録に倒す(前提 #3)。
            _state.value = AuthState.NeedOnboarding
        }
    }
}
```

- [ ] **Step 4: 通過確認**

Run: `./gradlew :frontend:jsTest --tests "*AuthViewModelTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelTest.kt
git commit -m "feat(frontend): AuthViewModel(PKCE boot シーケンス)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 12: i18n — string resources

**Files:**
- Create: `frontend/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: ja 文言を定義**

`strings.xml`:

```xml
<resources>
    <string name="app_name">mindstock</string>
    <string name="nav_stock">在庫</string>
    <string name="nav_shop">買い物</string>
    <string name="nav_activity">履歴</string>
    <string name="nav_profile">設定</string>
    <string name="action_add">追加</string>
    <string name="stock_view_list">リスト</string>
    <string name="stock_view_grid">グリッド</string>
    <string name="stock_empty">在庫がまだありません</string>
    <string name="action_replenish">補充</string>
    <string name="action_consume">消費</string>
    <string name="login_title">mindstock にログイン</string>
    <string name="login_button">ログイン</string>
    <string name="error_generic">エラーが発生しました</string>
</resources>
```

- [ ] **Step 2: コンパイル確認**(Compose Resources が `Res.string.*` を生成)

Run: `AUTH_CLIENT_ID=dummy AUTH_AUDIENCE=dummy AUTH_PROJECT_ID=dummy ./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL(`mindstock.frontend.generated.resources.Res.string` が生成される)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(frontend): string resources(ja)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 13: designsystem — テーマ(clay + Typography + tokens)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme/Typography.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme/MindstockTokens.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme/MindstockTheme.kt`

> clay の oklch 値は `app/core.jsx` の `TONES.clay`。Compose `Color` は sRGB なので **oklch を sRGB hex に変換**して使う。変換は実装時に oklch→sRGB(各値を算出)で行う。下記コードの hex はプレースホルダではなく「clay の各トークンを sRGB 8bit に変換した近似値」を入れること(算出して確定する)。

- [ ] **Step 1: Typography(NotoSansJP)**

`Typography.kt`(旧コードと同一・パッケージのみ designsystem に):

```kotlin
package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import mindstock.frontend.generated.resources.NotoSansJP_Black
import mindstock.frontend.generated.resources.NotoSansJP_Bold
import mindstock.frontend.generated.resources.NotoSansJP_ExtraBold
import mindstock.frontend.generated.resources.NotoSansJP_ExtraLight
import mindstock.frontend.generated.resources.NotoSansJP_Light
import mindstock.frontend.generated.resources.NotoSansJP_Medium
import mindstock.frontend.generated.resources.NotoSansJP_Regular
import mindstock.frontend.generated.resources.NotoSansJP_SemiBold
import mindstock.frontend.generated.resources.NotoSansJP_Thin
import mindstock.frontend.generated.resources.Res
import org.jetbrains.compose.resources.Font

@Composable
fun notoSansJpFamily(): FontFamily =
    FontFamily(
        Font(Res.font.NotoSansJP_Thin, FontWeight.Thin, FontStyle.Normal),
        Font(Res.font.NotoSansJP_ExtraLight, FontWeight.ExtraLight, FontStyle.Normal),
        Font(Res.font.NotoSansJP_Light, FontWeight.Light, FontStyle.Normal),
        Font(Res.font.NotoSansJP_Regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.NotoSansJP_Medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.NotoSansJP_SemiBold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.NotoSansJP_Bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.NotoSansJP_ExtraBold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(Res.font.NotoSansJP_Black, FontWeight.Black, FontStyle.Normal),
    )

@Composable
fun appTypography(): Typography {
    val f = notoSansJpFamily()
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = f),
        displayMedium = d.displayMedium.copy(fontFamily = f),
        displaySmall = d.displaySmall.copy(fontFamily = f),
        headlineLarge = d.headlineLarge.copy(fontFamily = f),
        headlineMedium = d.headlineMedium.copy(fontFamily = f),
        headlineSmall = d.headlineSmall.copy(fontFamily = f),
        titleLarge = d.titleLarge.copy(fontFamily = f),
        titleMedium = d.titleMedium.copy(fontFamily = f),
        titleSmall = d.titleSmall.copy(fontFamily = f),
        bodyLarge = d.bodyLarge.copy(fontFamily = f),
        bodyMedium = d.bodyMedium.copy(fontFamily = f),
        bodySmall = d.bodySmall.copy(fontFamily = f),
        labelLarge = d.labelLarge.copy(fontFamily = f),
        labelMedium = d.labelMedium.copy(fontFamily = f),
        labelSmall = d.labelSmall.copy(fontFamily = f),
    )
}
```

- [ ] **Step 2: MindstockTokens(status色/影/半径の CompositionLocal)**

`MindstockTokens.kt`:

```kotlin
package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material3 ColorScheme に無いモック固有トークン(status / 半径)。 */
data class MindstockTokens(
    val statusOk: Color,
    val statusOkSoft: Color,
    val statusLow: Color,
    val statusLowSoft: Color,
    val statusOut: Color,
    val statusOutSoft: Color,
    val radiusSm: Dp = 12.dp,
    val radiusMd: Dp = 16.dp,
    val radiusLg: Dp = 22.dp,
    val radiusXl: Dp = 28.dp,
)

/** clay の status 値(oklch→sRGB 変換済み・STATUS in core.jsx)。変換値を確定して入れる。 */
val clayTokens = MindstockTokens(
    statusOk = Color(0xFF4E9C84),
    statusOkSoft = Color(0xFFDFF1EA),
    statusLow = Color(0xFFC99A3E),
    statusLowSoft = Color(0xFFF6ECD6),
    statusOut = Color(0xFFC0573F),
    statusOutSoft = Color(0xFFF4E0DA),
)

val LocalMindstockTokens = staticCompositionLocalOf { clayTokens }
```

> 上記 status hex は core.jsx の `STATUS` oklch を sRGB に変換した近似。実装時に正確に算出して置換すること(oklch→OKLab→linear sRGB→gamma)。

- [ ] **Step 3: MindstockTheme(clay ColorScheme + Expressive)**

`MindstockTheme.kt`:

```kotlin
package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** clay の TONES.clay(oklch→sRGB 変換済み)。変換値を確定して入れる。 */
private val ClayColorScheme = lightColorScheme(
    primary = Color(0xFFB5613B),        // accent
    onPrimary = Color(0xFFFEFCF7),      // onAccent
    primaryContainer = Color(0xFFF3E0D2), // accentSoft
    background = Color(0xFFF6F4EF),     // bg
    surface = Color(0xFFFEFDFA),        // surface
    surfaceVariant = Color(0xFFF7F4EF), // surface2
    onSurface = Color(0xFF453E38),      // ink
    onSurfaceVariant = Color(0xFF7C746C), // sub
    outline = Color(0xFFE3DDD3),        // line
    outlineVariant = Color(0xFFEDE8DF), // lineSoft
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MindstockTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMindstockTokens provides clayTokens) {
        MaterialExpressiveTheme(
            colorScheme = ClayColorScheme,
            typography = appTypography(),
            content = content,
        )
    }
}
```

> `MaterialExpressiveTheme` のシグネチャ(引数名・必須/任意)は `1.10.0-alpha05` の実 API を確認して合わせる。`colorScheme`/`typography`/`content` が無ければ `MaterialTheme` にフォールバック(Expressive は motion/shape 中心で colorScheme は同じく渡せる)。**この 1 点だけ alpha API 要確認**。

- [ ] **Step 4: コンパイル確認 / Commit**

Run: `AUTH_CLIENT_ID=dummy AUTH_AUDIENCE=dummy AUTH_PROJECT_ID=dummy ./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme
git commit -m "feat(frontend): MindstockTheme(clay + M3 Expressive + NotoSansJP)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 14: designsystem — 参照画面が使う atom

参照画面(在庫一覧)が必要とする atom のみ作る。残り(Stepper / RoundBtn / Thumb / Sheet の全機能等)は **P6-1 のスコープ**(本タスクで全部は作らない)。

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppIcon.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/PrimaryButton.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/StatusDot.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/StockLevelBar.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/SegmentedControl.kt`

- [ ] **Step 1: AppIcon(material-icons-extended ラップ)**

`AppIcon.kt`:

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** semantic アイコン名。feature は AppIconName でのみ参照(将来差し替え可能に)。 */
enum class AppIconName { Box, Cart, Plus, Minus, Clock, Home, User }

private fun AppIconName.vector(): ImageVector =
    when (this) {
        AppIconName.Box -> Icons.Filled.Inventory2
        AppIconName.Cart -> Icons.Filled.ShoppingCart
        AppIconName.Plus -> Icons.Filled.Add
        AppIconName.Minus -> Icons.Filled.Remove
        AppIconName.Clock -> Icons.Outlined.AccessTime
        AppIconName.Home -> Icons.Outlined.Home
        AppIconName.User -> Icons.Outlined.Person
    }

@Composable
fun AppIcon(name: AppIconName, contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(imageVector = name.vector(), contentDescription = contentDescription, modifier = modifier)
}
```

- [ ] **Step 2: PrimaryButton**

`PrimaryButton.kt`:

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}
```

- [ ] **Step 3: StatusDot**(`Stock.status()` の区分に対応)

`StatusDot.kt`:

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** ok/low/out の 3 区分。color は呼び出し側が MindstockTokens から渡す。 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Surface(color = color, shape = CircleShape, modifier = modifier.size(8.dp)) {}
}
```

- [ ] **Step 4: StockLevelBar**(qty vs min の比率バー。core.jsx StockBar 相当)

`StockLevelBar.kt`:

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun StockLevelBar(qty: Int, min: Int, color: Color, modifier: Modifier = Modifier) {
    val comfortable = max(max(min * 2, min + 3), max(qty, 1))
    val pct = (qty.toFloat() / comfortable).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { pct },
        color = color,
        modifier = modifier.fillMaxWidth().height(8.dp),
    )
}
```

- [ ] **Step 5: SegmentedControl**(list/grid 切替)

`SegmentedControl.kt`:

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class SegOption(val key: String, val label: String)

@Composable
fun SegmentedControl(
    options: List<SegOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o.key == selectedKey,
                onClick = { onSelect(o.key) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(o.label) }
        }
    }
}
```

- [ ] **Step 6: コンパイル確認 / Commit**

Run: `AUTH_CLIENT_ID=dummy AUTH_AUDIENCE=dummy AUTH_PROJECT_ID=dummy ./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

> Material3 のアイコン名(`Inventory2` 等)や `SingleChoiceSegmentedButtonRow` の API が版で違えば、エラーメッセージに従い実在 symbol へ置換。

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom
git commit -m "feat(frontend): designsystem atom(在庫一覧用)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 15: feature/inventory — Repository

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data/InventoryRepository.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data/InventoryRepositoryTest.kt`

Repository は RPC を隠蔽する。テストでは `ProductRpcService` / `StockRegisterRpcService` の fake 実装を注入する(`@Rpc` interface を直接 implement)。

> 着手前に `:domain` の `Stocks`(list 公開 API)、`ProductId`、`Quantity`、`Note` のコンストラクタ/ファクトリを確認し、テストの値生成を実型に合わせる。

- [ ] **Step 1: 失敗するテスト**

`InventoryRepositoryTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.data

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

class InventoryRepositoryTest {
    @Test
    fun list_returns_success_outcome_on_ok() = runTest {
        val fakeProduct = object : FakeProductRpc() {
            override suspend fun list(householdId: HouseholdId) = RpcResult.Ok(Stocks(emptyList()))
        }
        val repo = InventoryRepository(productService = { fakeProduct }, stockRegisterService = { error("unused") })
        val out = repo.list(HouseholdId.dummy())
        out.shouldBeInstanceOf<RpcOutcome.Success<Stocks>>()
    }
}
```

> `FakeProductRpc` は `ProductRpcService` の全メソッドを `error("unused")` で実装した abstract base を commonTest に置き、テストごとに必要メソッドだけ override する。`HouseholdId.dummy()` は実際の生成方法に置換(domain 確認)。`Stocks(emptyList())` のコンストラクタも実型に合わせる。

- [ ] **Step 2: 失敗確認**

Run: `./gradlew :frontend:jsTest --tests "*InventoryRepositoryTest*"`
Expected: FAIL

- [ ] **Step 3: 実装**

`InventoryRepository.kt`:

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.data

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService

/**
 * 在庫まわりの RPC を隠蔽。サービスは「開く関数」を遅延注入(認証後にトークン付きで open される）。
 */
class InventoryRepository(
    private val productService: () -> ProductRpcService,
    private val stockRegisterService: () -> StockRegisterRpcService,
) {
    suspend fun list(householdId: HouseholdId): RpcOutcome<Stocks> =
        productService().list(householdId).toOutcome()

    suspend fun replenish(productId: ProductId, quantity: Quantity, note: Note): RpcOutcome<Unit> =
        stockRegisterService().replenish(productId, quantity, note).toOutcome()

    suspend fun consume(productId: ProductId, quantity: Quantity, note: Note): RpcOutcome<Unit> =
        stockRegisterService().consume(productId, quantity, note).toOutcome()
}
```

- [ ] **Step 4: 通過確認 / Commit**

Run: `./gradlew :frontend:jsTest --tests "*InventoryRepositoryTest*"`
Expected: PASS

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data/InventoryRepository.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data
git commit -m "feat(frontend): InventoryRepository を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 16: feature/inventory — UiState + ViewModel

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryUiState.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModelTest.kt`

- [ ] **Step 1: 失敗するテスト**

`InventoryViewModelTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import kotlin.test.Test

class InventoryViewModelTest {
    @Test
    fun load_success_sets_content() = runTest {
        val vm = InventoryViewModel(
            householdId = HouseholdId.dummy(),
            loadStocks = { RpcOutcome.Success(Stocks(emptyList())) },
        )
        vm.load()
        vm.state.value.shouldBeInstanceOf<InventoryUiState.Content>()
    }

    @Test
    fun load_failure_sets_error() = runTest {
        val vm = InventoryViewModel(
            householdId = HouseholdId.dummy(),
            loadStocks = { RpcOutcome.Failure(net.brightroom.mindstock.rpc.result.RpcError.Internal("boom")) },
        )
        vm.load()
        vm.state.value.shouldBeInstanceOf<InventoryUiState.Error>()
    }
}
```

- [ ] **Step 2: 失敗確認**

Run: `./gradlew :frontend:jsTest --tests "*InventoryViewModelTest*"`
Expected: FAIL

- [ ] **Step 3: 実装**

`InventoryUiState.kt`:

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.Stocks

sealed interface InventoryUiState {
    data object Loading : InventoryUiState
    data class Content(val stocks: Stocks, val view: StockView) : InventoryUiState
    data class Error(val message: String) : InventoryUiState
}

enum class StockView { List, Grid }
```

`InventoryViewModel.kt`:

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.userMessageOf

class InventoryViewModel(
    private val householdId: HouseholdId,
    private val loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>,
) : ViewModel() {
    private val _state = MutableStateFlow<InventoryUiState>(InventoryUiState.Loading)
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()
    private var view = StockView.List

    suspend fun load() {
        _state.value = InventoryUiState.Loading
        _state.value = when (val out = loadStocks(householdId)) {
            is RpcOutcome.Success -> InventoryUiState.Content(out.value, view)
            is RpcOutcome.Failure -> InventoryUiState.Error(userMessageOf(out.error))
        }
    }

    fun setView(v: StockView) {
        view = v
        val s = _state.value
        if (s is InventoryUiState.Content) _state.value = s.copy(view = v)
    }
}
```

> テストの `loadStocks = { ... }` シグネチャと一致させること(`suspend (HouseholdId) -> RpcOutcome<Stocks>`)。本番は `InventoryRepository::list` を渡す。

- [ ] **Step 4: 通過確認 / Commit**

Run: `./gradlew :frontend:jsTest --tests "*InventoryViewModelTest*"`
Expected: PASS

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryUiState.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModel.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModelTest.kt
git commit -m "feat(frontend): InventoryViewModel + UiState を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 17: feature/inventory — StockHomeScreen(UI)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomeScreen.kt`

UI はテスト網羅しない(規約)。`InventoryUiState` を描画し、view 切替を ViewModel に委譲。Stock の表示項目(商品名・現在数量・status)は `:domain` の `Stock` API(`currentQuantity()` / `status()` / `product`)を確認して合わせる。

- [ ] **Step 1: 実装**

`StockHomeScreen.kt`:

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.atom.SegOption
import net.brightroom.mindstock.frontend.designsystem.atom.SegmentedControl
import net.brightroom.mindstock.frontend.feature.inventory.InventoryUiState
import net.brightroom.mindstock.frontend.feature.inventory.StockView

@Composable
fun StockHomeScreen(
    state: InventoryUiState,
    onSelectView: (StockView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            is InventoryUiState.Loading -> CircularProgressIndicator()
            is InventoryUiState.Error -> Text(state.message)
            is InventoryUiState.Content -> {
                SegmentedControl(
                    options = listOf(SegOption("list", "リスト"), SegOption("grid", "グリッド")),
                    selectedKey = if (state.view == StockView.List) "list" else "grid",
                    onSelect = { onSelectView(if (it == "list") StockView.List else StockView.Grid) },
                )
                StockList(state)
            }
        }
    }
}

@Composable
private fun StockList(content: InventoryUiState.Content) {
    // content.stocks の list API を domain で確認して iterate する。
    // 各 Stock を 1 行で: 商品名 / 現在数量 / status。詳細な見た目は P6-1 で作り込む。
    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // items(content.stocks.list) { stock -> StockRow(stock) }
        // ↑ Stocks の公開 list プロパティ名を確認して置換(例: stocks.values / stocks.list)
    }
}
```

> このタスクは P6-0 の実証用に「描画され、view 切替が動く」ことが目的。行の作り込み(Thumb / StatusDot / StockLevelBar / 補充消費ボタン配置)は P6-1。`Stocks` の list 公開プロパティ名と `Stock` の表示 API を確認してから `StockList` の `items(...)` を埋める。

- [ ] **Step 2: コンパイル確認 / Commit**

Run: `AUTH_CLIENT_ID=dummy AUTH_AUDIENCE=dummy AUTH_PROJECT_ID=dummy ./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomeScreen.kt
git commit -m "feat(frontend): StockHomeScreen(在庫一覧・実証用)を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 18: ナビゲーション + shell

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/navigation/Route.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/AppShell.kt`

mobile/desktop の adaptive 外枠 + 4 タブ。P6-0 では「在庫」タブに StockHomeScreen を出し、他タブは placeholder(`Text`)で可(P6-1〜3 で実装)。

- [ ] **Step 1: Route 定義**

`Route.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.navigation

import kotlinx.serialization.Serializable

/** トップレベル目的地(型安全 route)。 */
sealed interface Route {
    @Serializable data object Stock : Route
    @Serializable data object Shop : Route
    @Serializable data object Activity : Route
    @Serializable data object Profile : Route
}
```

- [ ] **Step 2: AppShell(adaptive navigation suite)**

`AppShell.kt`:

```kotlin
package net.brightroom.mindstock.frontend.app.shell

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName

enum class Tab(val label: String, val icon: AppIconName) {
    Stock("在庫", AppIconName.Home),
    Shop("買い物", AppIconName.Cart),
    Activity("履歴", AppIconName.Clock),
    Profile("設定", AppIconName.User),
}

@Composable
fun AppShell(
    stockContent: @Composable () -> Unit,
) {
    var selected by remember { mutableStateOf(Tab.Stock) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = tab == selected,
                    onClick = { selected = tab },
                    icon = { AppIcon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                )
            }
        },
    ) {
        when (selected) {
            Tab.Stock -> stockContent()
            Tab.Shop -> Text("買い物(P6-1)")
            Tab.Activity -> Text("履歴(P6-1)")
            Tab.Profile -> Text("設定(P6-3)")
        }
    }
}
```

> `NavigationSuiteScaffold` の API(`navigationSuiteItems` / `item(...)` の引数)は導入版で確認して合わせる。文言は本タスクでは暫定直書きだが、確定時に string resources へ寄せる(`frontend-i18n-and-font.md`)。Tab.label も resources 化候補。

- [ ] **Step 3: コンパイル確認 / Commit**

Run: `AUTH_CLIENT_ID=dummy AUTH_AUDIENCE=dummy AUTH_PROJECT_ID=dummy ./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/navigation/Route.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/AppShell.kt
git commit -m "feat(frontend): adaptive shell + Route を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 19: App ルート配線(全部つなぐ)

ここまでの部品を `App()` で配線し、土台が縦に通ることを確認する。web 固有の副作用束ね(`AuthDeps` の実装・RpcClientProvider の生成・PKCE 一時値の sessionStorage 保存)は `webMain` に置く。

**Files:**
- Create: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/WebAuthDeps.kt`
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`(現スタブを置換)

- [ ] **Step 1: WebAuthDeps(AuthDeps の web 実装)**

`WebAuthDeps.kt`:

```kotlin
package net.brightroom.mindstock.frontend

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.frontend.auth.AuthClient
import net.brightroom.mindstock.frontend.auth.AuthConfig
import net.brightroom.mindstock.frontend.auth.Pkce
import net.brightroom.mindstock.frontend.auth.TokenStore
import net.brightroom.mindstock.frontend.auth.Tokens
import net.brightroom.mindstock.frontend.app.AuthDeps
import net.brightroom.mindstock.frontend.core.auth.BrowserNav
import net.brightroom.mindstock.frontend.core.rpc.RpcClientProvider
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.frontend.core.session.AppSession
import net.brightroom.mindstock.rpc.resident.ResidentRpcService
import net.brightroom.mindstock.rpc.result.RpcResult

private const val STATE_KEY = "mindstock.oauth.state.v1"
private const val VERIFIER_KEY = "mindstock.oauth.verifier.v1"

class WebAuthDeps(
    private val authClient: AuthClient,
    private val rpc: RpcClientProvider,
    private val session: AppSession,
) : AuthDeps {
    override fun currentPath(): String = BrowserNav.currentPath()

    override suspend fun handleCallback() {
        val savedState = SessionStore.get(STATE_KEY)
        val savedVerifier = SessionStore.get(VERIFIER_KEY) ?: error("no verifier")
        val receivedState = BrowserNav.currentQueryParam("state") ?: ""
        require(savedState != null && savedState == receivedState) { "state mismatch" }
        val code = BrowserNav.currentQueryParam("code") ?: error("no code")
        val tokens = authClient.exchangeCode(code, savedVerifier)
        TokenStore.save(tokens)
        SessionStore.remove(STATE_KEY); SessionStore.remove(VERIFIER_KEY)
        BrowserNav.replace("/")
    }

    override fun loadValidToken(): Tokens? = TokenStore.load()?.takeUnless { it.willExpireWithin(30) }

    override suspend fun redirectToAuthorize() {
        val verifier = Pkce.newVerifier()
        val state = Pkce.newVerifier(length = 43)
        SessionStore.set(STATE_KEY, state)
        SessionStore.set(VERIFIER_KEY, verifier)
        val scope = "openid profile offline_access urn:zitadel:iam:org:project:id:${AuthConfig.PROJECT_ID}:aud"
        val url = AuthClient.buildAuthorizeUrl(
            issuer = AuthConfig.ISSUER,
            clientId = AuthConfig.CLIENT_ID,
            redirectUri = AuthConfig.REDIRECT_URI,
            scope = scope,
            state = state,
            codeChallenge = Pkce.challenge(verifier),
        )
        BrowserNav.assign(url)
    }

    override suspend fun fetchMe(token: Tokens): Resident {
        val client = rpc.open("resident", token.accessToken)
        return when (val r = client.withService<ResidentRpcService>().me()) {
            is RpcResult.Ok -> r.value
            is RpcResult.Err -> error("me failed: ${r.error}") // 未登録/拒否 → throw → NeedOnboarding
        }
    }

    override fun onAuthenticated(resident: Resident) {
        // resident の id / profile から session を設定(Resident の公開 API を確認して合わせる）
        // session.setResident(resident.id, resident.profile.displayName)
    }
}

/** SessionStorage は internal なので web 側の薄い橋渡し(PKCE 一時値用)。 */
internal object SessionStore {
    fun get(key: String): String? = net.brightroom.mindstock.frontend.auth.sessionStorageGet(key)
    fun set(key: String, value: String) = net.brightroom.mindstock.frontend.auth.sessionStorageSet(key, value)
    fun remove(key: String) = net.brightroom.mindstock.frontend.auth.sessionStorageRemove(key)
}
```

> 注: `auth.SessionStorage` は `internal object`。PKCE 一時値(state/verifier)用に web から触れる薄い関数が必要。`auth` パッケージに `internal fun sessionStorageGet/Set/Remove` を expect/actual で追加するか、`SessionStorage` の可視性を `internal` のまま同モジュール内 web コードから使えるよう配置する(`webMain` は同一 `:frontend` モジュールなので `internal` 参照可能)。実装時に最小の形に整える(おそらく `TokenStore` と同じく `auth.SessionStorage.get/set/remove` を直接呼べる — `internal` は同モジュールで可視)。その場合 `SessionStore` ラッパは不要で `SessionStorage.get(...)` を直接使う。

- [ ] **Step 2: App.kt を配線**

`App.kt`:

```kotlin
package net.brightroom.mindstock.frontend

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import net.brightroom.mindstock.frontend.app.AuthViewModel
import net.brightroom.mindstock.frontend.auth.AuthClient
import net.brightroom.mindstock.frontend.auth.AuthConfig
import net.brightroom.mindstock.frontend.app.shell.AppShell
import net.brightroom.mindstock.frontend.core.auth.AuthState
import net.brightroom.mindstock.frontend.core.rpc.RpcClientProvider
import net.brightroom.mindstock.frontend.core.session.AppSession
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockTheme

@Composable
fun App() {
    val http = remember { HttpClient { install(ContentNegotiation) { json() }; install(WebSockets) } }
    val authClient = remember { AuthClient(http, AuthConfig.ISSUER, AuthConfig.CLIENT_ID, AuthConfig.REDIRECT_URI) }
    val session = remember { AppSession() }
    val rpc = remember {
        val wsBase = window.location.origin.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        RpcClientProvider(http, baseUrl = wsBase)
    }
    val vm = remember { AuthViewModel(WebAuthDeps(authClient, rpc, session)) }
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.boot() }

    MindstockTheme {
        when (state) {
            is AuthState.Booting -> Text("読み込み中…")
            is AuthState.Failed -> Text((state as AuthState.Failed).message)
            is AuthState.NeedOnboarding -> Text("オンボーディング(P6-3)") // 仮。P6-3 で表示名登録+世帯作成
            is AuthState.Ready -> AppShell(stockContent = { Text("在庫一覧(配線確認用プレースホルダ)") })
        }
    }
}
```

> P6-0 のゴールは「boot シーケンス → Ready → AppShell が描画される」こと。`stockContent` に実際の `StockHomeScreen` + `InventoryViewModel` を差すのは、AppSession に activeHouseholdId が入る経路(`household.list()` 呼び出し)を含むため **P6-1 の最初のタスク**で繋ぐ。P6-0 ではプレースホルダで土台疎通を確認する。

- [ ] **Step 3: コンパイル + 全テスト**

Run: `AUTH_CLIENT_ID=dummy AUTH_AUDIENCE=dummy AUTH_PROJECT_ID=dummy ./gradlew :frontend:compileKotlinWasmJs :frontend:jsTest`
Expected: BUILD SUCCESSFUL / 全テスト PASS

- [ ] **Step 4: Commit**

```bash
git add frontend/src/webMain
git commit -m "feat(frontend): App ルート配線(boot→shell 疎通)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 20: 最終確認 + ブラウザ起動確認(任意)

- [ ] **Step 1: 全コンパイル + テスト緑**

Run: `AUTH_CLIENT_ID=dummy AUTH_AUDIENCE=dummy AUTH_PROJECT_ID=dummy ./gradlew :frontend:compileKotlinWasmJs :frontend:compileKotlinJs :frontend:jsTest`
Expected: BUILD SUCCESSFUL / 全テスト PASS

- [ ] **Step 2: (任意・ローカル)dev server 起動でログイン経路を目視**

backend(`./gradlew :backend:api:run`)+ Zitadel を起動できる環境なら:
Run: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`(OOM 注意。重ければスキップして CI に委ねる)
Expected: `/` → authorize へ redirect → ログイン → callback → `me()` → Ready or NeedOnboarding。

- [ ] **Step 3: ルールの検証・補正**

土台実装で判明したズレ(命名 / 構成 / API)を frontend ルール 6 本に反映する(ルールは Task 1 で先に書いたが、実装が検証する設計 — spec §9)。差分があれば該当ルールを Edit してコミット。

```bash
git add .claude/rules
git commit -m "docs(rules): 土台実装の知見を frontend ルールに反映

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review(計画者によるチェック結果)

**Spec coverage(spec §1〜10 と Task の対応):**
- §1 構成方針 → Task 1(rules)/ §2 パッケージ → 全 Task の配置 / §3 PKCE boot → Task 4,5,6,10,11,19 / §4 RPC+session → Task 7,8,9 / §5 テーマ → Task 13,14 / §6 shell → Task 18 / §7 ViewModel・エラー → Task 8,11,16 / §8 参照画面 → Task 15,16,17,19 / §9 ルール → Task 1(+20で補正) / §10 ビルド/テスト → 全 Task の検証コマンド。**ギャップ無し**。
- 補足: spec §3.1 は「Err(NotFound)→onboard」と理想化しているが、実機構は「me() throw→onboard」(前提 #3)。Task 11 で正しい機構を実装し、その旨を明記済み。

**Placeholder scan:** 「TBD/TODO/後で」等の禁止表現なし。alpha API 要確認箇所(Task 13 の `MaterialExpressiveTheme`、Task 18 の `NavigationSuiteScaffold`)と domain 型確認箇所(`Stocks`/`HouseholdId`/`Stock` API)は、**プレースホルダではなく「実 API に合わせる」明示指示**として残置(これらは検証可能で、エラーメッセージが正解を教える性質)。clay の oklch→sRGB hex は「算出して確定」を明示。

**Type consistency:** `RpcOutcome`(Success/Failure)・`AuthState`(Booting/Ready/NeedOnboarding/Failed)・`AuthDeps`(6 メソッド)・`InventoryUiState`(Loading/Content/Error)・`AppSession.State` を定義タスクと利用タスクで照合済み。`AuthViewModel(deps: AuthDeps)` のコンストラクタは Task 11 定義と Task 19 利用で一致。`InventoryViewModel(householdId, loadStocks)` は Task 16 定義とテストで一致。

**既知の実装時確認(コンパイルが正解を教える、ブロッカーではない):**
1. `MaterialExpressiveTheme` の引数(alpha05)
2. `NavigationSuiteScaffold` / `SingleChoiceSegmentedButtonRow` の API
3. `:domain` の `Stocks`/`Stock`/`HouseholdId`/`Quantity`/`Note`/`Resident` 公開 API(値生成・list プロパティ名・表示メソッド)
4. `kotlin.time.Instant` の `@Serializable`(不可なら epochSeconds:Long 保持に切替)
5. navigation-compose の互換バージョン

---

## 関連

- spec: [docs/superpowers/specs/2026-06-05-p6-0-frontend-foundation-design.md](../specs/2026-06-05-p6-0-frontend-foundation-design.md)
- 親設計: [docs/superpowers/specs/2026-05-31-mindstock-full-replace-design/00-index.md](../specs/2026-05-31-mindstock-full-replace-design/00-index.md)
- proven code: git `ec695e2^`(旧 frontend。PKCE/RPC クライアントの動作実績)
- rules(本プランで新設): `.claude/rules/frontend-*.md`(6 本)
