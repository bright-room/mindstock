# フェーズ 0: 無風の掃除 実装プラン

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 参照ゼロの死コード・死変数・未使用 Gradle 依存・ビルドの罠(`check`→`integrationTest`)を削除し、以降のフェーズの diff を綺麗にする(挙動変更ゼロ)。

**Architecture:** 削除のみ。残す側のコードには触れない。各タスク=1 コミット粒度で、コミット単体でコンパイル/テストが通る。検証は「既存テスト green + コンパイル成功 + 削除シンボルの grep 残参照ゼロ」で行う(死コード削除のため新規テストは書かない。唯一 0-7 だけ既存テストの mockk スタブを修正する)。

**Tech Stack:** Kotlin Multiplatform / Gradle(build-logic convention)/ Ktor / kotlinx-rpc / Compose Multiplatform(Wasm)/ mockk / Kotest assertions

**前提:**
- 詳細な根拠・現物座標は spec [docs/superpowers/specs/2026-06-12-phase0-cleanup-design.md](../specs/2026-06-12-phase0-cleanup-design.md) を正とする。本プランは実行手順。
- 座標は 2026-06-12 / main(`342b2807`)で確認済み。**着手時に必ず現物を Read して行番号を再確認**してから編集する(先行コミットでずれうる)。
- ブランチ: `refactor/p0-cleanup`(main 起点)。
- コミットメッセージに issue/PR 番号を書かない。
- frontend のフルビルドは OOM するため使わない。コンパイル確認は `:frontend:compileKotlinWasmJs`(memory `local-build-tips`)。

---

## Task 0: ブランチ作成

- [ ] **Step 1: main 最新化してブランチを切る**

```bash
git switch main && git pull --ff-only
git switch -c refactor/p0-cleanup
```

- [ ] **Step 2: ベースライン確認(削除前の状態を把握)**

DB を起動せずに以下を実行し、**現状 `:backend:api:build` が赤になること**(= 0-8 で直す罠)を確認する。赤の原因が integrationTest であることをログで確認する。

```bash
./gradlew :backend:api:build
```

Expected: FAIL(`integrationTest` が TEST_DB に接続できず失敗、もしくは `check` 経由で integrationTest が起動)。この赤は Task 3 で解消する。

- [ ] **Step 3: domain/rpc の JVM テスト件数のベースラインを記録(0-11(d) 用)**

```bash
./gradlew :domain:jvmTest :rpc:jvmTest --rerun-tasks
```

Expected: PASS。`domain/build/reports/tests/jvmTest/index.html` と `rpc/build/reports/tests/jvmTest/index.html`、または実行ログの "tests completed" 件数を控える。**この件数は Task 5 で削除後と一致させる基準値**。

---

## Task 1: domain 死コード掃除(0-4 / 0-5 / 0-6)

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/ProductStatus.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/invitation/Invitation.kt:14`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/invitation/InvitationValidity.kt`
- Delete: `shared/src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlinx/datetime/LocalTime.kt`

- [ ] **Step 1: 0-4 `ProductStatus.isアーカイブ済()` を削除**

`ProductStatus.kt` を以下の全文に置き換える(メソッドと enum body セパレータ `;` を削除):

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable

@Serializable
enum class ProductStatus {
    採用中,
    アーカイブ済,
}
```

- [ ] **Step 2: 0-5 `Invitation.usable()` をインライン化**

`Invitation.kt:14` を変更:

```kotlin
    fun usable(): Boolean = validity == InvitationValidity.有効
```

- [ ] **Step 3: 0-5 `InvitationValidity.is有効()` を削除**

`InvitationValidity.kt` を以下の全文に置き換える:

```kotlin
package net.brightroom.mindstock.domain.model.household.invitation

import kotlinx.serialization.Serializable

@Serializable
enum class InvitationValidity {
    有効,
    無効,
}
```

- [ ] **Step 4: 0-6 `LocalTime.kt` をファイルごと削除**

```bash
git rm shared/src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlinx/datetime/LocalTime.kt
```

- [ ] **Step 5: 残参照ゼロを確認**

```bash
grep -rn --include='*.kt' -e 'isアーカイブ済' -e 'is有効' -e 'LocalTime.now' -e 'LocalTime.Companion' domain backend frontend shared
```

Expected: 出力なし(空)。

- [ ] **Step 6: domain / shared のテストとコンパイルを確認**

```bash
./gradlew :domain:test :shared:compileKotlinMetadata
```

Expected: PASS(`InvitationTest` の `usable()` / `revoke().usable()` 検証が green。`isアーカイブ済` / `is有効` を使うテストは元から無い)。

- [ ] **Step 7: コミット**

```bash
git add -A
git commit -m "chore: domain の参照ゼロ死メソッド/死 ext を削除

- ProductStatus.isアーカイブ済() を削除(参照ゼロ)
- InvitationValidity.is有効() を Invitation.usable() にインライン化
- shared の LocalTime.now() ext をファイルごと削除(全モジュール参照ゼロ)"
```

---

## Task 2: backend 死コード掃除(0-1 / 0-7)

**Files:**
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/Environment.kt`
- Modify: `backend/api/src/main/resources/application.yaml:2`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/stock/StockRegisterRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockRegisterDataSource.kt`
- Modify: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterServiceTest.kt:86,101,125`(+ import)

- [ ] **Step 1: 0-1 `Environment.kt` を削除**

```bash
git rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/Environment.kt
```

- [ ] **Step 2: 0-1 `application.yaml` の `KTOR_ENV` 行を削除**

`application.yaml:2` の `  environment: "$KTOR_ENV:LOCAL"` 行を削除し、冒頭を以下にする(`port` は 8080 のまま):

```yaml
ktor:
  deployment:
    port: "$PORT:8080"
  application:
```

- [ ] **Step 3: 0-7 interface の戻り値を `Unit` 化**

`StockRegisterRepository.kt` を全文置き換え:

```kotlin
package net.brightroom.mindstock.application.repository.stock

import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement

interface StockRegisterRepository {
    /** stock_movements に 1 行 INSERT する。 */
    fun appendMovement(
        productId: ProductId,
        movement: StockMovement,
    )
}
```

- [ ] **Step 4: 0-7 実装から `rebindIdentity` と id 読み戻しを削除**

`StockRegisterDataSource.kt` を全文置き換え(`MovementId` / `MovementIdentity` の import 削除・`@file:OptIn` は維持):

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.infrastructure.datasource.schemas.StockMovementsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class StockRegisterDataSource(
    private val database: Database,
) : StockRegisterRepository {
    override fun appendMovement(
        productId: ProductId,
        movement: StockMovement,
    ) {
        transaction(database) {
            StockMovementsTable.insert {
                it[StockMovementsTable.productId] = productId()
                it[kind] = movement.kindColumn()
                it[quantity] = movement.quantity()
                it[occurredAt] = movement.occurredAt()
                it[actorResidentId] = movement.actor.id()
                it[note] = movement.note()
                if (movement is StockMovement.Correction) {
                    it[targetMovementId] = movement.target()
                    it[reason] = movement.reason()
                }
            }
        }
    }
}
```

- [ ] **Step 5: 0-7 テストの mockk スタブを `just Runs` に修正**

`StockRegisterServiceTest.kt` の以下 3 箇所(現座標 `:86 / :101 / :125`。着手時に再確認)を変更する。Unit 戻りの関数を `returns` でスタブするとコンパイルエラーになるため:

変更前(各箇所):
```kotlin
            every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } returns base
```
```kotlin
            every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } returns mockk(relaxed = true)
```

変更後(3 箇所すべて):
```kotlin
            every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } just Runs
```

ファイル冒頭の import に以下が無ければ追加する(着手時に既存 import を確認):
```kotlin
import io.mockk.Runs
import io.mockk.just
```

> `slot<StockMovement>()` の capture は `just Runs` でも機能するため、後続の `check(appended.captured is ...)` / `appended.captured.occurredAt shouldBe ...` 検証はそのまま通る。

- [ ] **Step 6: 残参照ゼロを確認**

```bash
grep -rn --include='*.kt' -e 'KTOR_ENV' -e 'configuration.Environment' backend/api/src
grep -rn --include='*.kt' 'rebindIdentity' backend
```

Expected: 両方とも出力なし(空)。

- [ ] **Step 7: backend:core のテストと backend:api のコンパイルを確認**

```bash
./gradlew :backend:core:test :backend:api:compileKotlin
```

Expected: PASS(`StockRegisterServiceTest` の replenish/consume/correct テストが `just Runs` で green。`appended.captured` の検証が引き続き通る)。

- [ ] **Step 8: コミット**

```bash
git add -A
git commit -m "chore: backend の死変数 KTOR_ENV と無駄な id 詰め直しを削除

- Environment enum(参照ゼロ)と application.yaml の environment 行を削除
- StockRegisterRepository.appendMovement の戻り値を Unit 化(呼び出し元は全て戻り値を破棄)
- StockRegisterDataSource.rebindIdentity を削除(採番 id の詰め直しは未使用)
- 対応テストの mockk スタブを just Runs に修正"
```

---

## Task 3: ビルドの罠除去(0-8)

**Files:**
- Modify: `backend/api/build.gradle.kts:86-88`

- [ ] **Step 1: 0-8 `tasks.check { dependsOn(integrationTest) }` を削除**

`backend/api/build.gradle.kts` の末尾にある以下ブロック(現座標 `:86-88`)を削除する:

```kotlin
tasks.check {
    dependsOn(integrationTest)
}
```

> `val integrationTest by tasks.registering(...)`(`:67-84`)の定義自体は **残す**(CI で `./gradlew :backend:api:integrationTest` として明示実行する受け皿)。

- [ ] **Step 2: DB 未起動で `:backend:api:build` が green になることを確認**

DB(Postgres)を起動していない状態で実行する:

```bash
./gradlew :backend:api:build
```

Expected: PASS(Task 0 Step 2 では赤だったものが green になる = 罠が解消)。

- [ ] **Step 3: コミット**

```bash
git add backend/api/build.gradle.kts
git commit -m "build: check から integrationTest 依存を外す

DB 無し環境で ./gradlew build が赤になる罠を解消。integrationTest は
CI job と明示実行でのみ走らせる(タスク定義自体は受け皿として維持)。"
```

---

## Task 4: frontend 死コード掃除(0-2 / 0-3 / 0-12)

**Files:**
- Delete: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/navigation/Route.kt`
- Delete: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomePreview.kt`
- Modify: `frontend/build.gradle.kts`(generateAuthConfig)
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthClient.kt:110-121`
- Modify: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/AuthClientTest.kt:27-32`
- Modify: `.github/workflows/ci.yml:73`

- [ ] **Step 1: 0-2 `Route.kt` を削除**

```bash
git rm frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/navigation/Route.kt
```

`core/navigation/` が空になったらディレクトリも削除(git は空ディレクトリを追跡しないので通常は不要)。

- [ ] **Step 2: 0-3 `StockHomePreview.kt` を削除**

```bash
git rm frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomePreview.kt
```

- [ ] **Step 3: 0-12 `generateAuthConfig` から AUDIENCE / POST_LOGOUT を除去**

`frontend/build.gradle.kts` の `generateAuthConfig` タスクで、以下 6 行を削除する(現座標 `:56 / :58 / :62 / :64 / :77 / :78`。着手時に再確認):

- `val postLogout = providers.environmentVariable("AUTH_POST_LOGOUT_REDIRECT_URI").orElse("http://localhost:8080/")`
- `val audience = providers.environmentVariable("AUTH_AUDIENCE")`
- `inputs.property("postLogout", postLogout)`
- `inputs.property("audience", audience)`
- `const val POST_LOGOUT_REDIRECT_URI = "${postLogout.get()}"`
- `const val AUDIENCE = "${audience.get()}"`

結果、生成される `AuthConfig` ブロックは以下になる(`clientId` / `projectId` は orElse 無し=必須なので維持):

```kotlin
                object AuthConfig {
                    const val ISSUER = "${issuer.get()}"
                    const val CLIENT_ID = "${clientId.get()}"
                    const val REDIRECT_URI = "${redirectUri.get()}"
                    const val PROJECT_ID = "${projectId.get()}"
                }
```

- [ ] **Step 4: 0-12 `AuthClient.endSessionUrl()` を削除**

`AuthClient.kt` の companion object 内 `endSessionUrl(...)` 関数(現座標 `:110-121`)を削除する。`buildAuthorizeUrl` は残す。削除後の companion object の末尾は `buildAuthorizeUrl` の `}` で閉じる形になる。

- [ ] **Step 5: 0-12 対応テストを削除**

`AuthClientTest.kt` の `@Test fun endSessionUrl_includes_id_token_hint()`(現座標 `:27-32`)を削除する。`buildAuthorizeUrl_includes_pkce_and_s256` テストは残す。

> spotless が未使用 import(`import io.kotest.matchers.shouldBe`)で落ちる場合のみ、同コミットでその import 行も削除する。

- [ ] **Step 6: 0-12 ci.yml の `AUTH_AUDIENCE` を削除**

`.github/workflows/ci.yml:73` の `          AUTH_AUDIENCE: ci-placeholder` 行を削除する。`AUTH_CLIENT_ID`(`:72`)/ `AUTH_PROJECT_ID`(`:74`)は generateAuthConfig が必須参照するため残す。

- [ ] **Step 7: 残参照ゼロを確認**

```bash
grep -rn --include='*.kt' -e 'core.navigation.Route' -e 'previewStocks' -e 'StockHomePreview' -e 'endSessionUrl' -e 'AuthConfig.AUDIENCE' -e 'POST_LOGOUT_REDIRECT_URI' frontend/src
grep -rn 'AUTH_AUDIENCE' .github
```

Expected: 両方とも出力なし(空)。

- [ ] **Step 8: frontend のコンパイルとテストを確認**

```bash
./gradlew :frontend:compileKotlinWasmJs :frontend:jsTest
```

Expected: PASS(`AuthClientTest` の `buildAuthorizeUrl` テストが green。生成 `AuthConfig` から AUDIENCE/POST_LOGOUT が消えてもコンパイルが通る)。

> `jsTest` 実行に AUTH_CLIENT_ID 等が必要なら、ローカルでは `.env.zitadel`(`mise run up` 生成)を読む。未起動なら `AUTH_CLIENT_ID=x AUTH_PROJECT_ID=x ./gradlew :frontend:compileKotlinWasmJs` でコンパイルのみ確認でも可。

- [ ] **Step 9: コミット**

```bash
git add -A
git commit -m "chore: frontend の参照ゼロ死コードを削除

- Route sealed interface(タブは AppShell の Tab enum で完結)
- StockHomePreview(preview ハーネス撤去の取り残し)
- AuthConfig の AUDIENCE/POST_LOGOUT_REDIRECT_URI 生成と AuthClient.endSessionUrl()
  (ログアウト未実装・参照ゼロ。実装時にセットで再追加)
- ci.yml の不要になった AUTH_AUDIENCE placeholder"
```

---

## Task 5: 未使用依存の一掃(0-10 / 0-11)

**Files:**
- Modify: `frontend/build.gradle.kts:21,22,24`
- Modify: `gradle/libs.versions.toml:21,62,65,66`
- Modify: `backend/api/build.gradle.kts:50-51`
- Modify: `domain/build.gradle.kts:17-19`
- Modify: `rpc/build.gradle.kts:25-29`

- [ ] **Step 1: 0-10 `material3-adaptive-navigation-suite` を削除**

- `frontend/build.gradle.kts:21` の `implementation(libs.material3.adaptive.navigation.suite)` 行を削除。
- `gradle/libs.versions.toml:65` の `material3-adaptive-navigation-suite = { ... }` 行を削除。

> `compose.adaptive`(frontend `:18` / libs `:63`)は `currentWindowAdaptiveInfo()` で使用中なので **残す**。

- [ ] **Step 2: 0-11(a) `navigation-compose` を削除**

- `frontend/build.gradle.kts:22` の `implementation(libs.navigation.compose)` 行を削除。
- `gradle/libs.versions.toml:21` の `navigation-compose = "2.9.2"`(version)行を削除。
- `gradle/libs.versions.toml:66` の `navigation-compose = { ... }`(library)行を削除。

- [ ] **Step 3: 0-11(b) `compose-ui-tooling-preview` を削除**

- `frontend/build.gradle.kts:24` の `implementation(libs.compose.ui.tooling.preview)` 行を削除。
- `gradle/libs.versions.toml:62` の `compose-ui-tooling-preview = { ... }` 行を削除。

- [ ] **Step 4: 0-11(c) backend:api の flyway `testImplementation` 重複を削除**

`backend/api/build.gradle.kts` の以下 2 行(現座標 `:50-51`)を削除:

```kotlin
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.database.postgresql)
```

> `implementation`(`:30-31`)から推移するため不要。`testFixturesImplementation` 側の flyway/postgres(`:38-40`)は隔離のため残す。postgres-jdbc は test ブロックに存在しないので触らない。

- [ ] **Step 5: 0-11(d) domain/rpc の jvmTest 向け `kotest-runner-junit5` を削除(条件付き)**

`domain/build.gradle.kts` の以下ブロック(現座標 `:17-19`)を削除:

```kotlin
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
```

`rpc/build.gradle.kts` の以下ブロック(現座標 `:25-29`)を削除:

```kotlin
        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
```

- [ ] **Step 6: 0-11(d) JVM テストが従来通り実行されることを検証(最重要)**

```bash
./gradlew :domain:jvmTest :rpc:jvmTest --rerun-tasks
```

Expected: PASS、かつ **テスト件数が Task 0 Step 3 のベースラインと一致**すること(`build/reports/tests/jvmTest/index.html` の total、または実行ログの件数で確認)。

**判定:**
- 件数一致 → 削除確定、次へ進む。
- **`NO-SOURCE` / `0 件` / テストが discover されない**場合 → 退行。`kotest-runner-junit5` は JVM 実行に必要だった。Step 5 の削除を **revert** するか、最小 JUnit5 ランナーへの差し替えが必要。差し替えは親プラン外の判断なので、ここで停止してユーザに報告・確認する。

- [ ] **Step 7: frontend のコンパイルを確認**

```bash
./gradlew :frontend:compileKotlinWasmJs
```

Expected: PASS(削除した 3 依存はいずれも参照ゼロのためコンパイル不変)。

- [ ] **Step 8: 残参照ゼロを確認**

```bash
grep -rn --include='*.kt' -e 'navigationsuite' -e 'NavigationSuiteScaffold' -e 'NavHost' -e 'rememberNavController' -e 'androidx.navigation' -e '@Preview' -e 'ui.tooling' frontend/src
grep -n -e 'navigation-compose' -e 'material3-adaptive-navigation-suite' -e 'compose-ui-tooling-preview' gradle/libs.versions.toml
```

Expected: 1 つめは空。2 つめも空(libs から 3 依存が消えたこと)。

- [ ] **Step 9: コミット**

```bash
git add -A
git commit -m "build: 参照ゼロの未使用依存を削除

- material3-adaptive-navigation-suite(シェルは独自実装。compose.adaptive は残す)
- navigation-compose(NavHost 不使用)
- compose-ui-tooling-preview(@Preview 不使用)
- backend:api の flyway testImplementation 重複(implementation から推移)
- domain/rpc の jvmTest kotest-runner-junit5(JVM テスト実行を件数で非退行確認済み)"
```

---

## Task 6: Makefile 撤去(0-9)

**Files:**
- Delete: `Makefile`

- [ ] **Step 1: 0-9 `Makefile` を削除**

```bash
git rm Makefile
```

> `mise.toml` が `up` / `down` を提供済み(`mise run up` / `mise run down`)。`clean` 相当(`down -v --rmi all`)の mise タスク追加と README 同期は D-8 のスコープ。本コミットでは撤去のみ。

- [ ] **Step 2: 参照ゼロを確認**

```bash
grep -rn -e 'make up' -e 'make down' -e 'make clean' -e 'Makefile' README.md .github docs/superpowers/specs docs/superpowers/plans/2026-06-12-phase0-cleanup.md
```

Expected: 本プラン/spec 内の言及以外に出力なし(README・CI からの参照ゼロ)。

- [ ] **Step 3: コミット**

```bash
git rm Makefile  # 既に Step 1 で実行済みなら不要
git commit -m "chore: Makefile を撤去し mise.toml に一本化

up/down は mise run up / mise run down で代替(初期化込み)。"
```

---

## Task 7: 全体検証(PR 前)

- [ ] **Step 1: DB 未起動で backend ビルドが green(0-8 最終確認)**

```bash
./gradlew :backend:api:build
```

Expected: PASS。

- [ ] **Step 2: 単体テスト一式**

```bash
./gradlew test
```

Expected: PASS(domain / shared / backend:core の単体テスト。0-5 / 0-7 の安全網が green)。

- [ ] **Step 3: domain/rpc JVM テストの非退行(0-11(d) 再確認)**

```bash
./gradlew :domain:jvmTest :rpc:jvmTest --rerun-tasks
```

Expected: PASS、件数が Task 0 Step 3 のベースラインと一致。

- [ ] **Step 4: frontend コンパイル**

```bash
./gradlew :frontend:compileKotlinWasmJs
```

Expected: PASS。

- [ ] **Step 5: spotless**

```bash
./gradlew spotlessCheck
```

Expected: PASS(未使用 import 等が残っていないこと)。

- [ ] **Step 6: 全削除シンボルの残参照ゼロ(総まとめ)**

```bash
grep -rn --include='*.kt' -e 'KTOR_ENV' -e 'configuration.Environment' backend/api/src
grep -rn --include='*.kt' -e 'core.navigation.Route' -e 'previewStocks' -e 'StockHomePreview' frontend/src
grep -rn --include='*.kt' -e 'isアーカイブ済' -e 'is有効' -e 'LocalTime.now' -e 'rebindIdentity' domain backend frontend shared
grep -rn --include='*.kt' -e 'navigationsuite' -e 'NavHost' -e '@Preview' -e 'endSessionUrl' -e 'AuthConfig.AUDIENCE' -e 'POST_LOGOUT_REDIRECT_URI' frontend/src
```

Expected: 全て出力なし(空)。

- [ ] **Step 7: PR 作成 → CI draft 一巡**

draft PR を作り、CI の `test-backend` / `test-frontend` / `integration-test` が全て green になることを確認する(0-12 の ci.yml 変更後も generateAuthConfig が CLIENT_ID/PROJECT_ID で通ること、integration-test が DB 起動込みで green であること)。

```bash
git push -u origin refactor/p0-cleanup
gh pr create --draft --title "refactor(p0): 無風の掃除(死コード/未使用依存/ビルドの罠)" --body "<本文>"
```

PR 本文に「挙動変更ゼロ」「正味 -250 行目安」「0-11(d) は JVM テスト件数で非退行確認済み」を明記する。

---

## Self-Review(プラン作成者による spec 突き合わせ)

- **spec coverage**: 0-1(Task2 S1-2)/ 0-2(Task4 S1)/ 0-3(Task4 S2)/ 0-4(Task1 S1)/ 0-5(Task1 S2-3)/ 0-6(Task1 S4)/ 0-7(Task2 S3-5)/ 0-8(Task3)/ 0-9(Task6)/ 0-10(Task5 S1)/ 0-11(Task5 S2-6)/ 0-12(Task4 S3-6)。全 12 タスク網羅。
- **placeholder scan**: コード/コマンドは全て実体を記載。`<本文>` は PR 本文の人手記入箇所として意図的(プラン外の運用テキスト)。
- **type consistency**: `appendMovement` の戻り値 Unit 化は interface(Task2 S3)/ impl(S4)/ test(S5)で一貫。`just Runs` の import 追加も明記。`AuthConfig` の残存 const 4 つは Task4 S3 で確定。
- **0-11(d) の安全網**: Task0 S3 で件数ベースライン取得 → Task5 S6 / Task7 S3 で照合、の依存を明示。
