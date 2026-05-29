# `:backend:api` Ktor 構成見直し Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:backend:api` モジュールの Ktor 構成(plugin / Routing / Auth / Error handling)を spec 2026-05-29-ktor-configuration-redesign-design.md に従って再構築する。

**Architecture:**
- 例外 throw を全廃し、RPC service のシグネチャを `RpcResult<T, RpcError>` 戻り値ベースに移行
- 自作 `MindstockAuthPlugin` で JWT 検証と Session 構築を行い、ktor-auth を撤去
- Controller を Factory パターンで DI 化し、`MindstockSession` を per-connection で注入
- 死コード化していた `StatusPages` / `ExposedTransactionPlugin` / `CallLogging` / Koin を削除

**Tech Stack:** Ktor 3 / kotlinx-rpc 0.10.2 / Exposed JDBC v1 / kotlinx-serialization / auth0 java-jwt + jwks-rsa / Kotest / mockk

**Spec:** `docs/superpowers/specs/2026-05-29-ktor-configuration-redesign-design.md`

**Implementation philosophy:**
- Phase ごとに `./gradlew :backend:api:check` (= unit + integration tests) を green に保つ
- Phase 終わりごとに 1 commit
- 各 task は 2-5 分の粒度。step 単位で動かす

---

## File Map(変更ファイル一覧)

### 作成

- `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/RpcResult.kt` — sealed `RpcResult<T, E>`
- `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/RpcError.kt` — sealed `RpcError`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPlugin.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPlugin.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/JwksKeyProvider.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserControllerFactory.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicControllerFactory.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdControllerFactory.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogControllerFactory.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductControllerFactory.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockControllerFactory.kt`
- `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPluginTest.kt`
- `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPluginTest.kt`
- `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/transaction/TxWithGuardTest.kt`

### 変更

- `backend/api/build.gradle.kts` — Koin 削除、CallLogging/CallId/DoubleReceive 削除
- `backend/api/src/main/resources/application.yaml` — 削除モジュールを除外
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt` — `tx()` を `txWithGuard()` 化
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt` — factory + 自作 plugin に置換
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt` — Controller factory provider 追加
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WsBearerTokenExtractor.kt` — `extractRaw(call): String?` 新設、`extract` 削除
- `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/*RpcService.kt` (6 ファイル) — 戻り値を `RpcResult<T, RpcError>` 化
- `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/**/*Controller.kt` (6 ファイル) — `throw` → `Err` 返却、`call: ApplicationCall` → `session: MindstockSession`
- `backend/api/src/test/kotlin/.../presentation/rpc/**/*ControllerTest.kt` (6 ファイル) — シグネチャ変更追従
- `backend/api/src/test/kotlin/.../e2e/E2eTestSupport.kt` — 必要に応じて更新
- `backend/api/src/test/kotlin/.../e2e/**/*E2eTest.kt` (6 ファイル) — `shouldThrowAny` → `RpcResult.Err` アサーション

### 削除

- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/ExposedTransactionPlugin.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/TransactionConfiguration.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/logging/LoggingConfiguration.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/error/RpcExceptions.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/error/ErrorConfiguration.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/AuthConfiguration.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/JwtAuthConfiguration.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockPrincipal.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/ActorResolver.kt`
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/CurrentCall.kt`

---

# Phase 1: 死コード削除

`StatusPages` / `ErrorConfiguration.kt` は Phase 2 で `throw` 廃止後に削除する。本 Phase は他の死コードのみ。

### Task 1.1: Koin の gradle dependency を削除

**Files:**
- Modify: `backend/api/build.gradle.kts`

- [ ] **Step 1: Koin 行を削除**

`backend/api/build.gradle.kts` の以下の行を削除:

```kotlin
    implementation(libs.koin.ktor)
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 1.2: ExposedTransactionPlugin と TransactionConfiguration.kt を削除

`tx()` ヘルパー(`Transaction.kt`)は phase 2 で書き換えるためここでは触らない。

**Files:**
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/ExposedTransactionPlugin.kt`
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/TransactionConfiguration.kt`
- Modify: `backend/api/src/main/resources/application.yaml`

- [ ] **Step 1: 2 ファイル削除**

Run:
```bash
rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/ExposedTransactionPlugin.kt
rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/TransactionConfiguration.kt
```

- [ ] **Step 2: application.yaml から transactionConfigure module を削除**

`backend/api/src/main/resources/application.yaml` の以下の行を削除:

```yaml
      - "net.brightroom.mindstock.configuration.transaction.TransactionConfigurationKt.transactionConfigure"
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 1.3: LoggingConfiguration.kt を削除して関連 dep を整理

**Files:**
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/logging/LoggingConfiguration.kt`
- Modify: `backend/api/build.gradle.kts`
- Modify: `backend/api/src/main/resources/application.yaml`

- [ ] **Step 1: LoggingConfiguration.kt 削除**

Run:
```bash
rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/logging/LoggingConfiguration.kt
```

- [ ] **Step 2: build.gradle.kts から DoubleReceive / CallLogging / CallId の依存削除**

`backend/api/build.gradle.kts` の以下の行を削除:

```kotlin
    implementation(ktorLib.server.doubleReceive)
    implementation(ktorLib.server.callId)
    implementation(ktorLib.server.callLogging)
```

- [ ] **Step 3: application.yaml から loggingConfigure module を削除**

```yaml
      - "net.brightroom.mindstock.configuration.logging.LoggingConfigurationKt.loggingConfigure"
```

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 1.4: Phase 1 全体テスト + commit

- [ ] **Step 1: 全テスト実行**

Run: `./gradlew :backend:api:check`
Expected: BUILD SUCCESSFUL (integration テスト含む)

- [ ] **Step 2: commit**

Run:
```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(api): 死コードの Ktor plugin / dep を削除

- Koin: コード使用無しの死蔵 dep を削除(Ktor 3 native DI 採用済)
- ExposedTransactionPlugin: WS RPC に発火しない(tx() のみが実体)
- LoggingConfiguration (CallLogging/CallId/DoubleReceive): WS upgrade 時に 1 回しか
  発火せず RPC メソッド粒度のログが取れない。後続 phase で tx() に統合する

ref: docs/superpowers/specs/2026-05-29-ktor-configuration-redesign-design.md §2

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 2: `RpcResult` / `RpcError` 導入と throw の廃止

### Task 2.1: `:rpc` モジュールに `RpcResult` と `RpcError` を追加

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/RpcResult.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/RpcError.kt`

- [ ] **Step 1: RpcResult.kt を作成**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.serialization.Serializable

/**
 * RPC メソッドの戻り値共通型。成功 [Ok] と失敗 [Err] の sealed 二択。
 *
 * クライアント側は `when (r) { is Ok -> ...; is Err -> ... }` で網羅性検証可能。
 * 例外を throw せず本型を返す前提のため、エラーフィールド (NotFound.id 等) は
 * `@Serializable` 経由で完全に保持される。
 */
@Serializable
sealed interface RpcResult<out T, out E> {
    @Serializable
    data class Ok<T>(
        val value: T,
    ) : RpcResult<T, Nothing>

    @Serializable
    data class Err<E>(
        val error: E,
    ) : RpcResult<Nothing, E>
}
```

- [ ] **Step 2: RpcError.kt を作成**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.serialization.Serializable

/**
 * API 全体で共有する RPC エラー語彙。
 *
 * read/write を分けず、ありうるエラー集合の和集合を持つ。「この read メソッドは
 * BadRequest を返さない」という型レベル保証は失うが、クライアントは when の
 * 網羅性検証で新しい variant の追加に必ず気付ける。
 */
@Serializable
sealed interface RpcError {
    /** 認証失敗 / トークン期限切れ / Principal 未解決 等。 */
    @Serializable
    data class Unauthorized(
        val reason: String,
    ) : RpcError

    /** 集約 resolve 失敗(例: Repository.findById() が null)。 */
    @Serializable
    data class NotFound(
        val resource: String,
        val id: String,
    ) : RpcError

    /** 入力検証エラー。 */
    @Serializable
    data class BadRequest(
        val field: String,
        val reason: String,
    ) : RpcError

    /** 競合(重複登録 等)。 */
    @Serializable
    data class Conflict(
        val reason: String,
    ) : RpcError

    /** 想定外のサーバエラー。クライアントにスタックトレースは漏らさない。 */
    @Serializable
    data class Internal(
        val reason: String,
    ) : RpcError
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :rpc:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

### Task 2.2: `tx()` を `RpcResult` 返却 + 想定外例外を `Err(Internal)` 化する形に変更

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt`

- [ ] **Step 1: Transaction.kt を以下に置換**

```kotlin
@file:Suppress("DEPRECATION")

package net.brightroom.mindstock.configuration.transaction

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

private val logger = KotlinLogging.logger {}

/**
 * RPC message-scoped transaction boundary。
 *
 * 仕様変更(2026-05-29 ktor-restructure):
 * - 例外 throw ベースから [RpcResult] 戻り値ベースに移行。
 * - 想定外の例外は [RpcError.Internal] に変換する(client にスタックトレースは漏らさない)。
 * - [CancellationException] は伝播させる(coroutine cancellation 仕様)。
 * - [supervisorScope] は kRPC server scope へのエラー leak 防止のため維持。
 *
 * Phase 4 で `session: MindstockSession` 引数が追加され、`session.exp` チェックが組み込まれる。
 */
suspend fun <T> tx(
    database: Database,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> =
    try {
        supervisorScope {
            newSuspendedTransaction(db = database) { block() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC" }
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: 大量のコンパイルエラー(Controller がまだ古いシグネチャを使っているため期待通り)。これらは Task 2.3 以降で順次解消する

### Task 2.3: `UserPublicRpcService` interface を `RpcResult` 化

**Files:**
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/UserPublicRpcService.kt`

- [ ] **Step 1: interface を更新**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User

/**
 * JWT 検証は通すが User 未登録でも通る RPC。新規ユーザー登録のみ。
 * AuthIdentity は Principal から取得するため引数では受け取らない(なりすまし防止)。
 */
@Rpc
interface UserPublicRpcService {
    suspend fun register(displayName: DisplayName): RpcResult<User, RpcError>
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :rpc:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

### Task 2.4: `UserPublicController` を新シグネチャに更新

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicController.kt`

- [ ] **Step 1: Controller を以下に置換**

```kotlin
package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockPrincipal
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserPublicRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserPublicController(
    private val userRegisterService: UserRegisterService,
    private val call: ApplicationCall,
    private val database: Database,
) : UserPublicRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<User, RpcError> =
        tx(database) {
            val principal = call.principal<MindstockPrincipal>()
                ?: return@tx RpcResult.Err(RpcError.Unauthorized(reason = "missing principal"))
            RpcResult.Ok(userRegisterService.register(principal.authIdentity, displayName))
        }
}
```

- [ ] **Step 2: 既存テストを更新**

`backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicControllerTest.kt` を確認し、`UnauthorizedException` を throw する期待 → `RpcResult.Err(RpcError.Unauthorized(...))` を返す期待 に書き換える。`tx<Any?>(any(), any())` の mock は `tx<User, RpcError>(any(), any())` に対応する型に書き換える。

```kotlin
// Before:
// shouldThrow<UnauthorizedException> { impl.register(DisplayName("X")) }
// After:
val result = impl.register(DisplayName("X"))
result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
result.error.shouldBeInstanceOf<RpcError.Unauthorized>()
```

`mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")` の `tx` シグネチャは戻り値型が変わったので以下に変更:

```kotlin
coEvery {
    net.brightroom.mindstock.configuration.transaction
        .tx<User, RpcError>(any(), any())
} coAnswers {
    val block = arg<suspend () -> RpcResult<User, RpcError>>(1)
    block()
}
```

成功系テストの assertion も `result shouldBe RpcResult.Ok(user)` 形式に。

- [ ] **Step 3: テスト実行**

Run: `./gradlew :backend:api:test --tests "*UserPublicControllerTest"`
Expected: PASS

### Task 2.5: `UserRpcService` interface と `UserController` を `RpcResult` 化

**Files:**
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/UserRpcService.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserController.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserControllerTest.kt`

- [ ] **Step 1: UserRpcService.kt を更新**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.user.DisplayName

@Rpc
interface UserRpcService {
    suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError>
}
```

- [ ] **Step 2: UserController.kt を更新**

```kotlin
package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserController(
    private val userRegisterService: UserRegisterService,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
    private val database: Database,
) : UserRpcService {
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> =
        tx(database) {
            userRegisterService.rename(actor, displayName)
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 3: UserControllerTest を更新**

`impl.rename(newName)` の戻り値を `RpcResult.Ok(Unit)` で assert する形に変更。`tx` mock のシグネチャを `tx<Unit, RpcError>(any(), any())` に。

- [ ] **Step 4: テスト実行**

Run: `./gradlew :backend:api:test --tests "*UserControllerTest"`
Expected: PASS

### Task 2.6: `HouseholdRpcService` と `HouseholdController` を `RpcResult` 化

**Files:**
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/HouseholdRpcService.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdController.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdControllerTest.kt`

- [ ] **Step 1: HouseholdRpcService.kt を更新**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.UserId

@Rpc
interface HouseholdRpcService {
    suspend fun findOf(): RpcResult<Household?, RpcError>

    suspend fun create(): RpcResult<Household, RpcError>

    suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError>

    suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ): RpcResult<Unit, RpcError>
}
```

- [ ] **Step 2: HouseholdController.kt を更新**

```kotlin
package net.brightroom.mindstock.presentation.rpc.household

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.rpc.HouseholdRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class HouseholdController(
    private val householdService: HouseholdService,
    private val householdRegisterService: HouseholdRegisterService,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
    private val database: Database,
) : HouseholdRpcService {
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun findOf(): RpcResult<Household?, RpcError> =
        tx(database) { RpcResult.Ok(householdService.findOf(actor)) }

    override suspend fun create(): RpcResult<Household, RpcError> =
        tx(database) { RpcResult.Ok(householdRegisterService.create(actor)) }

    override suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> =
        tx(database) {
            actor
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val user = userRepository.findById(invitee)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "user", id = "$invitee"))
            householdRegisterService.invite(household, user, role)
            RpcResult.Ok(Unit)
        }

    override suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ): RpcResult<Unit, RpcError> =
        tx(database) {
            actor
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val user = userRepository.findById(target)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "user", id = "$target"))
            householdRegisterService.revoke(household, user)
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 3: HouseholdControllerTest を更新**

既存テストの `shouldThrow<NotFoundException>` / `verify { ... }` 後の戻り値 assertion を `RpcResult.Ok(...)` / `RpcResult.Err(RpcError.NotFound(...))` 形式に書き換える。`tx<X, RpcError>(any(), any())` の mock シグネチャに修正(複数メソッドあるので、各テストごとの T 型に合わせる)。

- [ ] **Step 4: テスト実行**

Run: `./gradlew :backend:api:test --tests "*HouseholdControllerTest"`
Expected: PASS

### Task 2.7: `CatalogRpcService` と `CatalogController` を `RpcResult` 化

**Files:**
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/CatalogRpcService.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogController.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogControllerTest.kt`

- [ ] **Step 1: CatalogRpcService.kt を更新**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.CatalogItems

@Rpc
interface CatalogRpcService {
    suspend fun search(query: String, limit: Int): RpcResult<CatalogItems, RpcError>

    suspend fun findById(id: CatalogItemId): RpcResult<CatalogItem?, RpcError>

    suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
    ): RpcResult<CatalogItem, RpcError>

    suspend fun revise(
        id: CatalogItemId,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
    ): RpcResult<Unit, RpcError>
}
```

- [ ] **Step 2: CatalogController.kt を更新**

```kotlin
package net.brightroom.mindstock.presentation.rpc.catalog

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.CatalogRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class CatalogController(
    private val catalogItemService: CatalogItemService,
    private val catalogItemRegisterService: CatalogItemRegisterService,
    private val catalogItemRepository: CatalogItemRepository,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
    private val database: Database,
) : CatalogRpcService {
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun search(query: String, limit: Int): RpcResult<CatalogItems, RpcError> =
        tx(database) {
            actor
            RpcResult.Ok(catalogItemService.search(query, limit))
        }

    override suspend fun findById(id: CatalogItemId): RpcResult<CatalogItem?, RpcError> =
        tx(database) {
            actor
            RpcResult.Ok(catalogItemService.findById(id))
        }

    override suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
    ): RpcResult<CatalogItem, RpcError> =
        tx(database) { RpcResult.Ok(catalogItemRegisterService.register(name, unit, actor)) }

    override suspend fun revise(
        id: CatalogItemId,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
    ): RpcResult<Unit, RpcError> =
        tx(database) {
            val catalogItem = catalogItemRepository.findById(id)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "catalog item", id = "$id"))
            catalogItemRegisterService.revise(catalogItem, newName, newUnit, actor)
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 3: CatalogControllerTest を更新**

`RpcResult` 形式に書き換える(Task 2.5/2.6 と同じパターン)。

- [ ] **Step 4: テスト実行**

Run: `./gradlew :backend:api:test --tests "*CatalogControllerTest"`
Expected: PASS

### Task 2.8: `ProductRpcService` と `ProductController` を `RpcResult` 化

**Files:**
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/ProductRpcService.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductController.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductControllerTest.kt`

- [ ] **Step 1: ProductRpcService.kt を更新**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products

@Rpc
interface ProductRpcService {
    suspend fun listOfHousehold(householdId: HouseholdId): RpcResult<Products, RpcError>

    suspend fun find(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product?, RpcError>

    suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product, RpcError>

    suspend fun setMinimumStock(
        id: ProductId,
        minimumStock: MinimumStock,
    ): RpcResult<Unit, RpcError>

    suspend fun archive(id: ProductId): RpcResult<Unit, RpcError>
}
```

- [ ] **Step 2: ProductController.kt を更新**

各メソッドで `throw NotFoundException(...)` を `return@tx RpcResult.Err(RpcError.NotFound(...))` に、戻り値を `RpcResult.Ok(...)` で包む。

```kotlin
package net.brightroom.mindstock.presentation.rpc.product

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.ProductRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class ProductController(
    private val productService: ProductService,
    private val productRegisterService: ProductRegisterService,
    private val householdRepository: HouseholdRepository,
    private val catalogItemRepository: CatalogItemRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
    private val database: Database,
) : ProductRpcService {
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun listOfHousehold(householdId: HouseholdId): RpcResult<Products, RpcError> =
        tx(database) {
            actor
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            RpcResult.Ok(productService.listOf(household))
        }

    override suspend fun find(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product?, RpcError> =
        tx(database) {
            actor
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val catalogItem = catalogItemRepository.findById(catalogItemId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "catalog item", id = "$catalogItemId"))
            RpcResult.Ok(productService.find(household, catalogItem))
        }

    override suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product, RpcError> =
        tx(database) {
            actor
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val catalogItem = catalogItemRepository.findById(catalogItemId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "catalog item", id = "$catalogItemId"))
            RpcResult.Ok(productRegisterService.adopt(household, catalogItem))
        }

    override suspend fun setMinimumStock(
        id: ProductId,
        minimumStock: MinimumStock,
    ): RpcResult<Unit, RpcError> =
        tx(database) {
            val product = productRepository.findById(id)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$id"))
            productRegisterService.setMinimumStock(product, minimumStock, actor)
            RpcResult.Ok(Unit)
        }

    override suspend fun archive(id: ProductId): RpcResult<Unit, RpcError> =
        tx(database) {
            val product = productRepository.findById(id)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$id"))
            productRegisterService.archive(product, actor)
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 3: ProductControllerTest を更新**

`RpcResult` 形式に書き換える。

- [ ] **Step 4: テスト実行**

Run: `./gradlew :backend:api:test --tests "*ProductControllerTest"`
Expected: PASS

### Task 2.9: `StockRpcService` と `StockController` を `RpcResult` 化

**Files:**
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/StockRpcService.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockController.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockControllerTest.kt`

- [ ] **Step 1: StockRpcService.kt を更新**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

@Rpc
interface StockRpcService {
    suspend fun get(productId: ProductId): RpcResult<Stock, RpcError>

    suspend fun list(householdId: HouseholdId): RpcResult<List<Stock>, RpcError>

    suspend fun movementHistory(
        productId: ProductId,
        limit: Int,
    ): RpcResult<StockMovements, RpcError>

    suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Replenishment, RpcError>

    suspend fun consume(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Consumption, RpcError>
}
```

- [ ] **Step 2: StockController.kt を更新**

```kotlin
package net.brightroom.mindstock.presentation.rpc.stock

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.StockRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class StockController(
    private val stockService: StockService,
    private val stockRegisterService: StockRegisterService,
    private val productRepository: ProductRepository,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
    private val database: Database,
) : StockRpcService {
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun get(productId: ProductId): RpcResult<Stock, RpcError> =
        tx(database) {
            actor
            val product = productRepository.findById(productId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            RpcResult.Ok(stockService.get(product))
        }

    override suspend fun list(householdId: HouseholdId): RpcResult<List<Stock>, RpcError> =
        tx(database) {
            actor
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            RpcResult.Ok(stockService.list(household))
        }

    override suspend fun movementHistory(
        productId: ProductId,
        limit: Int,
    ): RpcResult<StockMovements, RpcError> =
        tx(database) {
            actor
            val product = productRepository.findById(productId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            RpcResult.Ok(stockService.getMovementHistory(product, limit))
        }

    override suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Replenishment, RpcError> =
        tx(database) {
            val product = productRepository.findById(productId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            RpcResult.Ok(stockRegisterService.replenish(product, qty, occurredAt, actor, note))
        }

    override suspend fun consume(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Consumption, RpcError> =
        tx(database) {
            val product = productRepository.findById(productId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            RpcResult.Ok(stockRegisterService.consume(product, qty, occurredAt, actor, note))
        }
}
```

- [ ] **Step 3: StockControllerTest を更新**

`RpcResult` 形式に書き換える。`impl.get(productId) shouldBe stock` を `impl.get(productId) shouldBe RpcResult.Ok(stock)` に。

- [ ] **Step 4: テスト実行**

Run: `./gradlew :backend:api:test --tests "*StockControllerTest"`
Expected: PASS

### Task 2.10: `RpcExceptions.kt` / `ErrorConfiguration.kt` / `StatusPages` 削除

**Files:**
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/error/RpcExceptions.kt`
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/error/ErrorConfiguration.kt`
- Modify: `backend/api/src/main/resources/application.yaml`
- Modify: `backend/api/build.gradle.kts`

- [ ] **Step 1: 2 ファイル削除**

Run:
```bash
rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/error/RpcExceptions.kt
rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/error/ErrorConfiguration.kt
```

- [ ] **Step 2: application.yaml から errorConfigure module を削除**

以下の行を削除:

```yaml
      - "net.brightroom.mindstock.configuration.error.ErrorConfigurationKt.errorConfigure"
```

- [ ] **Step 3: build.gradle.kts から StatusPages 依存削除**

以下の行を削除:

```kotlin
    implementation(ktorLib.server.statusPages)
```

- [ ] **Step 4: `ActorResolver.kt` の `UnauthorizedException` 参照を残置不可能なので一時的にコメントアウト**

`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/ActorResolver.kt` の `UnauthorizedException` import + throw 2 箇所を一時的に `error("missing principal")` / `error("unknown user")` に置き換える(Phase 4 で本体ごと削除する)。

```kotlin
fun ApplicationCall.actor(userRepository: UserRepository): User {
    val principal = principal<MindstockPrincipal>() ?: error("missing principal")
    return userRepository.findByAuthIdentity(principal.authIdentity) ?: error("unknown user")
}
```

- [ ] **Step 5: コンパイル + unit test 実行**

Run: `./gradlew :backend:api:test`
Expected: PASS

### Task 2.11: E2E テストを `RpcResult.Err` ベースに更新

**Files:**
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserPublicRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/household/HouseholdRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/catalog/CatalogRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/product/ProductRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/stock/StockRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/auth/JwtAuthE2eTest.kt`

各テストで以下のパターンの書き換えを行う:

- 成功系: `val x = rpc.method(...)` → `val result = rpc.method(...); result.shouldBeInstanceOf<RpcResult.Ok<X>>(); val x = result.value`
- 既存の `shouldThrowAny { rpc.method(...) }`(NotFound 系):  `val r = rpc.method(...); r.shouldBeInstanceOf<RpcResult.Err<RpcError>>(); r.error.shouldBeInstanceOf<RpcError.NotFound>()`
- 既存の `shouldThrowAny { rpc.method(...) }`(認証失敗):**Phase 4 後** に「サーバが connection 拒否する」のでまだ `shouldThrowAny` を維持(JWT 認証失敗の場合 WS upgrade 自体が 401 で失敗する。これは RPC レイヤより下の話)

- [ ] **Step 1: UserPublicRpcServiceE2eTest を更新**

`shouldThrowAny { rpc.register(DisplayName("Second")) }` を以下に変更:

```kotlin
val r = rpc.register(DisplayName("Second"))
r.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
// Conflict(重複 sub)を期待。実装上は DB UNIQUE 制約違反が
// RpcError.Internal に変換されることが想定されるため、現状の挙動を pin する形で書く
// 必要なら HouseholdRegisterService に重複検出を入れ Conflict を明示返却する Future work
r.error.shouldBeInstanceOf<RpcError.Internal>()
```

成功系も `val user = rpc.register(...)` → `val result = rpc.register(...); result.shouldBeInstanceOf<RpcResult.Ok<User>>(); val user = result.value` 形式に。

- [ ] **Step 2: UserRpcServiceE2eTest を更新**

`rpc.rename(DisplayName("New Name"))` のような Unit 戻り値メソッドは:

```kotlin
val r = rpc.rename(DisplayName("New Name"))
r.shouldBeInstanceOf<RpcResult.Ok<Unit>>()
```

「Authorization header 無し」「unknown UserId Bearer」の `shouldThrowAny` は **Phase 4 後の挙動と一致するため維持**(JWT 検証失敗で WS upgrade 自体が拒否される)。

- [ ] **Step 3: HouseholdRpcServiceE2eTest を更新**

`shouldThrowAny` 内の RPC 呼び出しが「NotFound に該当するケース」なら `RpcResult.Err(RpcError.NotFound)` 形式に書き換える。`shouldThrowAny` を維持するのは「WS upgrade 自体が拒否される」ケースのみ。

- [ ] **Step 4: CatalogRpcServiceE2eTest を更新**

同様のパターンで。

- [ ] **Step 5: ProductRpcServiceE2eTest を更新**

同様のパターンで。

- [ ] **Step 6: StockRpcServiceE2eTest を更新**

同様のパターンで。

- [ ] **Step 7: JwtAuthE2eTest を更新**

すべて JWT 検証失敗のケース(WS upgrade 拒否)なので `shouldThrowAny` は **維持**。

- [ ] **Step 8: 全 E2E テスト実行**

Run: `./gradlew :backend:api:check`
Expected: BUILD SUCCESSFUL (integration 含む)

### Task 2.12: Phase 2 commit

- [ ] **Step 1: commit**

Run:
```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(api): RpcResult<T, RpcError> 戻り値ベースへ移行

- :rpc に sealed RpcResult / RpcError を追加
- 全 RPC service interface と Controller を throw → Err 返却に書き換え
- tx() を「想定外例外を Err(Internal) に変換」する形へ
- 死コードの StatusPages / RpcExceptions / ErrorConfiguration を削除
- Controller unit test と E2E test を RpcResult アサーションに書き換え

ref: docs/superpowers/specs/2026-05-29-ktor-configuration-redesign-design.md §5

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 3: Controller Factory パターン導入

### Task 3.1: 6 個の Controller Factory interface を作成

各 Factory は `fun interface` で `(ApplicationCall) -> Controller` の SAM 形式。

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicControllerFactory.kt`
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserControllerFactory.kt`
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdControllerFactory.kt`
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogControllerFactory.kt`
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductControllerFactory.kt`
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockControllerFactory.kt`

- [ ] **Step 1: UserPublicControllerFactory.kt 作成**

```kotlin
package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall

fun interface UserPublicControllerFactory {
    fun create(call: ApplicationCall): UserPublicController
}
```

- [ ] **Step 2: UserControllerFactory.kt 作成**

```kotlin
package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall

fun interface UserControllerFactory {
    fun create(call: ApplicationCall): UserController
}
```

- [ ] **Step 3: HouseholdControllerFactory.kt 作成**

```kotlin
package net.brightroom.mindstock.presentation.rpc.household

import io.ktor.server.application.ApplicationCall

fun interface HouseholdControllerFactory {
    fun create(call: ApplicationCall): HouseholdController
}
```

- [ ] **Step 4: CatalogControllerFactory.kt 作成**

```kotlin
package net.brightroom.mindstock.presentation.rpc.catalog

import io.ktor.server.application.ApplicationCall

fun interface CatalogControllerFactory {
    fun create(call: ApplicationCall): CatalogController
}
```

- [ ] **Step 5: ProductControllerFactory.kt 作成**

```kotlin
package net.brightroom.mindstock.presentation.rpc.product

import io.ktor.server.application.ApplicationCall

fun interface ProductControllerFactory {
    fun create(call: ApplicationCall): ProductController
}
```

- [ ] **Step 6: StockControllerFactory.kt 作成**

```kotlin
package net.brightroom.mindstock.presentation.rpc.stock

import io.ktor.server.application.ApplicationCall

fun interface StockControllerFactory {
    fun create(call: ApplicationCall): StockController
}
```

- [ ] **Step 7: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 3.2: `DependenciesConfiguration.kt` に factory provider を追加

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt`

- [ ] **Step 1: ファイル末尾の `dependencies {}` ブロックに以下を追加**

```kotlin
        // Controller Factory (30) — per-WS-connection 単位で Controller を組み立てる
        provide<UserPublicControllerFactory> {
            val urs = resolve<UserRegisterService>()
            val db = resolve<Database>()
            UserPublicControllerFactory { call -> UserPublicController(urs, call, db) }
        }
        provide<UserControllerFactory> {
            val urs = resolve<UserRegisterService>()
            val ur = resolve<UserRepository>()
            val db = resolve<Database>()
            UserControllerFactory { call -> UserController(urs, ur, call, db) }
        }
        provide<HouseholdControllerFactory> {
            val hs = resolve<HouseholdService>()
            val hrs = resolve<HouseholdRegisterService>()
            val hr = resolve<HouseholdRepository>()
            val ur = resolve<UserRepository>()
            val db = resolve<Database>()
            HouseholdControllerFactory { call -> HouseholdController(hs, hrs, hr, ur, call, db) }
        }
        provide<CatalogControllerFactory> {
            val cs = resolve<CatalogItemService>()
            val crs = resolve<CatalogItemRegisterService>()
            val cr = resolve<CatalogItemRepository>()
            val ur = resolve<UserRepository>()
            val db = resolve<Database>()
            CatalogControllerFactory { call -> CatalogController(cs, crs, cr, ur, call, db) }
        }
        provide<ProductControllerFactory> {
            val ps = resolve<ProductService>()
            val prs = resolve<ProductRegisterService>()
            val hr = resolve<HouseholdRepository>()
            val cr = resolve<CatalogItemRepository>()
            val pr = resolve<ProductRepository>()
            val ur = resolve<UserRepository>()
            val db = resolve<Database>()
            ProductControllerFactory { call -> ProductController(ps, prs, hr, cr, pr, ur, call, db) }
        }
        provide<StockControllerFactory> {
            val ss = resolve<StockService>()
            val srs = resolve<StockRegisterService>()
            val pr = resolve<ProductRepository>()
            val hr = resolve<HouseholdRepository>()
            val ur = resolve<UserRepository>()
            val db = resolve<Database>()
            StockControllerFactory { call -> StockController(ss, srs, pr, hr, ur, call, db) }
        }
```

- [ ] **Step 2: 必要な import を追加**

ファイル先頭に以下を追加:

```kotlin
import net.brightroom.mindstock.presentation.rpc.catalog.CatalogControllerFactory
import net.brightroom.mindstock.presentation.rpc.household.HouseholdControllerFactory
import net.brightroom.mindstock.presentation.rpc.product.ProductControllerFactory
import net.brightroom.mindstock.presentation.rpc.stock.StockControllerFactory
import net.brightroom.mindstock.presentation.rpc.user.UserControllerFactory
import net.brightroom.mindstock.presentation.rpc.user.UserPublicControllerFactory
import org.jetbrains.exposed.v1.jdbc.Database
import io.ktor.server.application.ApplicationCall
```

(`Database` / `ApplicationCall` の import がすでにあれば二重 import にしない)

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 3.3: `RoutingConfiguration.kt` を factory ベースに置換

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt`

- [ ] **Step 1: ファイル全体を以下に置換**

```kotlin
@file:OptIn(ExperimentalSerializationApi::class)

package net.brightroom.mindstock.configuration.routing

import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin
import net.brightroom.mindstock.configuration.auth.applicationCall
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.presentation.rpc.catalog.CatalogControllerFactory
import net.brightroom.mindstock.presentation.rpc.household.HouseholdControllerFactory
import net.brightroom.mindstock.presentation.rpc.product.ProductControllerFactory
import net.brightroom.mindstock.presentation.rpc.stock.StockControllerFactory
import net.brightroom.mindstock.presentation.rpc.user.UserControllerFactory
import net.brightroom.mindstock.presentation.rpc.user.UserPublicControllerFactory
import net.brightroom.mindstock.rpc.CatalogRpcService
import net.brightroom.mindstock.rpc.HouseholdRpcService
import net.brightroom.mindstock.rpc.ProductRpcService
import net.brightroom.mindstock.rpc.StockRpcService
import net.brightroom.mindstock.rpc.UserPublicRpcService
import net.brightroom.mindstock.rpc.UserRpcService

fun Application.routingConfigure() {
    install(ContentNegotiation) {
        jsonIo(CustomJson)
    }
    install(Krpc) {
        serialization { json(KrpcJson) }
    }
    install(WsSubprotocolEchoPlugin)

    val userPublicFactory: UserPublicControllerFactory by dependencies
    val userFactory: UserControllerFactory by dependencies
    val householdFactory: HouseholdControllerFactory by dependencies
    val catalogFactory: CatalogControllerFactory by dependencies
    val productFactory: ProductControllerFactory by dependencies
    val stockFactory: StockControllerFactory by dependencies

    routing {
        authenticate("user-public") {
            rpc("/api/v1/user/public") {
                registerService<UserPublicRpcService> { userPublicFactory.create(applicationCall) }
            }
        }
        authenticate("user") {
            rpc("/api/v1/user") {
                registerService<UserRpcService> { userFactory.create(applicationCall) }
            }
            rpc("/api/v1/household") {
                registerService<HouseholdRpcService> { householdFactory.create(applicationCall) }
            }
            rpc("/api/v1/catalog") {
                registerService<CatalogRpcService> { catalogFactory.create(applicationCall) }
            }
            rpc("/api/v1/product") {
                registerService<ProductRpcService> { productFactory.create(applicationCall) }
            }
            rpc("/api/v1/stock") {
                registerService<StockRpcService> { stockFactory.create(applicationCall) }
            }
        }
    }
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 3.4: 全テスト実行 + Phase 3 commit

- [ ] **Step 1: 全テスト実行**

Run: `./gradlew :backend:api:check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: commit**

Run:
```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(api): Controller Factory パターンを導入し RoutingConfiguration を整理

各 Controller について fun interface *ControllerFactory を作り、DI 経由で
(ApplicationCall) -> Controller の高階関数として provide する。RoutingConfiguration
の by dependencies が 14 → 6 個に減り、Controller の依存が増えても routing は
無変更で済むようになった。

ref: docs/superpowers/specs/2026-05-29-ktor-configuration-redesign-design.md §3

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 4: Auth 再設計

ktor-auth を撤去し、自作 `MindstockAuthPlugin` + `RequireRegisteredUserPlugin` に置換する。Controller の `call: ApplicationCall` → `session: MindstockSession` への置き換えを同 phase で行う。

### Task 4.1: `MindstockSession` data class を作成

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt`

- [ ] **Step 1: ファイル作成**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.util.AttributeKey
import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * WS upgrade 時に [MindstockAuthPlugin] が組み立て、call.attributes に格納する。
 * 接続単位で immutable。
 *
 * - [identity]: JWT 検証成功時に組み立てた AuthIdentity
 * - [userId]: 登録済み User の id。未登録の場合は null(register エンドポイントでのみ許容)
 * - [exp]: JWT の expiresAt。各 RPC メソッドで `tx()` 経由の guard が比較する
 * - [callId]: 接続単位のトレース ID。構造化ログに紐付ける
 */
@OptIn(ExperimentalUuidApi::class)
data class MindstockSession(
    val identity: AuthIdentity,
    val userId: UserId?,
    val exp: Instant,
    val callId: Uuid,
)

internal val MindstockSessionKey: AttributeKey<MindstockSession> =
    AttributeKey("net.brightroom.mindstock.MindstockSession")
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 4.2: `JwksKeyProvider` を作成

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/JwksKeyProvider.kt`

- [ ] **Step 1: ファイル作成**

```kotlin
package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwt.interfaces.RSAKeyProvider
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * java-jwt の `Algorithm.RSA256(...)` が要求する [RSAKeyProvider] を
 * `auth0/jwk` の [JwkProvider] から橋渡しする。
 *
 * - JWKS の cache / rate-limit は [JwkProvider] 側で行う
 * - 秘密鍵は不要(検証専用)
 */
class JwksKeyProvider(
    private val jwkProvider: JwkProvider,
) : RSAKeyProvider {
    override fun getPublicKeyById(keyId: String?): RSAPublicKey =
        jwkProvider.get(keyId).publicKey as RSAPublicKey

    override fun getPrivateKey(): RSAPrivateKey? = null

    override fun getPrivateKeyId(): String? = null
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 4.3: `MindstockAuthPlugin` を作成

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPlugin.kt`

- [ ] **Step 1: `WsBearerTokenExtractor.kt` に `extractRaw` を追加**

`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WsBearerTokenExtractor.kt` に新メソッドを追加(既存 `extract` は phase 末で削除する):

```kotlin
    /**
     * Authorization ヘッダまたは Sec-WebSocket-Protocol から
     * 生の JWT 文字列を取り出す。MindstockAuthPlugin 用。
     */
    fun extractRaw(call: ApplicationCall): String? {
        call.request.header(HttpHeaders.Authorization)?.let { value ->
            val parts = value.trim().split(" ", limit = 2)
            if (parts.size == 2 && parts[0].equals(AuthScheme.Bearer, ignoreCase = true)) {
                return parts[1].trim()
            }
        }
        val protocols = call.request.headers
            .getAll(HttpHeaders.SecWebSocketProtocol)
            .orEmpty()
        val entries = protocols.flatMap { it.split(",") }.map { it.trim() }
        val bearerEntry = entries.firstOrNull { it.startsWith(WS_PROTOCOL_BEARER_PREFIX) } ?: return null
        val b64 = bearerEntry.removePrefix(WS_PROTOCOL_BEARER_PREFIX)
        return runCatching {
            String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8)
        }.getOrNull()
    }
```

- [ ] **Step 2: MindstockAuthPlugin.kt を作成**

```kotlin
@file:Suppress("DEPRECATION")

package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.respond
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MindstockAuthConfig {
    var jwkProvider: JwkProvider? = null
    var issuer: String? = null
    var audience: String? = null
    var userRepository: UserRepository? = null
    var database: Database? = null
    var leewaySeconds: Long = 30
}

/**
 * JWT 検証 + [MindstockSession] 組み立てを担う Ktor plugin。
 *
 * Security Invariants(spec §4.6):
 * 1. JWT 検証 crypto は自前で書かない → `com.auth0:java-jwt` の `JWT.require(...).build().verify()` 経由
 * 2. Algorithm は RSA256 固定(`JwksKeyProvider` 経由)
 * 3. JWKS は cache + rate-limit 必須(呼び出し側で `JwkProviderBuilder` を渡す前提)
 * 4. `withIssuer` / `withAudience` を必ず指定
 * 5. `acceptLeeway(30)` を明示
 * 6. validate 相当の DB アクセスは `newSuspendedTransaction`
 * 7. token 値を含む `Sec-WebSocket-Protocol` は response header に echo しない([WsSubprotocolEchoPlugin])
 */
@OptIn(ExperimentalUuidApi::class)
val MindstockAuthPlugin =
    createApplicationPlugin(name = "MindstockAuth", createConfiguration = ::MindstockAuthConfig) {
        val jwkProvider = requireNotNull(pluginConfig.jwkProvider) { "jwkProvider required" }
        val issuer = requireNotNull(pluginConfig.issuer) { "issuer required" }
        val audience = requireNotNull(pluginConfig.audience) { "audience required" }
        val userRepository = requireNotNull(pluginConfig.userRepository) { "userRepository required" }
        val database = requireNotNull(pluginConfig.database) { "database required" }
        val leewaySeconds = pluginConfig.leewaySeconds

        val verifier: JWTVerifier =
            JWT.require(Algorithm.RSA256(JwksKeyProvider(jwkProvider)))
                .withIssuer(issuer)
                .withAudience(audience)
                .acceptLeeway(leewaySeconds)
                .build()

        onCall { call ->
            val token = WsBearerTokenExtractor.extractRaw(call)
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val decoded = runCatching { verifier.verify(token) }.getOrNull()
            if (decoded == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val sub = decoded.subject
            if (sub.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub))
            val userId =
                newSuspendedTransaction(db = database) {
                    userRepository.findByAuthIdentity(identity)?.id
                }
            val expDate = decoded.expiresAt
                ?: run {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@onCall
                }
            val session =
                MindstockSession(
                    identity = identity,
                    userId = userId,
                    exp = Instant.fromEpochMilliseconds(expDate.time),
                    callId = Uuid.random(),
                )
            call.attributes.put(MindstockSessionKey, session)
        }
    }
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 4.4: `MindstockAuthPlugin` の unit test を追加

DB アクセスを含む確認は Phase 4.12 で既存 E2E テスト経由で行う(`JwtAuthE2eTest` が「expired/wrong issuer/wrong audience/unknown key signed」すべてを既にカバーしている。MindstockAuthPlugin 化後も同テストが green でなければならず、それが本 plugin の最強の証拠)。

本 Task では DB を使わない最小ユニットテストのみ追加する: 「Authorization header が無い call は session 属性が付与されない」。

**Files:**
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPluginTest.kt`

- [ ] **Step 1: テストファイル作成**

```kotlin
package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.JwkProviderBuilder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.user.UserRepository
import org.jetbrains.exposed.v1.jdbc.Database
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * DB / 本物 JWT を使わない最小ユニットテスト。
 * 「token なし → session 未付与 → 401 + handler 内で session 取得不可」のフロー確認のみ。
 *
 * 有効トークン / 期限切れ / wrong issuer / wrong audience 等の検証は
 * 既存 `JwtAuthE2eTest` (integration) が網羅しており、Phase 4.12 で
 * MindstockAuthPlugin 化後も同テストが green であることが本 plugin の検証になる。
 */
class MindstockAuthPluginTest :
    FunSpec({
        test("Authorization header 無し → 401, MindstockSession 属性は付かない") {
            var sessionSeen = false
            testApplication {
                application {
                    install(MindstockAuthPlugin) {
                        // JWKS は本テストでは到達しないが、null チェックを通すためダミー URL を渡す
                        jwkProvider = JwkProviderBuilder(URL("http://127.0.0.1:1/jwks"))
                            .cached(1, 1, TimeUnit.MINUTES)
                            .rateLimited(1, 1, TimeUnit.MINUTES)
                            .build()
                        issuer = "test-issuer"
                        audience = "test-aud"
                        userRepository = mockk<UserRepository>(relaxed = true)
                        database = mockk<Database>(relaxed = true)
                    }
                    routing {
                        get("/probe") {
                            sessionSeen = call.attributes.getOrNull(MindstockSessionKey) != null
                            call.respondText("ok")
                        }
                    }
                }
                val res = client.get("/probe")
                res.status shouldBe HttpStatusCode.Unauthorized
                sessionSeen shouldBe false
            }
        }
    })
```

- [ ] **Step 2: テスト実行**

Run: `./gradlew :backend:api:test --tests "*MindstockAuthPluginTest"`
Expected: PASS

### Task 4.5: `RequireRegisteredUserPlugin` と unit test を追加

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPlugin.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPluginTest.kt`

- [ ] **Step 1: plugin ファイル作成**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

/**
 * Route subtree に install することで「登録済み User しか通さない」境界を作る。
 * MindstockAuthPlugin が組み立てた MindstockSession を見て userId が null なら 401。
 */
val RequireRegisteredUserPlugin =
    createRouteScopedPlugin(name = "RequireRegisteredUser") {
        onCall { call ->
            val session = call.attributes.getOrNull(MindstockSessionKey)
            if (session == null || session.userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
```

- [ ] **Step 2: test ファイル作成**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RequireRegisteredUserPluginTest :
    FunSpec({
        fun MindstockSessionOf(userId: UserId?): MindstockSession =
            MindstockSession(
                identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
                userId = userId,
                exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                callId = Uuid.random(),
            )

        test("session 有り + userId 非 null → 200") {
            testApplication {
                application {
                    intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {
                        call.attributes.put(MindstockSessionKey, MindstockSessionOf(UserId(Uuid.random())))
                    }
                    routing {
                        route("/guarded") {
                            install(RequireRegisteredUserPlugin)
                            get { call.respondText("ok") }
                        }
                    }
                }
                client.get("/guarded").status shouldBe HttpStatusCode.OK
            }
        }

        test("session 有り + userId null → 401") {
            testApplication {
                application {
                    intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {
                        call.attributes.put(MindstockSessionKey, MindstockSessionOf(null))
                    }
                    routing {
                        route("/guarded") {
                            install(RequireRegisteredUserPlugin)
                            get { call.respondText("ok") }
                        }
                    }
                }
                client.get("/guarded").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("session 無し → 401") {
            testApplication {
                application {
                    routing {
                        route("/guarded") {
                            install(RequireRegisteredUserPlugin)
                            get { call.respondText("ok") }
                        }
                    }
                }
                client.get("/guarded").status shouldBe HttpStatusCode.Unauthorized
            }
        }
    })
```

- [ ] **Step 3: テスト実行**

Run: `./gradlew :backend:api:test --tests "*RequireRegisteredUserPluginTest"`
Expected: PASS

### Task 4.6: `tx()` を `session.exp` チェック付きに拡張

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/transaction/TxWithGuardTest.kt`

- [ ] **Step 1: Transaction.kt を更新**

```kotlin
@file:Suppress("DEPRECATION")

package net.brightroom.mindstock.configuration.transaction

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

private val logger = KotlinLogging.logger {}

/**
 * RPC message-scoped transaction boundary + session guard。
 *
 * - session.exp が現在時刻を超えていたら即 `Err(Unauthorized("token expired"))`(L2)
 * - block 内の想定外例外は `Err(Internal)` に変換し、logger.error する
 * - CancellationException は伝播
 * - supervisorScope は kRPC server scope へのエラー leak 防止のため維持
 */
suspend fun <T> tx(
    database: Database,
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> {
    val now = Clock.System.now()
    if (now > session.exp) {
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        supervisorScope { newSuspendedTransaction(db = database) { block() } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId} user_id=${session.userId}" }
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}
```

- [ ] **Step 2: TxWithGuardTest.kt 作成**

```kotlin
package net.brightroom.mindstock.configuration.transaction

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.test.TestDataSource
import net.brightroom.mindstock.test.testHikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class TxWithGuardTest :
    FunSpec({
        fun sessionWith(exp: Instant): MindstockSession =
            MindstockSession(
                identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
                userId = UserId(Uuid.random()),
                exp = exp,
                callId = Uuid.random(),
            )

        test("session.exp が過去 → Err(Unauthorized(token expired))、block は呼ばれない") {
            val db = mockk<Database>()  // newSuspendedTransaction 内で参照されないため Mock で十分
            var called = false
            val expired = Clock.System.now() - 1.hours
            val result =
                runBlocking {
                    tx<Int>(db, sessionWith(expired)) {
                        called = true
                        RpcResult.Ok(1)
                    }
                }
            result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            result.error.shouldBeInstanceOf<RpcError.Unauthorized>()
            (result.error as RpcError.Unauthorized).reason shouldBe "token expired"
            called shouldBe false
        }

        test("block 内で IllegalStateException → Err(Internal)") {
            TestDataSource.withFreshSchema { jdbcUrl, _ ->
                val ds = testHikariDataSource(jdbcUrl, TestDataSource.user, TestDataSource.password)
                try {
                    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
                    val database = Database.connect(ds)
                    val result =
                        runBlocking {
                            tx<Int>(database, sessionWith(Clock.System.now() + 1.hours)) {
                                error("boom")
                            }
                        }
                    result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                    result.error.shouldBeInstanceOf<RpcError.Internal>()
                } finally {
                    ds.close()
                }
            }
        }

        test("正常系: block が Ok を返せばそのまま返る") {
            TestDataSource.withFreshSchema { jdbcUrl, _ ->
                val ds = testHikariDataSource(jdbcUrl, TestDataSource.user, TestDataSource.password)
                try {
                    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
                    val database = Database.connect(ds)
                    val result =
                        runBlocking {
                            tx<String>(database, sessionWith(Clock.System.now() + 1.hours)) {
                                RpcResult.Ok("hello")
                            }
                        }
                    result shouldBe RpcResult.Ok("hello")
                } finally {
                    ds.close()
                }
            }
        }
    })
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin :backend:api:compileTestKotlin`
Expected: 全 Controller がまだ `tx(database) { ... }` の 2 引数版を呼んでいるため compile error 多数 → 次 Task で順次解消

### Task 4.7: 6 個の Controller を `session: MindstockSession` に書き換え

`call.actor(userRepository)` (DB lookup) を **削除** し、`session.userId` から直接 User を解決する形に変える。`session.userId == null` のケースは `RequireRegisteredUserPlugin` が routing 層で弾くため、登録済みエンドポイントの Controller では `userId!!` で良い。`UserPublicController` だけは未登録 OK なので `session.identity` だけ使う。

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockController.kt`
- Modify: 6 個の `*ControllerFactory.kt`

- [ ] **Step 1: UserPublicController を更新**

```kotlin
package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserPublicRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserPublicController(
    private val userRegisterService: UserRegisterService,
    private val session: MindstockSession,
    private val database: Database,
) : UserPublicRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<User, RpcError> =
        tx(database, session) {
            RpcResult.Ok(userRegisterService.register(session.identity, displayName))
        }
}
```

- [ ] **Step 2: UserController を更新**

```kotlin
package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserController(
    private val userRegisterService: UserRegisterService,
    private val userRepository: UserRepository,
    private val session: MindstockSession,
    private val database: Database,
) : UserRpcService {
    private suspend fun resolveActor(): User =
        // session.userId は RequireRegisteredUserPlugin により non-null 保証
        userRepository.findById(requireNotNull(session.userId))
            ?: error("session.userId points to non-existent User — likely deleted between upgrade and call")

    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val actor = resolveActor()
            userRegisterService.rename(actor, displayName)
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 3: HouseholdController を更新**

```kotlin
package net.brightroom.mindstock.presentation.rpc.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.rpc.HouseholdRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class HouseholdController(
    private val householdService: HouseholdService,
    private val householdRegisterService: HouseholdRegisterService,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val session: MindstockSession,
    private val database: Database,
) : HouseholdRpcService {
    private suspend fun resolveActor(): User =
        userRepository.findById(requireNotNull(session.userId))
            ?: error("session.userId points to non-existent User")

    override suspend fun findOf(): RpcResult<Household?, RpcError> =
        tx(database, session) { RpcResult.Ok(householdService.findOf(resolveActor())) }

    override suspend fun create(): RpcResult<Household, RpcError> =
        tx(database, session) { RpcResult.Ok(householdRegisterService.create(resolveActor())) }

    override suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            resolveActor()
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val user = userRepository.findById(invitee)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "user", id = "$invitee"))
            householdRegisterService.invite(household, user, role)
            RpcResult.Ok(Unit)
        }

    override suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            resolveActor()
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val user = userRepository.findById(target)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "user", id = "$target"))
            householdRegisterService.revoke(household, user)
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 4: CatalogController を更新**

Task 2.7 の CatalogController に対する diff:
1. import `io.ktor.server.application.ApplicationCall` を削除
2. import `net.brightroom.mindstock.configuration.auth.actor` を削除
3. import `net.brightroom.mindstock.configuration.auth.MindstockSession` を追加
4. constructor の `private val call: ApplicationCall,` を `private val session: MindstockSession,` に置換
5. `private val actor: User by lazy { call.actor(userRepository) }` を以下に置換:

```kotlin
    private suspend fun resolveActor(): User =
        userRepository.findById(requireNotNull(session.userId))
            ?: error("session.userId points to non-existent User")
```

6. 全 `tx(database) {` を `tx(database, session) {` に置換(4 箇所)
7. 全 `actor` 参照を `resolveActor()` に置換。ただし関数の引数として渡すのみで結果を使わないケース(例: `catalogItemService.search()` の前の `actor` ステートメント — 認証チェックの副作用だった)は、 routing 層 enforcement に責務が移ったので **行ごと削除**

更新後の最終コード:

```kotlin
package net.brightroom.mindstock.presentation.rpc.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.CatalogRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class CatalogController(
    private val catalogItemService: CatalogItemService,
    private val catalogItemRegisterService: CatalogItemRegisterService,
    private val catalogItemRepository: CatalogItemRepository,
    private val userRepository: UserRepository,
    private val session: MindstockSession,
    private val database: Database,
) : CatalogRpcService {
    private suspend fun resolveActor(): User =
        userRepository.findById(requireNotNull(session.userId))
            ?: error("session.userId points to non-existent User")

    override suspend fun search(query: String, limit: Int): RpcResult<CatalogItems, RpcError> =
        tx(database, session) { RpcResult.Ok(catalogItemService.search(query, limit)) }

    override suspend fun findById(id: CatalogItemId): RpcResult<CatalogItem?, RpcError> =
        tx(database, session) { RpcResult.Ok(catalogItemService.findById(id)) }

    override suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
    ): RpcResult<CatalogItem, RpcError> =
        tx(database, session) { RpcResult.Ok(catalogItemRegisterService.register(name, unit, resolveActor())) }

    override suspend fun revise(
        id: CatalogItemId,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val catalogItem = catalogItemRepository.findById(id)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "catalog item", id = "$id"))
            catalogItemRegisterService.revise(catalogItem, newName, newUnit, resolveActor())
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 5: ProductController を更新**

Step 4 と同じ diff ルールを Task 2.8 の ProductController に適用。最終コード:

```kotlin
package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.ProductRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class ProductController(
    private val productService: ProductService,
    private val productRegisterService: ProductRegisterService,
    private val householdRepository: HouseholdRepository,
    private val catalogItemRepository: CatalogItemRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val session: MindstockSession,
    private val database: Database,
) : ProductRpcService {
    private suspend fun resolveActor(): User =
        userRepository.findById(requireNotNull(session.userId))
            ?: error("session.userId points to non-existent User")

    override suspend fun listOfHousehold(householdId: HouseholdId): RpcResult<Products, RpcError> =
        tx(database, session) {
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            RpcResult.Ok(productService.listOf(household))
        }

    override suspend fun find(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product?, RpcError> =
        tx(database, session) {
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val catalogItem = catalogItemRepository.findById(catalogItemId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "catalog item", id = "$catalogItemId"))
            RpcResult.Ok(productService.find(household, catalogItem))
        }

    override suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product, RpcError> =
        tx(database, session) {
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val catalogItem = catalogItemRepository.findById(catalogItemId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "catalog item", id = "$catalogItemId"))
            RpcResult.Ok(productRegisterService.adopt(household, catalogItem))
        }

    override suspend fun setMinimumStock(
        id: ProductId,
        minimumStock: MinimumStock,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val product = productRepository.findById(id)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$id"))
            productRegisterService.setMinimumStock(product, minimumStock, resolveActor())
            RpcResult.Ok(Unit)
        }

    override suspend fun archive(id: ProductId): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val product = productRepository.findById(id)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$id"))
            productRegisterService.archive(product, resolveActor())
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 6: StockController を更新**

Step 4 と同じ diff ルールを Task 2.9 の StockController に適用。最終コード:

```kotlin
package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.StockRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class StockController(
    private val stockService: StockService,
    private val stockRegisterService: StockRegisterService,
    private val productRepository: ProductRepository,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val session: MindstockSession,
    private val database: Database,
) : StockRpcService {
    private suspend fun resolveActor(): User =
        userRepository.findById(requireNotNull(session.userId))
            ?: error("session.userId points to non-existent User")

    override suspend fun get(productId: ProductId): RpcResult<Stock, RpcError> =
        tx(database, session) {
            val product = productRepository.findById(productId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            RpcResult.Ok(stockService.get(product))
        }

    override suspend fun list(householdId: HouseholdId): RpcResult<List<Stock>, RpcError> =
        tx(database, session) {
            val household = householdRepository.findById(householdId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            RpcResult.Ok(stockService.list(household))
        }

    override suspend fun movementHistory(
        productId: ProductId,
        limit: Int,
    ): RpcResult<StockMovements, RpcError> =
        tx(database, session) {
            val product = productRepository.findById(productId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            RpcResult.Ok(stockService.getMovementHistory(product, limit))
        }

    override suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Replenishment, RpcError> =
        tx(database, session) {
            val product = productRepository.findById(productId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            RpcResult.Ok(stockRegisterService.replenish(product, qty, occurredAt, resolveActor(), note))
        }

    override suspend fun consume(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Consumption, RpcError> =
        tx(database, session) {
            val product = productRepository.findById(productId)
                ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            RpcResult.Ok(stockRegisterService.consume(product, qty, occurredAt, resolveActor(), note))
        }
}
```

- [ ] **Step 7: 各 Factory interface の signature を更新**

各 `*ControllerFactory` を `(MindstockSession) -> Controller` に変更:

```kotlin
fun interface StockControllerFactory {
    fun create(session: MindstockSession): StockController
}
```

(他 5 個も同じ。`import net.brightroom.mindstock.configuration.auth.MindstockSession` を追加)

- [ ] **Step 8: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL(test はまだ NG だが本 step は main のみ)

### Task 4.8: `DependenciesConfiguration` の factory provider を session ベースに書き換え

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt`

- [ ] **Step 1: factory provider 6 個の lambda を `{ session -> ... }` に変更**

例(UserController):

```kotlin
        provide<UserControllerFactory> {
            val urs = resolve<UserRegisterService>()
            val ur = resolve<UserRepository>()
            val db = resolve<Database>()
            UserControllerFactory { session -> UserController(urs, ur, session, db) }
        }
```

他 5 個も同じパターン。`ApplicationCall` import が不要になれば削除。`MindstockSession` import を追加:

```kotlin
import net.brightroom.mindstock.configuration.auth.MindstockSession
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 4.9: `RoutingConfiguration` を新 plugin ベースに書き換え

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt`

- [ ] **Step 1: ファイル全体を以下に置換**

```kotlin
@file:OptIn(ExperimentalSerializationApi::class)

package net.brightroom.mindstock.configuration.routing

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.configuration.auth.MindstockAuthPlugin
import net.brightroom.mindstock.configuration.auth.MindstockSessionKey
import net.brightroom.mindstock.configuration.auth.RequireRegisteredUserPlugin
import net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.presentation.rpc.catalog.CatalogControllerFactory
import net.brightroom.mindstock.presentation.rpc.household.HouseholdControllerFactory
import net.brightroom.mindstock.presentation.rpc.product.ProductControllerFactory
import net.brightroom.mindstock.presentation.rpc.stock.StockControllerFactory
import net.brightroom.mindstock.presentation.rpc.user.UserControllerFactory
import net.brightroom.mindstock.presentation.rpc.user.UserPublicControllerFactory
import net.brightroom.mindstock.rpc.CatalogRpcService
import net.brightroom.mindstock.rpc.HouseholdRpcService
import net.brightroom.mindstock.rpc.ProductRpcService
import net.brightroom.mindstock.rpc.StockRpcService
import net.brightroom.mindstock.rpc.UserPublicRpcService
import net.brightroom.mindstock.rpc.UserRpcService
import org.jetbrains.exposed.v1.jdbc.Database
import java.net.URL
import java.util.concurrent.TimeUnit

fun Application.routingConfigure() {
    install(ContentNegotiation) {
        jsonIo(CustomJson)
    }
    install(Krpc) {
        serialization { json(KrpcJson) }
    }
    install(WsSubprotocolEchoPlugin)

    val cfg = environment.config.config("external.auth")
    val authIssuer = cfg.property("issuer").getString()
    val authAudience = cfg.property("audience").getString()
    val jwksUrl = cfg.property("jwks-url").getString()

    val userRepository: UserRepository by dependencies
    val database: Database by dependencies

    install(MindstockAuthPlugin) {
        jwkProvider = JwkProviderBuilder(URL(jwksUrl))
            .cached(10, 1, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()
        issuer = authIssuer
        audience = authAudience
        this.userRepository = userRepository
        this.database = database
    }

    val userPublicFactory: UserPublicControllerFactory by dependencies
    val userFactory: UserControllerFactory by dependencies
    val householdFactory: HouseholdControllerFactory by dependencies
    val catalogFactory: CatalogControllerFactory by dependencies
    val productFactory: ProductControllerFactory by dependencies
    val stockFactory: StockControllerFactory by dependencies

    routing {
        route("/api/v1") {
            // JWT 有効ならよい(未登録 OK)
            route("/user/public") {
                rpc {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<UserPublicRpcService> { userPublicFactory.create(session) }
                }
            }
            // 登録済み User 必須
            route("/") {
                install(RequireRegisteredUserPlugin)

                rpc("/user") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<UserRpcService> { userFactory.create(session) }
                }
                rpc("/household") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<HouseholdRpcService> { householdFactory.create(session) }
                }
                rpc("/catalog") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<CatalogRpcService> { catalogFactory.create(session) }
                }
                rpc("/product") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<ProductRpcService> { productFactory.create(session) }
                }
                rpc("/stock") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<StockRpcService> { stockFactory.create(session) }
                }
            }
        }
    }
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 4.10: 旧 auth 関連ファイルを削除

**Files:**
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/AuthConfiguration.kt`
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/JwtAuthConfiguration.kt`
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockPrincipal.kt`
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/ActorResolver.kt`
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/CurrentCall.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WsBearerTokenExtractor.kt` — `extract()` メソッド削除
- Modify: `backend/api/src/main/resources/application.yaml`
- Modify: `backend/api/build.gradle.kts`

- [ ] **Step 1: ファイル削除**

Run:
```bash
rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/AuthConfiguration.kt
rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/JwtAuthConfiguration.kt
rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockPrincipal.kt
rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/ActorResolver.kt
rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/CurrentCall.kt
```

- [ ] **Step 2: `WsBearerTokenExtractor.kt` の旧 `extract(): HttpAuthHeader?` メソッドを削除**

`extractRaw(): String?` のみ残し、`HttpAuthHeader` import も削除。

- [ ] **Step 3: `application.yaml` から `authConfigure` 行を削除**

```yaml
      - "net.brightroom.mindstock.configuration.auth.AuthConfigurationKt.authConfigure"
```

- [ ] **Step 4: `build.gradle.kts` から ktor-auth 依存を削除**

```kotlin
    implementation(ktorLib.server.auth)
    implementation(ktorLib.server.auth.jwt)
```

ただし `MindstockAuthPlugin` で `com.auth0:java-jwt` と `com.auth0:jwks-rsa` を直接使うので、これらを transitive で持ってきていた `ktorLib.server.auth.jwt` が消えると import 不可になる可能性がある。その場合は **直接 lib を追加**:

```kotlin
    implementation(libs.auth0.javaJwt)
    implementation(libs.auth0.jwksRsa)
```

(`gradle/libs.versions.toml` に該当エントリが無ければ追加:

```toml
auth0-java-jwt = { module = "com.auth0:java-jwt", version = "4.4.0" }
auth0-jwks-rsa = { module = "com.auth0:jwks-rsa", version = "0.22.1" }
```
)

- [ ] **Step 5: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Task 4.11: Controller unit test の Mock を session ベースに更新

**Files:**
- Modify: 6 個の `*ControllerTest.kt`

- [ ] **Step 1: 全 Controller test の `mockk<ApplicationCall>()` → `MindstockSession` 直接生成 に置換**

各テストで:
- `mockkStatic(ApplicationCall::actor)` 行と関連 `every { call.actor(...) }` を削除
- `val call = mockk<ApplicationCall>()` → `val session = MindstockSession(identity = ..., userId = user.id, exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE), callId = Uuid.random())` に変更
- `tx<Any?>(any(), any())` mock を `tx<X, RpcError>(any(), any(), any())` に変更(3 引数)
- Controller インスタンス化引数 `call` → `session`、Controller 内で `userRepository.findById(session.userId!!)` するなら `every { userRepository.findById(user.id) } returns user` を mock

具体例: `UserControllerTest.kt`

```kotlin
class UserControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("rename resolves actor via session and delegates to UserRegisterService") {
            val userRegisterService = mockk<UserRegisterService>(relaxed = true)
            val userRepository = mockk<UserRepository>()
            val database = mockk<Database>()
            val user = User(
                id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
                displayName = DisplayName("Alice"),
            )
            val session = MindstockSession(
                identity = user.authIdentity,
                userId = user.id,
                exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                callId = Uuid.random(),
            )
            every { userRepository.findById(user.id) } returns user

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Unit, RpcError>(any(), any(), any())
            } coAnswers {
                val block = arg<suspend () -> RpcResult<Unit, RpcError>>(2)
                block()
            }

            val impl = UserController(userRegisterService, userRepository, session, database)
            val newName = DisplayName("Bob")
            val r = impl.rename(newName)

            r shouldBe RpcResult.Ok(Unit)
            verify { userRegisterService.rename(user, newName) }
        }
    })
```

- [ ] **Step 2: 残り 5 個の Controller test を同パターンで更新**

- [ ] **Step 3: テスト実行**

Run: `./gradlew :backend:api:test`
Expected: PASS

### Task 4.12: E2E test の helper を session ベースで動くか確認

**Files:**
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/E2eTestSupport.kt`(必要な場合のみ)

- [ ] **Step 1: 全 E2E テスト実行**

Run: `./gradlew :backend:api:integrationTest`
Expected: PASS

もし失敗する場合、よくある原因:
- `application.yaml` から `authConfigure` が消えたが、何か他の参照が残っている → `git grep authConfigure`
- E2E テストが `authenticate("user")` realm の存在前提のテストパターンを使っていた → 認証失敗系は 401 WS upgrade 失敗 と同じ挙動になっているか確認

### Task 4.13: Phase 4 commit

- [ ] **Step 1: commit**

Run:
```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(api): ktor-auth を撤去し自作 MindstockAuthPlugin に置換

- MindstockAuthPlugin: WS upgrade 時に JWT 検証 + MindstockSession 組み立て
  Security Invariants 1-7 (spec §4.6) に準拠
- RequireRegisteredUserPlugin: route-scoped で「登録済み user 必須」を担保
- MindstockSession: (identity, userId, exp, callId) を 1 オブジェクトに集約
- Controller は call: ApplicationCall → session: MindstockSession に置換
- tx() に session.exp チェックを追加(WS 中の JWT 期限切れ対策 L2)
- validate {} 内 blocking transaction バグも plugin 移行で自動的に解消
- 旧 AuthConfiguration / JwtAuthConfiguration / ActorResolver / MindstockPrincipal /
  CurrentCall を削除

ref: docs/superpowers/specs/2026-05-29-ktor-configuration-redesign-design.md §4

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 5: 代替ロギング

`tx()` 内で RPC メソッド粒度の構造化 JSON ログを 1 行出力する。

### Task 5.1: `tx()` に構造化ログ出力を追加

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt`

- [ ] **Step 1: Transaction.kt を更新**

```kotlin
@file:Suppress("DEPRECATION")

package net.brightroom.mindstock.configuration.transaction

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}
private val callLogJson = Json { encodeDefaults = true }

@Serializable
private data class TxLogEntry(
    val callId: String,
    val userId: String?,
    val outcome: String, // "Ok" | "Err:<variant>" | "Throwable"
    val elapsedMs: Long,
)

@OptIn(ExperimentalUuidApi::class)
suspend fun <T> tx(
    database: Database,
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> {
    val start = Clock.System.now()
    val now = start
    if (now > session.exp) {
        emitLog(session, start, outcome = "Err:Unauthorized(expired)")
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        val result = supervisorScope {
            newSuspendedTransaction(db = database) { block() }
        }
        emitLog(session, start, outcome = when (result) {
            is RpcResult.Ok -> "Ok"
            is RpcResult.Err -> "Err:${result.error::class.simpleName}"
        })
        result
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId}" }
        emitLog(session, start, outcome = "Throwable:${e::class.simpleName}")
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun emitLog(session: MindstockSession, start: kotlinx.datetime.Instant, outcome: String) {
    val elapsedMs = (Clock.System.now() - start).inWholeMilliseconds
    val entry = TxLogEntry(
        callId = session.callId.toString(),
        userId = session.userId?.toString(),
        outcome = outcome,
        elapsedMs = elapsedMs,
    )
    logger.info { "rpc call ${callLogJson.encodeToString(entry)}" }
}
```

- [ ] **Step 2: コンパイル + テスト**

Run: `./gradlew :backend:api:check`
Expected: BUILD SUCCESSFUL

### Task 5.2: Phase 5 commit と最終確認

- [ ] **Step 1: `application.yaml` が想定の module だけ残しているか確認**

Run: `grep "Configuration" backend/api/src/main/resources/application.yaml`
Expected: 以下の 4 行のみ:

```
      - "net.brightroom.mindstock.configuration.di.DependenciesConfigurationKt.dependenciesConfigure"
      - "net.brightroom.mindstock.configuration.migration.MigrationConfigurationKt.migrationConfigure"
      - "net.brightroom.mindstock.configuration.external.exposed.ExposedConfigurationKt.exposedConfigure"
      - "net.brightroom.mindstock.configuration.routing.RoutingConfigurationKt.routingConfigure"
```

- [ ] **Step 2: 全テスト最終実行**

Run: `./gradlew :backend:api:check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: commit**

Run:
```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(api): tx() に RPC メソッド粒度の構造化 JSON ログを追加

CallLogging plugin 廃止後の代替として、tx() が各 RPC メソッドの
callId / userId / outcome (Ok/Err variant) / elapsedMs を 1 行 JSON で出力する。
WS upgrade 単位ではなく RPC method 単位の粒度。

ref: docs/superpowers/specs/2026-05-29-ktor-configuration-redesign-design.md §2.1

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# 自己レビューチェックリスト(実装者向け)

各 phase 終了時に確認:

- [ ] `./gradlew :backend:api:check` が green
- [ ] `git grep -E "throw (Unauthorized|NotFound)Exception"` で本体コードに残骸がないこと(Phase 2 完了後)
- [ ] `git grep "authenticate\\(\"" -- backend/api/src/main` で `authenticate("user")` パターンが残っていないこと(Phase 4 完了後)
- [ ] `git grep "ApplicationCall::actor"` で extension の参照が残っていないこと(Phase 4 完了後)
- [ ] `git grep "StatusPages\\|RpcExceptions\\|ErrorConfiguration\\|CallLogging\\|CallId\\|DoubleReceive\\|ExposedTransactionPlugin\\|koin"` で死コードが完全に消えていること

# 想定外の事象とリカバリ

- **`provide<T> { resolve<U>() }` で型推論が効かない**: 明示的に `provide<UserControllerFactory>(...) { ... }` と書く。それでも駄目なら `resolve<UserRegisterService>()` のように山形括弧で明示
- **`testApplication` が integration tag 経由でしか動かない**: `MindstockAuthPluginTest` は `@Tags("integration")` を付けて `:backend:api:integrationTest` ターゲットで実行する
- **kotlinx-rpc が `RpcResult<T?, RpcError>` のような nullable type を serialize できない**: `Stock?` を `findOf()` 等で返す場合に発生する可能性あり。失敗したら `data class FindOfResult(val household: Household? = null)` で包む方針に切り替え
