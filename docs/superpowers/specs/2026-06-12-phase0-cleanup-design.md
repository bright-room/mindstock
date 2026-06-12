# フェーズ 0: 無風の掃除 — 詳細 spec

> 親: [2026-06-12-refactoring-master-plan.md](../plans/2026-06-12-refactoring-master-plan.md) のフェーズ 0。
> 本書はそのタスク表(0-1〜0-12)を、現物 Read で座標を再確認した上で before/after・残参照ゼロ根拠・検証手順まで展開した実装 spec。座標は **2026-06-12 / main(`342b2807`)** で確認済み。

## 狙い

デッドコード・死変数・ドキュメントずれ・ビルドの罠を先に消し、以降のフェーズ(1〜5)の diff を綺麗にする。**挙動変更ゼロ**(削除されるのは全て参照ゼロのコードか、ビルドタスクの依存関係のみ)。

## スコープ

- **やる**: 0-1〜0-12 の 12 タスク。デッドコード削除・未使用 Gradle 依存削除・`tasks.check`→`integrationTest` 依存の除去・Makefile 撤去。
- **やらない(他フェーズ)**: README の環境変数リファレンス新設(D-8)/ `PORT` デフォルト 8090 化(D-12)/ mise への `clean` タスク追加(D-8 の mise・README 同期で扱う)/ ログアウト機能の実装(プロダクト判断)。
- **non-goal**: リファクタや命名改善。本フェーズは「消す」だけ。残す側のコードには触れない。

## 親プランからの補正(現物突き合わせで判明した差分)

実装エージェントが誤った座標・前提で着手しないよう、親プラン記述と現物の差分をここに明示する。

1. **0-7**: 呼び出し元(Service)が戻り値を無視するのは親プラン通り正しい。ただし **テスト 3 箇所の mockk スタブが Unit 化でコンパイル不能になる** → `just Runs` への置換が必須(下記 0-7 参照)。
2. **0-11**: 親プランの「postgres-jdbc の testImplementation 重複」は **誤り**。`backend/api/build.gradle.kts` の test ブロックに postgres-jdbc は無い。重複は flyway 2 行(`:50-51`)のみ。
3. **0-11**: 親プランの「domain・rpc の jvmTest ソースセット **自体が不存在**」は **誤り**。`kmp-shared` convention(`build-logic/src/main/kotlin/net.brightroom.mindstock.kmp-shared.gradle.kts:11`)が `jvm()` ターゲットを宣言しており jvmTest は実在する。`kotest-runner-junit5` は commonTest spec を JVM ターゲットで走らせる JUnit5 エンジンを供給している可能性があるため、**検証付き条件削除**とする(下記 0-11)。
4. **0-12**: `endSessionUrl()` 削除に伴い、**対応テスト `AuthClientTest.kt:27-32` も道連れ削除**が必要。
5. **0-6**: 親プランは「8 行削除」だが、`LocalTime.kt` はこの 1 関数だけのファイル。関数だけ消すと orphan import が残る → **ファイルごと削除**。
6. **0-4 / 0-5**: enum メソッド削除時、`;`(enum body セパレータ)の後始末が要る。機械的に行削除すると dangling `;` でコンパイルエラー → 下記の after を正とする。

---

## タスク詳細

### 0-1 `Environment` enum と `KTOR_ENV` 死変数の削除

**根拠**: `Environment` enum は定義以外に参照ゼロ(`grep -rn 'Environment' backend/api/src` で `configuration/Environment.kt` 自身のみ)。`KTOR_ENV` を読むのは `application.yaml:2` だけで、その値を使う側(= `Environment`)が死んでいる。

**操作**:
1. ファイル削除: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/Environment.kt`(14 行・全削除)
2. `backend/api/src/main/resources/application.yaml:2` の `  environment: "$KTOR_ENV:LOCAL"` 行を削除。

after(`application.yaml` 冒頭):

```yaml
ktor:
  deployment:
    port: "$PORT:8080"
  application:
```

> `port` はこのフェーズでは `8080` のまま(8090 化は D-12)。

**残参照確認**: `grep -rn 'KTOR_ENV\|Environment\b' backend/api/src` が空。

---

### 0-2 `Route` sealed interface の削除

**根拠**: タブ切替は `app/shell/` の Tab enum で完結。`core.navigation.Route` / `Route.Stock` 等の参照ゼロ。

**操作**: ファイル削除 `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/navigation/Route.kt`(14 行・全削除)。

**残参照確認**: `grep -rn 'core.navigation.Route\|Route\.Stock\|Route\.Shop\|Route\.Activity\|Route\.Profile' frontend/src` が空。

> `core/navigation/` ディレクトリが空になる場合はディレクトリごと削除。

---

### 0-3 `StockHomePreview.kt`(preview ハーネス残骸)の削除

**根拠**: 認証/backend 無しで在庫ホームを描く `previewStocks()` のサンプルデータ。ハーネス撤去時の取り残しで参照ゼロ(`previewStocks` / `StockHomePreview` の参照は自ファイルのみ)。preview ハーネス再現は memory `fidelity-verify-loop-mechanics` 通り webMain `PreviewHarness.kt` で別途行う方式に移行済み。

**操作**: ファイル削除 `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomePreview.kt`(111 行・全削除)。

**残参照確認**: `grep -rn 'previewStocks\|StockHomePreview' frontend/src` が空。

---

### 0-4 `ProductStatus.isアーカイブ済()` の削除

**根拠**: 参照ゼロ(`grep -rn 'isアーカイブ済' domain backend frontend` が定義行のみ)。testing.md「単に値を返すだけのアクセサはテスト不要」に該当する死メソッド。

**操作**: `domain/.../inventory/product/ProductStatus.kt` を以下に。

after(全文):

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable

@Serializable
enum class ProductStatus {
    採用中,
    アーカイブ済,
}
```

> メソッドと共に enum body セパレータ `;` も削除する(残すとコンパイルエラー)。

---

### 0-5 `InvitationValidity.is有効()` の削除(`Invitation.usable()` へインライン)

**根拠**: `is有効()` の唯一の呼び出し元は `Invitation.kt:14` の `usable()`。インライン化すれば `is有効()` は死ぬ。enum `InvitationValidity` 自体は永続化・hydration で多数使用中のため残す。

**操作**:
1. `domain/.../household/invitation/Invitation.kt:14` を変更:

```kotlin
    fun usable(): Boolean = validity == InvitationValidity.有効
```

2. `domain/.../household/invitation/InvitationValidity.kt` を以下に:

```kotlin
package net.brightroom.mindstock.domain.model.household.invitation

import kotlinx.serialization.Serializable

@Serializable
enum class InvitationValidity {
    有効,
    無効,
}
```

**残参照確認**: `grep -rn 'is有効' domain backend frontend` が空。`InvitationTest.kt:12-13` の `usable()` 検証は挙動不変で green を維持。

---

### 0-6 `LocalTime.now()` ext の削除(ファイルごと)

**根拠**: `LocalTime.Companion.now()` は全モジュール参照ゼロ(`grep -rn 'LocalTime.now\|LocalTime.Companion'` が定義行のみ)。ファイルはこの 1 関数のみで構成。

**操作**: ファイル削除 `shared/src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlinx/datetime/LocalTime.kt`(10 行・全削除)。

> 関数だけ消すと `LocalDateTime` / `LocalTime` / `TimeZone` の orphan import が残るため、ファイルごと消す。`LocalDateTime.now()` ext(同 extensions 配下の別ファイル)は使用中なので触らない。

**残参照確認**: `grep -rn 'LocalTime.now' domain backend frontend shared` が空。

---

### 0-7 `appendMovement` の戻り値 `Unit` 化と `rebindIdentity` 削除

**根拠**: `StockRegisterDataSource.appendMovement` は採番後の id で `Persisted` に詰め直した `StockMovement` を返すが、**呼び出し元 3 箇所(`StockRegisterService.kt:39,53,67`)は全て戻り値を捨てている**。`rebindIdentity`(約 25 行)は完全に無駄仕事。

**操作**:

1. interface `backend/core/.../application/repository/stock/StockRegisterRepository.kt`:

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

2. 実装 `backend/core/.../infrastructure/datasource/stock/StockRegisterDataSource.kt`:

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

変更点: `rebindIdentity` private 関数と `MovementId` / `MovementIdentity` の import を削除。`insert{} get StockMovementsTable.id` の id 読み戻しも不要になり削除。`@file:OptIn(ExperimentalUuidApi)` は `productId()`(Uuid)で引き続き必要なので残す。

3. **Service は変更不要**(`:39,53,67` は元から戻り値を捨てている)。

4. **テスト修正(必須)** `backend/core/src/test/.../application/service/stock/StockRegisterServiceTest.kt`:
   Unit 戻りの関数を `returns` でスタブするとコンパイルエラーになるため、3 箇所を `just Runs` に置換する。
   - `:86` `... appendMovement(product.id, capture(appended)) } returns base` → `} just Runs`
   - `:101` `... } returns mockk(relaxed = true)` → `} just Runs`
   - `:125` `... } returns mockk(relaxed = true)` → `} just Runs`
   - import に `io.mockk.Runs` / `io.mockk.just` が無ければ追加する。
   - `slot<StockMovement>()` の capture は `just Runs` でも機能するため、後続の `check(appended.captured is ...)` / `appended.captured.occurredAt shouldBe ...` の検証はそのまま green。

**残参照確認**: `grep -rn 'rebindIdentity' backend` が空。`appendMovement` の参照は interface / impl / Service 3 箇所 / テスト 3 箇所のみ。

---

### 0-8 `tasks.check { dependsOn(integrationTest) }` の削除

**根拠**: `check`→`integrationTest` 依存により、DB の無い環境で `./gradlew build`(= `check` を含む)が必ず赤になる。integrationTest は CI job と明示実行(`./gradlew :backend:api:integrationTest`)でのみ走れば十分。

**操作**: `backend/api/build.gradle.kts:86-88` の以下ブロックを削除:

```kotlin
tasks.check {
    dependsOn(integrationTest)
}
```

`val integrationTest by tasks.registering(...)`(:67-84)の **定義自体は残す**(CI で明示実行する受け皿)。

**検証**: DB 未起動で `./gradlew :backend:api:build` が green になること(削除前は赤)。

---

### 0-9 `Makefile` の削除(mise.toml へ一本化)

**根拠**: `mise.toml` が `up` / `down` タスクを既に提供(`mise run up` で初期化込み、`mise run down` でデータ保持停止・全消しは `docker compose down -v`)。`Makefile` を参照するのは親プラン本文のみで、CI・README・コードからの参照ゼロ。

**操作**: ファイル削除 `Makefile`(13 行・全削除)。

> `Makefile` の `clean`(`down -v --rmi all --remove-orphans`)に対応する mise タスクは現状無いが、mise・README の同期は D-8 のスコープ。本フェーズでは Makefile 撤去のみ行い、`clean` 相当タスクの追加は D-8 に委ねる(直書きコマンドで代替可能なため挙動退行なし)。

**残参照確認**: `grep -rn 'make up\|make down\|make clean\|Makefile' README.md .github docs` が(本 spec / 親プラン以外で)空。

---

### 0-10 未使用依存 `material3-adaptive-navigation-suite` の削除

**根拠**: `NavigationSuiteScaffold` / `navigationsuite` の参照ゼロ。シェルは独自 `WideShell` / `BottomNav` 実装。同居する `compose.adaptive` は `currentWindowAdaptiveInfo()`(`app/shell/AppShell.kt:8,33`)で使用中のため **残す**。

**操作**:
1. `frontend/build.gradle.kts:21` の `implementation(libs.material3.adaptive.navigation.suite)` 行を削除。
2. `gradle/libs.versions.toml:65` の `material3-adaptive-navigation-suite = { ... }` 行を削除。

**残参照確認**: `grep -rn 'navigationsuite\|NavigationSuiteScaffold' frontend/src` が空。`currentWindowAdaptiveInfo` は残ること。

---

### 0-11 未使用依存の一掃

**根拠と操作**(全て参照ゼロを裏取り済み):

**(a) `navigation-compose`** — `NavHost` / `rememberNavController` / `androidx.navigation` 参照ゼロ:
- `frontend/build.gradle.kts:22` の `implementation(libs.navigation.compose)` 削除
- `gradle/libs.versions.toml:21` の `navigation-compose = "2.9.2"`(version)削除
- `gradle/libs.versions.toml:66` の `navigation-compose = { ... }`(library)削除

**(b) `compose-ui-tooling-preview`** — `@Preview` / `ui.tooling` 参照ゼロ:
- `frontend/build.gradle.kts:24` の `implementation(libs.compose.ui.tooling.preview)` 削除
- `gradle/libs.versions.toml:62` の `compose-ui-tooling-preview = { ... }` 削除

**(c) `backend/api` の flyway `testImplementation` 重複** — `implementation`(`:30-31`)から推移するため不要:
- `backend/api/build.gradle.kts:50` の `testImplementation(libs.flyway.core)` 削除
- `backend/api/build.gradle.kts:51` の `testImplementation(libs.flyway.database.postgresql)` 削除
- `testFixturesImplementation` 側の flyway / postgres(`:38-40`)は **隔離のため残す**(親プラン通り)。
- ※ 親プランの「postgres-jdbc の testImplementation 重複」は誤り。test ブロックに postgres-jdbc は無いので触らない。

**(d) `domain` / `rpc` の jvmTest 向け `kotest-runner-junit5`(検証付き条件削除)**:

`kmp-shared` convention が `jvm()` を宣言しているため jvmTest ソースセットは実在する。`kotest-runner-junit5` は commonTest spec を JVM ターゲットで実行する JUnit5 エンジンを供給している可能性がある。よって以下の手順で **削除 → 検証 → 退行時は撤回** とする:

1. 削除:
   - `domain/build.gradle.kts:17-19` の `jvmTest.dependencies { implementation(libs.kotest.runner.junit5) }` ブロック
   - `rpc/build.gradle.kts:25-29` の `jvmTest { dependencies { implementation(libs.kotest.runner.junit5) } }` ブロック
2. 検証: `./gradlew :domain:jvmTest :rpc:jvmTest --rerun-tasks` を実行し、**テストが discover され実行される**こと(各モジュールのテスト件数が削除前と一致)を確認する。
3. 判定:
   - テストが従来通り実行されれば削除を確定。
   - **「0 件 / NO-SOURCE / UP-TO-DATE で素通り」になったら退行**。削除を撤回するか、`kotest-runner-junit5` を `kotlin-test-junit5` 相当の最小 JUnit5 ランナーに差し替えて JVM 実行を復活させる(差し替えが必要なら親プラン外の判断としてユーザに確認)。

> この (d) のみ「無条件削除」ではない。`./gradlew test` が緑でも JVM テストが静かに 0 件化する罠を防ぐため、件数の before/after を必ず取る。

---

### 0-12 frontend 死定数 `AuthConfig` 生成とログアウト残骸の削除

**根拠**: `AuthConfig.AUDIENCE` / `AuthConfig.POST_LOGOUT_REDIRECT_URI` は参照ゼロの死定数。`AuthClient.endSessionUrl()` も未呼出(ログアウト機能は未実装)。ログアウト実装時にセットで再追加する方針(親プラン決定事項)。

**操作**:

1. `frontend/build.gradle.kts` の `generateAuthConfig` タスクから AUDIENCE / POST_LOGOUT を除去:
   - `:56` `val postLogout = providers.environmentVariable("AUTH_POST_LOGOUT_REDIRECT_URI").orElse("http://localhost:8080/")` 削除
   - `:58` `val audience = providers.environmentVariable("AUTH_AUDIENCE")` 削除
   - `:62` `inputs.property("postLogout", postLogout)` 削除
   - `:64` `inputs.property("audience", audience)` 削除
   - `:77` `const val POST_LOGOUT_REDIRECT_URI = "${postLogout.get()}"` 削除
   - `:78` `const val AUDIENCE = "${audience.get()}"` 削除
   - 残す const: `ISSUER` / `CLIENT_ID` / `REDIRECT_URI` / `PROJECT_ID`。`clientId`(`:57`)/ `projectId`(`:59`)は `orElse` 無し = 必須プロバイダなので維持。

   after(生成される `AuthConfig` の中身):

   ```kotlin
   object AuthConfig {
       const val ISSUER = "..."
       const val CLIENT_ID = "..."
       const val REDIRECT_URI = "..."
       const val PROJECT_ID = "..."
   }
   ```

2. `frontend/src/commonMain/.../auth/AuthClient.kt:110-121` の `endSessionUrl(...)` 関数を削除(companion object 内。`buildAuthorizeUrl` は残す)。

3. `frontend/src/commonTest/.../auth/AuthClientTest.kt:27-32` の `@Test fun endSessionUrl_includes_id_token_hint()` を削除(`buildAuthorizeUrl` のテストは残す)。

4. `.github/workflows/ci.yml:73` の `AUTH_AUDIENCE: ci-placeholder` 行を削除。`generateAuthConfig` が `AUTH_AUDIENCE` を読まなくなるため不要。`AUTH_CLIENT_ID`(`:72`)/ `AUTH_PROJECT_ID`(`:74`)は生成タスクが必須参照するため **残す**。

**残参照確認**: `grep -rn 'endSessionUrl\|POST_LOGOUT_REDIRECT_URI\|AuthConfig.AUDIENCE' frontend/src` が空。`grep -rn 'AUTH_AUDIENCE' .github` が空。

> `AuthClientTest.kt` の `import io.kotest.matchers.shouldBe`(`:3`)は本タスク前から未使用の可能性があるが、本フェーズのスコープ外として触らない(spotless が落ちる場合のみ同コミットで除去)。

---

## コミット分割案(1 ブランチ `refactor/p0-cleanup` / 1 PR・小コミット)

| コミット | 含むタスク | 検証 |
|---|---|---|
| 1. domain 死コード掃除 | 0-4, 0-5, 0-6 | `./gradlew :domain:test :shared:compileKotlinMetadata` |
| 2. backend 死コード掃除 | 0-1, 0-7 | `./gradlew :backend:core:test :backend:api:compileKotlin`(+ 0-7 テスト green) |
| 3. ビルドの罠除去 | 0-8 | DB 未起動で `./gradlew :backend:api:build` green |
| 4. frontend 死コード掃除 | 0-2, 0-3, 0-12 | `./gradlew :frontend:compileKotlinWasmJs :frontend:jsTest` |
| 5. 未使用依存の一掃 | 0-10, 0-11 | `./gradlew :frontend:compileKotlinWasmJs` + 0-11(d) の件数確認 |
| 6. Makefile 撤去 | 0-9 | 参照 grep ゼロ |

> 順序は「層が深い順」。各コミット単体でコンパイル/テストが通る粒度を保つ。コミットメッセージに issue/PR 番号を書かない(working agreement)。

## 全体検証(PR 前)

1. `./gradlew :backend:api:build`(**DB 未起動で** green = 0-8 の確認を兼ねる。削除前は赤)
2. `./gradlew test`(domain / shared / backend の単体テスト green。0-5 / 0-7 のテストが安全網)
3. `./gradlew :domain:jvmTest :rpc:jvmTest --rerun-tasks`(0-11(d): テスト件数が削除前と一致=JVM 実行が生きている)
4. `./gradlew :frontend:compileKotlinWasmJs`(frontend のコンパイル。フルビルドは OOM のため使わない — memory `local-build-tips`)
5. `./gradlew spotlessCheck`
6. 削除シンボルの残参照ゼロ:
   ```
   grep -rn 'KTOR_ENV\|configuration.Environment' backend/api/src
   grep -rn 'core.navigation.Route\|previewStocks\|StockHomePreview' frontend/src
   grep -rn 'isアーカイブ済\|is有効\|LocalTime.now\|rebindIdentity' domain backend frontend shared
   grep -rn 'navigationsuite\|NavHost\|@Preview\|endSessionUrl\|AuthConfig.AUDIENCE\|POST_LOGOUT_REDIRECT_URI' frontend/src
   ```
   いずれも空であること。
7. CI(draft PR で一巡): `test-backend` / `test-frontend` / `integration-test` が green(0-12 の ci.yml 変更後も generateAuthConfig が CLIENT_ID/PROJECT_ID で通ること)。

## リスクと撤退

- リスクは最小(削除対象は全て参照ゼロ or ビルドタスク依存)。**唯一の非自明点は 0-11(d)** で、ここは件数 before/after を取る手順で罠を封じる。
- 撤退単位はコミット。あるコミットで想定外の波及(コンパイル/テスト赤)が出たら、そのコミットのみ revert して切り離す。
- 規模目安: 約 -250 行(本体)。挙動変更ゼロ。

## 関連

- plan: [2026-06-12-refactoring-master-plan.md](../plans/2026-06-12-refactoring-master-plan.md)(フェーズ 0 / 実行プロトコル / 決定事項)
- 付録: [2026-06-12-env-inventory.md](../plans/2026-06-12-env-inventory.md)(0-1 / 0-12 が解消する env 行)
- memory: `local-build-tips`(frontend フルビルド OOM 回避)/ `fidelity-verify-loop-mechanics`(preview ハーネス再現方式 — 0-3 の前提)
