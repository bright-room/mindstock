# P0: 解体と土台再構築 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:shared` を除く全モジュールの Kotlin ソースと旧 docs(設計フォルダを除く)を削除し、各モジュールが空でもコンパイルし、バックエンドが `/health` で起動・フロントが空画面を表示する「緑のビルド土台」を作る。

**Architecture:** 既存の KMP マルチモジュール構成・gradle・build-logic・CI・settings は維持。`:domain`/`:rpc`/`:backend:core` は中身を空にして以降のプランで再構築する。`:backend:api` は `application.yaml` 依存(EngineMain + config モジュール)をやめ、`embeddedServer` で最小起動する。`:frontend` は `ComposeViewport { App() }` の `App` を空スタブに置換する。

**Tech Stack:** Kotlin Multiplatform / Ktor / Compose Multiplatform(Wasm)/ Gradle。

> **注(実行後の移設):** P0 実行後、設計と本プランを superpowers 構成へ移設した(設計 = [`../specs/2026-05-31-mindstock-full-replace-design/`](../specs/2026-05-31-mindstock-full-replace-design/00-index.md)、本プラン = `docs/superpowers/plans/`)。本文中 Task 1 の `docs/design/...` は **P0 実行当時のパス**(当時はこの設計が `docs/design/` 配下に存在した)であり記録として残す。

> **実装ロードマップ(本プランは 1/7)**
> 1. **P0 解体と土台**(本プラン)
> 2. P1 domain — resident / household コンテキスト(TDD)
> 3. P2 domain — catalog / inventory(Stock・Product・StockMovement・ShoppingList)(TDD)
> 4. P3 `:rpc` — `@Rpc` service interface + `RpcResult` / `RpcError` + DTO
> 5. P4 `:backend:core` — Exposed テーブル + Flyway migration + Repository(コンテキスト別)
> 6. P5 `:backend:api` + `:backend:core` — application service + presentation `@Rpc` controller + Zitadel 認証配線
> 7. P6 `:frontend` — Compose 画面(ログイン/オンボーディング/在庫/買い物/履歴/設定/商品追加/招待)
>
> 設計の真実は [`../specs/2026-05-31-mindstock-full-replace-design/00-index.md`](../specs/2026-05-31-mindstock-full-replace-design/00-index.md)(01 sudo / 02 ICONIX / 03 詳細ドメイン)。

---

## 削除・作成するファイル

**削除(`git rm`):**
- 旧 docs(設計フォルダ以外の `docs/` 配下 Markdown): `docs/authentication-current-state.md`、`docs/superpowers/**`
- `:domain` / `:rpc` / `:backend:core` / `:backend:schedules` / `:backend:api` / `:frontend` の全 `*.kt`(`src/**` 配下。`build/` 生成物は対象外)

**保持:**
- `shared/**` の全 Kotlin、`build-logic/**`、各 `build.gradle.kts`、`settings.gradle.kts`、`gradle/`、`.github/`、`docs/design/**`、`docs/スクリーンショット*.png`、各モジュールの `resources/`(`logback.xml` / `index.html` 等)

**作成(最小スケルトン):**
- `backend/api/src/main/kotlin/net/brightroom/mindstock/Main.kt`(embeddedServer + `/health`)
- `backend/schedules/src/main/kotlin/net/brightroom/mindstock/Main.kt`(プレースホルダ)
- `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/Main.kt`(既存維持)
- `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`(空スタブに置換)
- `backend/api/src/main/resources/application.yaml`(modules を空に簡素化)

> `:domain` / `:rpc` / `:backend:core` は **ソースを 0 ファイル**にする(空モジュールはコンパイル可)。

---

## Task 1: 旧 docs Markdown を削除(設計フォルダは保持)

**Files:**
- Delete: `docs/authentication-current-state.md`, `docs/superpowers/**`
- Keep: `docs/design/**`, `docs/スクリーンショット*.png`

- [ ] **Step 1: 削除対象を確認(設計フォルダが除外されていること)**

Run:
```bash
find docs -name "*.md" -not -path "docs/design/*"
```
Expected: `docs/authentication-current-state.md` と `docs/superpowers/specs/*.md` / `docs/superpowers/plans/*.md` のみが列挙され、`docs/design/` 配下は **含まれない**。

- [ ] **Step 2: 削除を実行**

Run:
```bash
git rm -q docs/authentication-current-state.md
git rm -qr docs/superpowers
```

- [ ] **Step 3: 設計フォルダと新プランが残っていることを確認**

Run:
```bash
ls docs/design/2026-05-31-mindstock-full-replace/ && ls docs/design/2026-05-31-mindstock-full-replace/plans/
```
Expected: `00-index.md`〜`03-domain-detail.md` と本プランファイルが存在する。

- [ ] **Step 4: Commit**

```bash
git add -A docs
git commit -m "docs: 旧 spec/plan を削除しフルリプレイス設計に一本化"
```

---

## Task 2: `:shared` 以外の Kotlin ソースを全削除

**Files:**
- Delete: `domain/src/**/*.kt`, `rpc/src/**/*.kt`, `backend/core/src/**/*.kt`, `backend/schedules/src/**/*.kt`, `backend/api/src/**/*.kt`, `frontend/src/**/*.kt`
- Keep: `shared/src/**`, `build-logic/**`, 各 `resources/`

- [ ] **Step 1: 削除対象の .kt を確認(shared / build-logic / build 生成物を除外)**

Run:
```bash
find domain rpc backend frontend -path "*/src/*" -name "*.kt"
```
Expected: 上記 6 モジュールの `src` 配下 `*.kt` が列挙される(`shared/` と `build-logic/` は含まれない)。

- [ ] **Step 2: 一括削除**

Run:
```bash
find domain rpc backend frontend -path "*/src/*" -name "*.kt" -exec git rm -q {} +
```

- [ ] **Step 3: 残存 .kt が shared / build-logic のみであることを確認**

Run:
```bash
find . -name "*.kt" -not -path "*/build/*" -not -path "./shared/*" -not -path "./build-logic/*"
```
Expected: **出力なし**(空)。

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor: shared を除く Kotlin ソースを全削除(フルリプレイス土台)"
```

---

## Task 3: `:backend:api` を最小起動スケルトンに再構築

`application.yaml` の config モジュール参照(削除済みクラス)をやめ、`embeddedServer` で `/health` のみを返す最小サーバにする。

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/Main.kt`
- Modify: `backend/api/src/main/resources/application.yaml`

- [ ] **Step 1: Main.kt を作成**

`backend/api/src/main/kotlin/net/brightroom/mindstock/Main.kt`:
```kotlin
package net.brightroom.mindstock

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port, module = Application::healthModule).start(wait = true)
}

fun Application.healthModule() {
    routing {
        get("/health") { call.respondText("OK") }
    }
}
```

- [ ] **Step 2: application.yaml を簡素化(modules を空に / 旧 config 参照を除去)**

`backend/api/src/main/resources/application.yaml`:
```yaml
ktor:
  environment: "$KTOR_ENV:LOCAL"
  deployment:
    port: "$PORT:8080"
```

- [ ] **Step 3: ビルドして緑を確認**

Run: `./gradlew :backend:api:build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 起動して /health を確認**

Run(別シェル可):
```bash
./gradlew :backend:api:run &
sleep 20 && curl -s localhost:8080/health
```
Expected: `OK`。確認後 `kill %1` でサーバ停止。

- [ ] **Step 5: Commit**

```bash
git add backend/api
git commit -m "feat(backend): /health のみの最小起動スケルトン(embeddedServer)"
```

---

## Task 4: `:backend:schedules` と `:frontend` のスケルトンを再構築

**Files:**
- Create: `backend/schedules/src/main/kotlin/net/brightroom/mindstock/Main.kt`
- Create: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/Main.kt`
- Create: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`

- [ ] **Step 1: schedules Main.kt(プレースホルダ)を作成**

`backend/schedules/src/main/kotlin/net/brightroom/mindstock/Main.kt`:
```kotlin
package net.brightroom.mindstock

fun main() {
    println("schedules: placeholder entrypoint. No batch implemented yet.")
}
```

- [ ] **Step 2: frontend Main.kt(Compose エントリ)を作成**

`frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/Main.kt`:
```kotlin
@file:OptIn(ExperimentalComposeUiApi::class)

package net.brightroom.mindstock.frontend

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

fun main() {
    ComposeViewport {
        App()
    }
}
```

- [ ] **Step 3: frontend App.kt(空スタブ)を作成**

`frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`:
```kotlin
package net.brightroom.mindstock.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun App() {
    MaterialTheme {
        Text("mindstock")
    }
}
```

- [ ] **Step 4: JVM 側と frontend コンパイルの緑を確認**

Run:
```bash
./gradlew :shared:build :domain:build :rpc:build :backend:core:build :backend:schedules:build :backend:api:build
```
Expected: `BUILD SUCCESSFUL`(`:domain` / `:rpc` / `:backend:core` は空モジュールとして成功)。

Run(フロントは Wasm リンクが OOM しやすいためコンパイルのみ確認):
```bash
./gradlew :frontend:compileKotlinWasmJs
```
Expected: `BUILD SUCCESSFUL`。

> 注: フル `:frontend` の Wasm ブラウザ配布(`wasmJsBrowserDistribution`)はローカルで OOM することがある(既知)。配布生成の最終確認は CI に委ねる。

- [ ] **Step 5: Commit**

```bash
git add backend/schedules frontend
git commit -m "feat: schedules / frontend の最小スケルトンを再構築"
```

---

## Task 5: 土台全体の緑を最終確認

- [ ] **Step 1: 残存 Kotlin がスケルトン + shared + build-logic のみであることを確認**

Run:
```bash
find . -name "*.kt" -not -path "*/build/*" -not -path "./build-logic/*"
```
Expected: `shared/src/**` と、Task 3/4 で作成した 4 ファイル(backend:api Main, schedules Main, frontend Main, frontend App)のみ。

- [ ] **Step 2: JVM フルビルド(フロント Wasm 配布を除外)**

Run:
```bash
./gradlew build -x :frontend:wasmJsBrowserDistribution -x :frontend:wasmJsBrowserProductionWebpack
```
Expected: `BUILD SUCCESSFUL`。失敗時は欠けているスケルトン(エントリ/リソース)を特定して補う。

> 統合テスト(`integrationTest`)は HikariCP プールがキャップ済みで通常実行できる(既知)。本プランでは DB 依存コードが無いため対象タスクなし。

- [ ] **Step 3: 完了コミット(差分が無ければスキップ)**

```bash
git status --short
```
差分があれば:
```bash
git add -A
git commit -m "chore: フルリプレイス土台のビルド緑化を確認"
```

---

## 完了条件(Definition of Done)

- `:shared` 以外の旧 Kotlin ソースと旧 docs(設計フォルダ除く)が削除されている。
- `./gradlew build -x :frontend:wasmJsBrowserDistribution -x :frontend:wasmJsBrowserProductionWebpack` が緑。
- `:backend:api:run` が起動し `GET /health` が `OK` を返す。
- `:frontend:compileKotlinWasmJs` が緑(空画面 `mindstock` を描画するスタブ)。
- 設計ドキュメントが保持されている(P0 実行後 `docs/superpowers/specs/2026-05-31-mindstock-full-replace-design/` へ移設)。
