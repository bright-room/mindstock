# WS-RPC トランスポート再設計 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** kotlinx-rpc を単一 WS エンドポイント `/api/rpc` に集約し、登録必須の認可をルーティング層からアプリ層(guard ヘルパー)へ移すことで、route-scoped ガードが WS upgrade で漏れる不具合を構造ごと除去する。

**Architecture:** 認証(JWT)はハンドシェイクで `MindstockAuthPlugin`(現行)が担い、有効 JWT なら未登録でも接続可。登録要否は各 RPC メソッドが `requireRegistered`(既定・fail-closed)/ `allowUnregistered`(register・whoami のみ)で宣言。未登録/登録済みの判定は新設 `SessionRpcService.whoami()`。frontend は単一接続 + boot で whoami 分岐。

**Tech Stack:** Kotlin Multiplatform, kotlinx-rpc 0.10.2, Ktor(server CIO / client CIO), Kotest(backend FunSpec), kotlin.test + Kotest assertions(frontend commonTest), Exposed/Postgres(無関係)。

**Spec:** [docs/superpowers/specs/2026-06-06-ws-rpc-transport-redesign-design.md](../specs/2026-06-06-ws-rpc-transport-redesign-design.md)

**Branch:** `feat/ws-rpc-transport-redesign`(`main` から分岐済み。設計ドキュメントは commit 済み)

---

## File Structure

**:rpc(新規)**
- `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/session/SessionStatus.kt` — `Registered(Resident) | Unregistered` の sealed wire 型
- `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/session/SessionRpcService.kt` — `@Rpc whoami()`

**:backend:api(変更/新規/削除)**
- 変更 `configuration/guard/SessionGuard.kt` — `guarded` を `requireRegistered` + `allowUnregistered` に置換
- 新規 `presentation/rpc/session/SessionController.kt` — whoami 実装
- 変更 `configuration/routing/RoutingConfiguration.kt` — 単一 `/api/rpc`、全サービス相乗り、`RequireRegisteredUser`/`route("")`/per-path 廃止
- 変更 全 Controller(Catalog/Household/HouseholdRegister/Product/ProductRegister/Stock/StockRegister/ResidentRegister)— guard ヘルパー移行
- 削除 `configuration/auth/RequireRegisteredUserPlugin.kt` + `configuration/auth/RequireRegisteredUserPluginTest.kt`
- 削除 `presentation/rpc/resident/ResidentController.kt` + `:rpc` の `ResidentRpcService.kt`(me() 廃止)
- 変更 `configuration/auth/SessionAccess.kt` — 未使用化する `requireResidentId()` を削除
- 変更 `configuration/guard/SessionGuardTest.kt` — 新ヘルパーのテストへ
- 新規 `backend/api/src/test/.../e2e/rpc/SingleEndpointRpcTest.kt` — 単一エンドポイントの e2e

**:frontend(変更)**
- 変更 `core/rpc/RpcClientProvider.kt` — 単一接続(connect/service/close)
- 変更 `core/rpc/RpcClientProviderTest.kt` — probeHeaders を `/api/rpc` 単一に
- 変更 `app/AuthViewModel.kt`(`AuthDeps` + boot 分岐)
- 変更 `app/AuthViewModelTest.kt`(fake と分岐テスト)
- 変更 `WebAuthDeps.kt`(fetchSessionStatus 実装)
- 変更 ルール doc `.claude/rules/frontend-rpc-and-error.md`

---

## Phase 1 — :rpc 契約

### Task 1: SessionStatus / SessionRpcService

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/session/SessionStatus.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/session/SessionRpcService.kt`

- [ ] **Step 1: SessionStatus を作成**

```kotlin
package net.brightroom.mindstock.rpc.session

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.resident.Resident

/** 現在の接続の登録状態。boot 時の分岐に使う wire 型。 */
@Serializable
sealed interface SessionStatus {
    @Serializable
    data class Registered(
        val resident: Resident,
    ) : SessionStatus

    @Serializable
    data object Unregistered : SessionStatus
}
```

- [ ] **Step 2: SessionRpcService を作成**

```kotlin
package net.brightroom.mindstock.rpc.session

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface SessionRpcService {
    /** 現在の接続の登録状態を返す。認証済みなら未登録でも呼べる。 */
    suspend fun whoami(): RpcResult<SessionStatus, RpcError>
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :rpc:compileKotlinJvm`
Expected: BUILD SUCCESSFUL(`@Rpc` の codegen が通る)

- [ ] **Step 4: Commit**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/session/
git commit -m "feat(rpc): SessionRpcService.whoami と SessionStatus を追加"
```

---

## Phase 2 — backend guard ヘルパー(TDD)

### Task 2: `requireRegistered` / `allowUnregistered`

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuardTest.kt`

- [ ] **Step 1: テストを新ヘルパー仕様に書き換える(まず失敗させる)**

`SessionGuardTest.kt` を全置換:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.guard

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class SessionGuardTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val residentId = ResidentId.create()

        fun registered(exp: kotlin.time.Instant = Clock.System.now().plus(1.hours)) =
            MindstockSession.Registered(identity, residentId, exp, Uuid.random())

        fun unregistered(exp: kotlin.time.Instant = Clock.System.now().plus(1.hours)) =
            MindstockSession.Unregistered(identity, exp, Uuid.random())

        // ---- allowUnregistered ----
        test("allowUnregistered: 未登録でも block を実行する") {
            allowUnregistered(unregistered()) { RpcResult.Ok(42) } shouldBe RpcResult.Ok(42)
        }

        test("allowUnregistered: 登録済みでも block を実行する") {
            allowUnregistered(registered()) { RpcResult.Ok(7) } shouldBe RpcResult.Ok(7)
        }

        test("allowUnregistered: 期限切れは Unauthorized で短絡") {
            var ran = false
            val r = allowUnregistered<Unit>(unregistered(exp = Clock.System.now().minus(1.hours))) {
                ran = true
                RpcResult.Ok(Unit)
            }
            ran shouldBe false
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        // ---- requireRegistered ----
        test("requireRegistered: 登録済みは residentId を渡して block 実行") {
            var seen: ResidentId? = null
            val r = requireRegistered(registered()) { id ->
                seen = id
                RpcResult.Ok(1)
            }
            seen shouldBe residentId
            r shouldBe RpcResult.Ok(1)
        }

        test("requireRegistered: 未登録は Unauthorized(block は実行されない / fail-closed)") {
            var ran = false
            val r = requireRegistered<Unit>(unregistered()) {
                ran = true
                RpcResult.Ok(Unit)
            }
            ran shouldBe false
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        test("requireRegistered: 期限切れは Unauthorized で短絡") {
            val r = requireRegistered<Unit>(registered(exp = Clock.System.now().minus(1.hours))) {
                RpcResult.Ok(Unit)
            }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        // ---- 例外翻訳(requireRegistered 経由で共通処理を確認)----
        test("IllegalArgumentException は BadRequest") {
            val r = requireRegistered<Unit>(registered()) { throw IllegalArgumentException("bad") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.BadRequest>()
        }

        test("ResourceNotFoundException は NotFound") {
            val r = requireRegistered<Unit>(registered()) { throw ResourceNotFoundException("x not found") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.NotFound>()
        }

        test("MembershipRequiredException は Unauthorized") {
            val r = requireRegistered<Unit>(registered()) { throw MembershipRequiredException("not member") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        test("DuplicateJanException は Conflict") {
            val r = requireRegistered<Unit>(registered()) { throw DuplicateJanException("dup") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Conflict>()
        }

        test("想定外例外は Internal") {
            val r = requireRegistered<Unit>(registered()) { throw RuntimeException("boom") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Internal>()
        }

        test("CancellationException は握り潰さず再 throw する") {
            shouldThrow<CancellationException> {
                requireRegistered<Unit>(registered()) { throw CancellationException("cancelled") }
            }
        }
    })
```

- [ ] **Step 2: テストを実行して失敗を確認**

Run: `./gradlew :backend:api:test --tests "net.brightroom.mindstock.configuration.guard.SessionGuardTest"`
Expected: コンパイルエラー(`requireRegistered`/`allowUnregistered` 未定義)

- [ ] **Step 3: SessionGuard.kt を全置換**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.guard

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.CannotArchiveWithStockException
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.exception.InsufficientStockException
import net.brightroom.mindstock.domain.exception.InvitationInvalidException
import net.brightroom.mindstock.domain.exception.LastOwnerException
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * RPC message 単位の認可ガード + 失効ガード + 例外→RpcError 翻訳。
 *
 * 認可は 2 つのヘルパーで表す(登録ガードは route ではなくここ=アプリ境界で行う):
 * - [requireRegistered] 既定。登録済み必須。Unregistered は Unauthorized で短絡(fail-closed)。
 * - [allowUnregistered] 認証のみ(未登録 OK)。register / whoami だけが使う。
 *
 * 共通処理([runGuarded]):接続時に保存した session.exp を現在時刻と比較し失効なら短絡(WS は
 * 張りっぱなしのため upgrade 時の 1 回検証では取りこぼす)。supervisorScope で例外 leak を防ぎ、
 * ドメイン例外を RpcError に翻訳する(DB transaction は張らない。境界は DataSource 自前)。
 * IdP 側の即時失効(revocation)は対象外。守るのは JWT の有効期限切れのみ。
 */
suspend fun <T : Any> allowUnregistered(
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> = runGuarded(session, block)

suspend fun <T : Any> requireRegistered(
    session: MindstockSession,
    block: suspend (ResidentId) -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> =
    runGuarded(session) {
        when (session) {
            is MindstockSession.Registered -> block(session.residentId)
            is MindstockSession.Unregistered -> RpcResult.Err(RpcError.Unauthorized(reason = "registration required"))
        }
    }

private suspend fun <T : Any> runGuarded(
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> {
    if (Clock.System.now() > session.exp) {
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        supervisorScope { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        RpcResult.Err(RpcError.BadRequest(field = "request", reason = e.message ?: "invalid request"))
    } catch (e: ResourceNotFoundException) {
        RpcResult.Err(RpcError.NotFound(message = e.message ?: "not found"))
    } catch (e: OwnerRequiredException) {
        RpcResult.Err(RpcError.Unauthorized(reason = e.message ?: "owner required"))
    } catch (e: MembershipRequiredException) {
        RpcResult.Err(RpcError.Unauthorized(reason = e.message ?: "membership required"))
    } catch (e: LastOwnerException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "last owner"))
    } catch (e: DuplicateJanException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "duplicate"))
    } catch (e: CannotArchiveWithStockException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "cannot archive with stock"))
    } catch (e: InsufficientStockException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "insufficient stock"))
    } catch (e: InvitationInvalidException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "invitation invalid"))
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId}" }
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}
```

- [ ] **Step 4: テストを実行して成功を確認**

Run: `./gradlew :backend:api:test --tests "net.brightroom.mindstock.configuration.guard.SessionGuardTest"`
Expected: PASS(12 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuardTest.kt
git commit -m "feat(api): guarded を requireRegistered/allowUnregistered に置換(認可をアプリ境界へ)"
```

---

## Phase 3 — backend Controller 移行 + 単一エンドポイント

> このフェーズはコンパイルが一旦壊れる(`guarded` 削除済み・参照が残る)。Task 3→6 を続けて行い、Task 6 末で全体ビルド + e2e を回す。途中の commit は Task ごと。

### Task 3: SessionController(whoami)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/session/SessionController.kt`

- [ ] **Step 1: SessionController を作成**

```kotlin
package net.brightroom.mindstock.presentation.rpc.session

import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.allowUnregistered
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.session.SessionRpcService
import net.brightroom.mindstock.rpc.session.SessionStatus

class SessionController(
    private val residentService: ResidentService,
    private val session: MindstockSession,
) : SessionRpcService {
    override suspend fun whoami(): RpcResult<SessionStatus, RpcError> =
        allowUnregistered(session) {
            when (session) {
                is MindstockSession.Registered ->
                    RpcResult.Ok(SessionStatus.Registered(residentService.me(session.residentId)))

                is MindstockSession.Unregistered ->
                    RpcResult.Ok(SessionStatus.Unregistered)
            }
        }
}
```

- [ ] **Step 2: Commit(ビルドは Task 5 後にまとめて確認)**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/session/SessionController.kt
git commit -m "feat(api): whoami の SessionController を追加"
```

### Task 4: 各 Controller を guard ヘルパーへ移行

**保護コントローラの機械的変換ルール**(Catalog/Household/HouseholdRegister/Product/ProductRegister/Stock/StockRegister に適用):
1. import 置換: `import net.brightroom.mindstock.configuration.guard.guarded` → `import net.brightroom.mindstock.configuration.guard.requireRegistered`
2. import 削除: `import net.brightroom.mindstock.configuration.auth.requireResidentId`(ある場合)
3. 本文置換: `guarded(session) {` → `requireRegistered(session) { residentId ->`
4. 本文置換: `session.requireResidentId()` → `residentId`

**Files & 各メソッド:**
- `presentation/rpc/catalog/CatalogController.kt`(`search`, `lookupByJan`。requireResidentId は使っていないので import 削除は不要、ルール 1・3 のみ)
- `presentation/rpc/household/HouseholdController.kt`(`list`, `previewInvite`)
- `presentation/rpc/household/HouseholdRegisterController.kt`(`create`, `rename`, `leave`, `changeRole`, `removeMember`, `createInvite`, `revokeInvite`, `join`)
- `presentation/rpc/product/ProductController.kt`(`list`, `listArchived`, `shoppingList`)
- `presentation/rpc/product/ProductRegisterController.kt`(`adopt`, `addCustom`, `changeUnit`, `changeImage`, `changeMinimum`, `archive`, `unarchive`, `setWanted`)
- `presentation/rpc/stock/StockController.kt`(`history`, `activity`)
- `presentation/rpc/stock/StockRegisterController.kt`(`replenish`, `consume`, `correct`)

- [ ] **Step 1: StockController を変換(代表例・完全形)**

```kotlin
package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.requireRegistered
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.stock.ActivityEntry
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import net.brightroom.mindstock.rpc.stock.StockRpcService

class StockController(
    private val stockService: StockService,
    private val session: MindstockSession,
) : StockRpcService {
    override suspend fun history(productId: ProductId): RpcResult<StockMovements, RpcError> =
        requireRegistered(session) { residentId -> RpcResult.Ok(stockService.history(productId, residentId)) }

    override suspend fun activity(householdId: HouseholdId): RpcResult<ActivityFeed, RpcError> =
        requireRegistered(session) { residentId ->
            val stocks = stockService.activity(householdId, residentId)
            val entries =
                stocks.list
                    .flatMap { stock -> stock.movements.list.map { ActivityEntry(stock.product, it) } }
                    .sortedByDescending { it.movement.occurredAt() }
            RpcResult.Ok(ActivityFeed(entries))
        }
}
```

- [ ] **Step 2: 残り 6 つの保護コントローラに上記 4 ルールを適用**

Catalog/Household/HouseholdRegister/Product/ProductRegister/StockRegister の各ファイルで「`guarded(session) {` → `requireRegistered(session) { residentId ->`」「`session.requireResidentId()` → `residentId`」「import の `guarded`→`requireRegistered`、`requireResidentId` import 削除」を機械的に置換する。メソッドの中身・引数・戻り値型は変えない。

- [ ] **Step 3: ResidentRegisterController を変換(registerDisplayName=allowUnregistered / rename=requireRegistered)**

```kotlin
package net.brightroom.mindstock.presentation.rpc.resident

import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.allowUnregistered
import net.brightroom.mindstock.configuration.guard.requireRegistered
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class ResidentRegisterController(
    private val residentRegisterService: ResidentRegisterService,
    private val session: MindstockSession,
) : ResidentRegisterRpcService {
    override suspend fun registerDisplayName(displayName: DisplayName): RpcResult<Resident, RpcError> =
        allowUnregistered(session) {
            when (session) {
                is MindstockSession.Registered -> RpcResult.Err(RpcError.Conflict(reason = "already registered"))
                is MindstockSession.Unregistered -> RpcResult.Ok(residentRegisterService.register(session.identity, displayName))
            }
        }

    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            residentRegisterService.rename(residentId, displayName)
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 4: ResidentController と ResidentRpcService を削除(me 廃止)**

まず参照確認:

Run: `grep -rn "ResidentRpcService\|\.me()" backend frontend --include=*.kt | grep -v "residentService.me\|/build/"`
Expected: `WebAuthDeps.kt`(frontend、Task 8 で whoami へ移行)以外に参照が無いこと。`residentService.me(`(application 層)は残すので除外。

参照が WebAuthDeps だけなら削除:

```bash
git rm backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/resident/ResidentController.kt
git rm rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/resident/ResidentRpcService.kt
```

- [ ] **Step 5: `requireResidentId()` を削除(未使用化)**

`configuration/auth/SessionAccess.kt` から `MindstockSession.requireResidentId()` 関数を削除する(`sessionOf()` は残す)。削除後の `SessionAccess.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.ApplicationCall

/** WS upgrade 時に MindstockAuthPlugin が格納した session を取り出す。 */
fun sessionOf(call: ApplicationCall): MindstockSession = call.attributes[MindstockSessionKey]
```

確認: `grep -rn "requireResidentId" backend --include=*.kt | grep -v /build/` が空であること。

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/ \
        backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/SessionAccess.kt
git add -u
git commit -m "feat(api): 全 Controller を requireRegistered/allowUnregistered へ移行・me/requireResidentId を廃止"
```

### Task 5: RoutingConfiguration を単一エンドポイントへ + RequireRegisteredUser 削除

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt`
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPlugin.kt`
- Delete: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPluginTest.kt`

- [ ] **Step 1: import を差し替え**

`RoutingConfiguration.kt` の import で:
- 削除: `import net.brightroom.mindstock.configuration.auth.RequireRegisteredUserPlugin`
- 削除: `import net.brightroom.mindstock.presentation.rpc.resident.ResidentController`
- 削除: `import net.brightroom.mindstock.rpc.resident.ResidentRpcService`
- 追加: `import net.brightroom.mindstock.presentation.rpc.session.SessionController`
- 追加: `import net.brightroom.mindstock.rpc.session.SessionRpcService`

- [ ] **Step 2: `routing { }` ブロックを単一エンドポイントに全置換**

`fun Application.routingConfigure()` 内の `routing { route("/api/v1") { ... } }` を以下に置換(`install(...)` と `val ... by dependencies` の先取り解決ブロックはそのまま残す):

```kotlin
    routing {
        // 認証必須の単一エンドポイント。全サービスを 1 接続に相乗りする。
        // 登録要否はルートで分けず、各 Controller が requireRegistered/allowUnregistered で表明する。
        rpc("/api/rpc") {
            registerService<SessionRpcService> { SessionController(residentService, sessionOf(call)) }
            registerService<ResidentRegisterRpcService> {
                ResidentRegisterController(residentRegisterService, sessionOf(call))
            }
            registerService<CatalogRpcService> { CatalogController(catalogService, sessionOf(call)) }
            registerService<HouseholdRpcService> {
                HouseholdController(householdService, invitationService, sessionOf(call))
            }
            registerService<HouseholdRegisterRpcService> {
                HouseholdRegisterController(
                    householdRegisterService,
                    createInvitationScenario,
                    revokeInvitationScenario,
                    joinHouseholdScenario,
                    sessionOf(call),
                )
            }
            registerService<ProductRpcService> { ProductController(productService, sessionOf(call)) }
            registerService<ProductRegisterRpcService> {
                ProductRegisterController(productRegisterService, adoptProductScenario, sessionOf(call))
            }
            registerService<StockRpcService> { StockController(stockService, sessionOf(call)) }
            registerService<StockRegisterRpcService> {
                StockRegisterController(stockRegisterService, sessionOf(call))
            }
        }
    }
```

> `residentService` は SessionController が使うので `val residentService: ResidentService by dependencies` は残す。`MindstockAuthPlugin` の install もそのまま(app レベル)。`route("/api/v1")` と `route("")` は消える。

- [ ] **Step 3: RequireRegisteredUserPlugin とそのテストを削除**

```bash
git rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPlugin.kt
git rm backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPluginTest.kt
```

- [ ] **Step 4: backend をコンパイル + spotless**

Run: `./gradlew :backend:api:compileKotlin :backend:api:spotlessApply`
Expected: BUILD SUCCESSFUL(`guarded`/`requireResidentId`/`ResidentRpcService` の未解決参照が無いこと)

- [ ] **Step 5: 既存 backend テスト全実行(回帰確認)**

Run: `./gradlew :backend:api:test`
Expected: BUILD SUCCESSFUL(SessionGuardTest 含む。削除した RequireRegisteredUserPluginTest は無くなっている)

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt
git add -u
git commit -m "feat(api): RPC を単一 /api/rpc に集約し RequireRegisteredUserPlugin を削除"
```

### Task 6: 単一エンドポイントの e2e テスト

**Files:**
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/rpc/SingleEndpointRpcTest.kt`

テスト方針: 本番と同じ CIO 実機サーバ + 実 kRPC client で、単一 `/api/rpc` に `SessionRpcService` と `ResidentRegisterRpcService`(rename=requireRegistered / registerDisplayName=allowUnregistered)を相乗りさせ、1 接続から両方を呼ぶ。JWKS はモック、JWT は `TestJwtIssuer`。

- [ ] **Step 1: e2e テストを作成**

```kotlin
package net.brightroom.mindstock.e2e.rpc

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders.SecWebSocketProtocol
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.configuration.auth.MindstockAuthPlugin
import net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin
import net.brightroom.mindstock.configuration.auth.sessionOf
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.e2e.auth.TestJwtIssuer
import net.brightroom.mindstock.e2e.auth.TestKeyPair
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.presentation.rpc.resident.ResidentRegisterController
import net.brightroom.mindstock.presentation.rpc.session.SessionController
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.session.SessionRpcService
import net.brightroom.mindstock.rpc.session.SessionStatus
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO

class SingleEndpointRpcTest :
    FunSpec({
        val issuer = TestJwtIssuer.DEFAULT_ISSUER
        val audience = TestJwtIssuer.DEFAULT_AUDIENCE
        val resident = Resident(ResidentId.create(), Profile(DisplayName("Alice")))

        fun stubJwkProvider(): JwkProvider =
            mockk<JwkProvider>().also { provider ->
                val jwk = mockk<Jwk>()
                io.mockk.every { jwk.publicKey } returns TestKeyPair.publicKey
                io.mockk.every { provider.get(any<String>()) } returns jwk
            }

        fun residentRepo(registered: Boolean): ResidentRepository =
            mockk<ResidentRepository>().also {
                if (registered) {
                    io.mockk.every { it.findByAuth(any<AuthIdentity>()) } returns resident
                } else {
                    io.mockk.every { it.findByAuth(any<AuthIdentity>()) } throws ResourceNotFoundException("nf")
                }
            }

        fun residentService(): ResidentService =
            mockk<ResidentService>().also { coEvery { it.me(any()) } returns resident }

        fun residentRegisterService(): ResidentRegisterService =
            mockk<ResidentRegisterService>(relaxed = true).also {
                coEvery { it.register(any(), any()) } returns resident
            }

        @OptIn(ExperimentalEncodingApi::class)
        fun bearer(token: String): String =
            "mindstock.bearer." + Base64.UrlSafe.encode(token.encodeToByteArray()).trimEnd('=')

        // registered=true/false でサーバを立て、1 接続で whoami/rename/registerDisplayName を呼ぶブロックを実行。
        suspend fun <T> withConn(
            registered: Boolean,
            withToken: Boolean = true,
            block: suspend (
                session: SessionRpcService,
                register: ResidentRegisterRpcService,
            ) -> T,
        ): T {
            val server =
                embeddedServer(ServerCIO, port = 0) {
                    install(ContentNegotiation) { jsonIo(CustomJson) }
                    install(Krpc) { serialization { json(KrpcJson) } }
                    install(WsSubprotocolEchoPlugin)
                    install(MindstockAuthPlugin) {
                        jwkProvider = stubJwkProvider()
                        this.issuer = issuer
                        this.audience = audience
                        residentRepository = residentRepo(registered)
                    }
                    val rs = residentService()
                    val rrs = residentRegisterService()
                    routing {
                        rpc("/api/rpc") {
                            registerService<SessionRpcService> { SessionController(rs, sessionOf(call)) }
                            registerService<ResidentRegisterRpcService> { ResidentRegisterController(rrs, sessionOf(call)) }
                        }
                    }
                }
            server.start(wait = false)
            val port = server.engine.resolvedConnectors().first().port
            val client = HttpClient(ClientCIO) { installKrpc { serialization { json(KrpcJson) } }; install(ClientWebSockets) }
            return try {
                val rpcClient =
                    client.rpc("ws://127.0.0.1:$port/api/rpc") {
                        if (withToken) {
                            headers.append(SecWebSocketProtocol, "mindstock.v1")
                            headers.append(SecWebSocketProtocol, bearer(TestJwtIssuer.issue(subject = "sub-1")))
                        }
                    }
                block(rpcClient.withService<SessionRpcService>(), rpcClient.withService<ResidentRegisterRpcService>())
            } finally {
                client.close()
                server.stop(0, 0)
            }
        }

        test("未登録: whoami=Unregistered / registerDisplayName 成立 / rename は Unauthorized") {
            runBlocking {
                withConn(registered = false) { sessionSvc, registerSvc ->
                    sessionSvc.whoami() shouldBe RpcResult.Ok(SessionStatus.Unregistered)
                    registerSvc.registerDisplayName(DisplayName("Alice")).shouldBeInstanceOf<RpcResult.Ok<Resident>>()
                    val r = registerSvc.rename(DisplayName("Bob"))
                    (r as RpcResult.Err).error.shouldBeInstanceOf<net.brightroom.mindstock.rpc.result.RpcError.Unauthorized>()
                }
            }
        }

        test("登録済み: whoami=Registered / rename 成立(1 接続で多重化)") {
            runBlocking {
                withConn(registered = true) { sessionSvc, registerSvc ->
                    sessionSvc.whoami() shouldBe RpcResult.Ok(SessionStatus.Registered(resident))
                    registerSvc.rename(DisplayName("Bob")) shouldBe RpcResult.Ok(Unit)
                }
            }
        }

        test("トークン無しはハンドシェイクで接続できない") {
            runBlocking {
                shouldThrow<Throwable> {
                    withConn(registered = false, withToken = false) { sessionSvc, _ -> sessionSvc.whoami() }
                }
            }
        }
    })
```

- [ ] **Step 2: e2e テストを実行**

Run: `./gradlew :backend:api:test --tests "net.brightroom.mindstock.e2e.rpc.SingleEndpointRpcTest"`
Expected: PASS(3 tests)

- [ ] **Step 3: Commit**

```bash
git add backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/rpc/SingleEndpointRpcTest.kt
git commit -m "test(api): 単一 /api/rpc の e2e(whoami/登録要否/相乗り/未認証)を追加"
```

---

## Phase 4 — frontend 単一接続 + boot

### Task 7: RpcClientProvider を単一接続に

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcClientProvider.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcClientProviderTest.kt`

- [ ] **Step 1: テストを単一エンドポイント仕様に更新(まず失敗させる)**

`RpcClientProviderTest.kt` を全置換:

```kotlin
package net.brightroom.mindstock.frontend.core.rpc

import io.kotest.matchers.collections.shouldContainExactly
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RpcClientProviderTest {
    @Test
    fun connect_sends_app_and_bearer_subprotocols_to_single_endpoint() =
        runTest {
            var capturedPath = ""
            var captured: List<String> = emptyList()
            val engine =
                MockEngine { req ->
                    capturedPath = req.url.encodedPath
                    captured = req.headers.getAll(HttpHeaders.SecWebSocketProtocol) ?: emptyList()
                    respond("")
                }
            val provider = RpcClientProvider(HttpClient(engine), baseUrl = "ws://localhost")
            provider.probeHeaders("jwt-token")
            capturedPath shouldBeEndpoint "/api/rpc"
            // "jwt-token" の base64url(no pad) = "and0LXRva2Vu"
            captured shouldContainExactly listOf("mindstock.v1", "mindstock.bearer.and0LXRva2Vu")
        }
}

private infix fun String.shouldBeEndpoint(expected: String) {
    if (this != expected) throw AssertionError("path was '$this', expected '$expected'")
}
```

- [ ] **Step 2: テスト実行して失敗を確認**

Run: `./gradlew :frontend:compileTestKotlinWasmJs` もしくは `:frontend:jvmTest`(該当ターゲット)
Expected: コンパイルエラー(`probeHeaders` の引数が変わる)

> 注: frontend テストの実行ターゲットは既存設定に従う(`./gradlew :frontend:allTests` か個別ターゲット)。WasmJs フルビルドは OOM のため避け、commonTest を回せるターゲットを使う(memory: local-build-tips)。

- [ ] **Step 3: RpcClientProvider を全置換**

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
import kotlinx.rpc.withService
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 認証済み Krpc クライアントを単一接続で開く。全サービスを 1 本の WS(/api/rpc)に相乗りさせ、
 * [service] で各 @Rpc サービスを取り出す。トークンは subprotocol で運ぶ。
 * baseUrl は ws:// or wss://。
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

    @PublishedApi
    internal var client: KtorRpcClient? = null

    /** 単一の認証済み接続を開く(既存があれば閉じて張り直す)。 */
    fun connect(accessToken: String) {
        close()
        val b64 = encode(accessToken)
        client =
            rpcHttp.rpc("$baseUrl/api/rpc") {
                headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
                headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.$b64")
            }
    }

    /** 接続済み client からサービスを取得。connect 前に呼ぶと例外。 */
    inline fun <reified T : Any> service(): T =
        requireNotNull(client) { "rpc not connected" }.withService<T>()

    /** Test helper: 同一ヘッダで 1 回 GET し MockEngine に検査させる。 */
    internal suspend fun probeHeaders(accessToken: String) {
        val b64 = encode(accessToken)
        rawHttp.get("$baseUrl/api/rpc") {
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.$b64")
        }
    }

    fun close() {
        client?.close("reauth or logout")
        client = null
    }

    private fun encode(token: String): String = Base64.UrlSafe.encode(token.encodeToByteArray()).trimEnd('=')
}
```

- [ ] **Step 4: テスト実行して成功を確認**

Run: 上記と同じターゲット
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcClientProvider.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcClientProviderTest.kt
git commit -m "feat(frontend): RpcClientProvider を単一 /api/rpc 接続に"
```

### Task 8: boot を whoami ベースに

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelTest.kt`
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/WebAuthDeps.kt`

- [ ] **Step 1: AuthViewModelTest を whoami 分岐仕様へ更新(まず失敗させる)**

`AuthViewModelTest.kt` の `FakeAuthDeps` と各テストを更新する。`fetchMe` を `fetchSessionStatus` に置換し、3 分岐(Registered→Ready / Unregistered→NeedOnboarding / 例外→Failed)を検証する。`FakeAuthDeps` の該当箇所:

```kotlin
import net.brightroom.mindstock.rpc.session.SessionStatus
// ...
class FakeAuthDeps(
    private val path: String,
    private val token: String?,
    private val status: SessionStatus? = null,   // null なら fetchSessionStatus で例外
    // ... 既存フラグ(redirectCalled / onAuthenticatedCalled など)はそのまま
) : AuthDeps {
    // ... currentPath / handleCallback / loadValidToken / redirectToAuthorize は既存のまま

    override suspend fun fetchSessionStatus(token: Tokens): SessionStatus =
        status ?: throw RuntimeException("boot failed")

    override fun onAuthenticated(resident: Resident) {
        onAuthenticatedCalled = true
    }
}
```

テスト本体(既存 `valid_token_and_me_ok_becomes_ready` / `..._throws_becomes_onboarding` を置換):

```kotlin
@Test
fun registered_becomes_ready() =
    runTest {
        val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
        val deps = FakeAuthDeps(path = "/", token = "tok", status = SessionStatus.Registered(resident))
        val vm = AuthViewModel(deps)
        vm.boot()
        deps.onAuthenticatedCalled shouldBe true
        vm.state.value.shouldBeInstanceOf<AuthState.Ready>()
    }

@Test
fun unregistered_becomes_onboarding() =
    runTest {
        val deps = FakeAuthDeps(path = "/", token = "tok", status = SessionStatus.Unregistered)
        val vm = AuthViewModel(deps)
        vm.boot()
        vm.state.value.shouldBeInstanceOf<AuthState.NeedOnboarding>()
    }

@Test
fun whoami_failure_becomes_failed() =
    runTest {
        val deps = FakeAuthDeps(path = "/", token = "tok", status = null)
        val vm = AuthViewModel(deps)
        vm.boot()
        vm.state.value.shouldBeInstanceOf<AuthState.Failed>()
    }
```

(`no_token_redirects_to_authorize_and_stays_booting` は変更不要。)

- [ ] **Step 2: テスト実行して失敗を確認**

Run: frontend commonTest 該当ターゲット
Expected: コンパイルエラー(`fetchSessionStatus` 未定義 / `AuthDeps.fetchMe` 参照)

- [ ] **Step 3: AuthViewModel(AuthDeps + boot)を更新**

`AuthDeps` インターフェースの `fetchMe` を置換:

```kotlin
import net.brightroom.mindstock.rpc.session.SessionStatus
// ...
    /** 単一接続を張り whoami を呼んで登録状態を返す。失敗時 throw。 */
    suspend fun fetchSessionStatus(token: Tokens): SessionStatus
```
(`import net.brightroom.mindstock.domain.model.resident.Resident` は `onAuthenticated(resident: Resident)` で引き続き使う。)

`boot()` の try ブロックを置換:

```kotlin
        try {
            when (val status = deps.fetchSessionStatus(token)) {
                is SessionStatus.Registered -> {
                    deps.onAuthenticated(status.resident)
                    _state.value = AuthState.Ready
                }
                is SessionStatus.Unregistered -> {
                    _state.value = AuthState.NeedOnboarding
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // 登録状態は whoami が明示的に返すため、ここに来るのは通信失敗等の本当のエラー。
            _state.value = AuthState.Failed("起動に失敗しました")
        }
```

`AuthDeps` の KDoc コメント「registered 判定は me() の throw を未登録に倒す」も whoami ベースの説明に更新する。

- [ ] **Step 4: WebAuthDeps を更新**

`fetchMe` を `fetchSessionStatus` に置換:

```kotlin
import net.brightroom.mindstock.rpc.session.SessionRpcService
import net.brightroom.mindstock.rpc.session.SessionStatus
// 削除: import net.brightroom.mindstock.rpc.resident.ResidentRpcService
// ...
    override suspend fun fetchSessionStatus(token: Tokens): SessionStatus {
        rpc.connect(token.accessToken)
        return when (val r = rpc.service<SessionRpcService>().whoami()) {
            is RpcResult.Ok -> r.value
            is RpcResult.Err -> error("whoami failed: ${r.error}")
        }
    }
```
(`import kotlinx.rpc.withService` は不要になるので削除。`rpc.service<T>()` が内部で withService する。)

- [ ] **Step 5: frontend commonTest 実行 + コンパイル**

Run: `./gradlew :frontend:compileKotlinWasmJs`(コンパイル)+ commonTest 該当ターゲット
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelTest.kt \
        frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/WebAuthDeps.kt
git commit -m "feat(frontend): boot を whoami ベースの登録判定に(単一接続)"
```

### Task 9: ルール doc 更新

**Files:**
- Modify: `.claude/rules/frontend-rpc-and-error.md`

- [ ] **Step 1: 「未登録判定」の記述を whoami ベースへ**

`frontend-rpc-and-error.md` の「登録状態判定: `/resident` は登録済み必須ルートのため、未登録ユーザの `me()` は WS ハンドシェイクで拒否され **例外を throw** する…」の段落を以下に差し替える:

```markdown
- 登録状態判定: 全 RPC は単一エンドポイント `/api/rpc` に相乗りし、有効 JWT なら未登録でも接続できる。boot は `SessionRpcService.whoami()` の `SessionStatus`(`Registered(resident)` / `Unregistered`)で home / onboarding を分岐する(例外を制御フローに使わない)。通信失敗等の例外は `AuthState.Failed` に倒す。
```

「WS subprotocol でトークンを運ぶ」「RpcClientProvider 経由でのみ開く」の記述はそのまま有効(エンドポイントが単一になった点だけ本文と齟齬がないか確認し、必要なら 1 文修正)。

- [ ] **Step 2: Commit**

```bash
git add .claude/rules/frontend-rpc-and-error.md
git commit -m "docs(rules): 登録判定を whoami ベースへ更新"
```

---

## Phase 5 — 全体検証

### Task 10: フルビルド + 実機スモーク

- [ ] **Step 1: backend フル検証**

Run: `./gradlew :backend:api:test :backend:api:spotlessCheck`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: frontend コンパイル(WasmJs フルビルドは OOM のため compile のみ)**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 実機スモーク(任意・手動)**

backend を起動(`mise exec -- ./gradlew :backend:api:run`)し、実 Zitadel トークンで raw socket により差分確認:
- 未登録トークン + WS `/api/rpc` → 101(接続可)。whoami は Unregistered。
- トークン無し + WS `/api/rpc` → 401。
- Chrome で admin@localhost / Password1! ログイン → 未登録なら onboarding、(DB 登録済みにすれば)home まで到達、コンソールエラー無し。

> 実 token 取得や DB 登録の手順は spec の分析ドキュメント / memory [[backend-ws-auth-401-bug]] を参照。

- [ ] **Step 4: PR 作成**

```bash
git push -u origin feat/ws-rpc-transport-redesign
gh pr create --base main --title "feat(api/frontend): WS-RPC を単一 /api/rpc に集約し認可をアプリ層へ" --body "（spec/plan へのリンクと §概要を記載）"
```

---

## Self-Review

- **Spec coverage**: §3.1 ルーティング=Task5 / §3.2 guard=Task2 / §3.3 whoami=Task1,3 / §3.4 Controller 移行=Task4 / §3.5 me 削除=Task4 / §4 frontend=Task7,8 / §5 versioning=コード変更なし(規約のみ。Task9 doc で触れる) / §6 テスト=Task2,6,7,8 / §7 移行・PR #109 close=完了済 / §9 決定事項=反映済。**カバー済**。
- **Placeholder scan**: 各コード step は完全形 or 機械的置換ルール(Task4 Step2)を明示。`TBD`/`後で` 無し。
- **Type consistency**: `requireRegistered(session){ residentId -> }` / `allowUnregistered(session){ }` / `SessionStatus.Registered(resident)` / `SessionRpcService.whoami()` / `RpcClientProvider.connect()/service<T>()/close()` / `AuthDeps.fetchSessionStatus(): SessionStatus` を全タスクで一致使用。
