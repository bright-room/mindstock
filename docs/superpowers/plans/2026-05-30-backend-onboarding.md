# Backend オンボーディング Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> ⚠️ **このプランは全面改訂が必要(2026-05-30)。着手前に読むこと。**
> brainstorming で設計が更新され、以下が確定した:
> 1. **世帯名**(`household_names` 事実テーブル + `HouseholdName` VO)の追加。`Household(id, name, members)` に変更。`HouseholdRegisterRepository/Service/DataSource.create` を `(ownerId)` → `(ownerId, householdName)` に。
> 2. **session bootstrap を HTTP endpoint 案から破棄** → WS の `bootstrap()` RPC へ。**Task 3/4(HTTP `GET /api/v1/auth/session`)は無効**。
> 3. **`UserPublicRpcService` → `OnboardingRpcService` 改名** + `register` を Scenario 経由 + `bootstrap()` 追加。
> 4. 登録判定は「事実(user 行 + OWNER membership)の存在から導出」。
>
> 正は spec [2026-05-30-frontend-onboarding-foundation-design.md](../specs/2026-05-30-frontend-onboarding-foundation-design.md)。下記 Task 1/2 の Scenario・Controller 差し替えの骨子は流用できるが、Task 3/4 は bootstrap RPC に置き換える。本ファイルは次ステップで書き直す。

**Goal:** 初回サインイン時に User + Household + OWNER membership を原子的かつ冪等に作る `RegisterFirstHouseholdScenario` と、起動時ブートストラップ用の薄い HTTP `GET /api/v1/auth/session` エンドポイントを追加する。

**Architecture:** application 層に初の Scenario(複数 Service をまたぐユースケース)を導入し、`UserPublicController.register` をそれ経由に差し替える。session endpoint は RPC ではなく素の Ktor route で、`MindstockAuthPlugin` の内側 / `RequireRegisteredUserPlugin` の外側に置き、JWT 有効・未登録(`registered=false`)/ 登録済み(`registered=true` + displayName + householdId)/ トークン無効(401)を区別する。

**Tech Stack:** Kotlin/JVM, Ktor, Ktor DI, Exposed(`newSuspendedTransaction`), kotlinx-serialization(CustomJson = snake_case + discriminator NONE), Kotest(FunSpec) + MockK。

**親 spec:** [docs/superpowers/specs/2026-05-30-frontend-onboarding-foundation-design.md](../specs/2026-05-30-frontend-onboarding-foundation-design.md)(§3 Backend スコープ)

---

## 前提・重要な約束

- **冪等性は Scenario レベルで担保する。** `household_memberships` に `UNIQUE(user_id)` を**張らない**(将来「1 世帯 = 複数ユーザー」を塞がないため)。
- `UserRegisterDataSource.register` / `HouseholdRegisterDataSource.create` は無条件 INSERT で冪等性が無い(`users.zitadel_sub` は UNIQUE なので二重 register は DB エラーになる)。よって Scenario が「既に登録済みなら作らない」を判定する。
- 「不在」の判定には `try { ... } catch (e: ResourceNotFoundException) { null }` を使う。これは `MindstockAuthPlugin` が既に採用している既存慣行(`error-handling.md` の「素通し」原則は『Repository の不在を別例外に詰め替えるな』であり、ここは詰め替えではなくユースケース分岐なので整合する)。
- session endpoint のレスポンスは **CustomJson(`ClassDiscriminatorMode.NONE` + `JsonNamingStrategy.SnakeCase` + `encodeDefaults = true`)** でシリアライズされる。よって sealed polymorphic を避け、フラットな `data class` + デフォルト値で表現する。wire 上のキーは snake_case(`display_name` / `household_id`)になる。

---

## File Structure

- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenario.kt` — 原子的・冪等なオンボーディング Scenario
- Create: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenarioTest.kt` — Scenario 単体テスト(core/src/test を新設)
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicController.kt` — register を Scenario 経由に
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicControllerFactory.kt` — 変更不要(確認のみ。後述)
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt` — Scenario の provide + UserPublic factory 配線変更
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicControllerTest.kt` — Scenario を MockK に差し替え
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/http/session/SessionResponse.kt` — wire DTO + resolveSession 純粋ロジック
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/http/session/SessionResponseTest.kt` — resolveSession 単体テスト
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt` — `GET /api/v1/auth/session` route 追加

---

## Task 1: RegisterFirstHouseholdScenario(原子的・冪等なオンボーディング)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenario.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenarioTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenarioTest.kt`:

```kotlin
package net.brightroom.mindstock.application.scenario.onboarding

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RegisterFirstHouseholdScenarioTest :
    FunSpec({

        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1"))
        val displayName = DisplayName("Alice")
        val profile =
            Profile(
                userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                displayName = displayName,
            )

        test("未登録なら User を作り Household を作って Profile を返す") {
            val userRepository = mockk<UserRepository>()
            val userRegisterService = mockk<UserRegisterService>()
            val householdRegisterService = mockk<HouseholdRegisterService>(relaxed = true)

            every { userRepository.findProfileByAuthIdentity(identity) } throws
                ResourceNotFoundException("user not found")
            every { userRegisterService.register(identity, displayName) } returns profile

            val scenario =
                RegisterFirstHouseholdScenario(userRepository, userRegisterService, householdRegisterService)

            scenario.run(identity, displayName) shouldBe profile

            verify(exactly = 1) { userRegisterService.register(identity, displayName) }
            verify(exactly = 1) { householdRegisterService.create(profile.userId) }
        }

        test("既に登録済みなら register も create も呼ばず既存 Profile を返す(冪等)") {
            val userRepository = mockk<UserRepository>()
            val userRegisterService = mockk<UserRegisterService>(relaxed = true)
            val householdRegisterService = mockk<HouseholdRegisterService>(relaxed = true)

            every { userRepository.findProfileByAuthIdentity(identity) } returns profile

            val scenario =
                RegisterFirstHouseholdScenario(userRepository, userRegisterService, householdRegisterService)

            scenario.run(identity, displayName) shouldBe profile

            verify(exactly = 0) { userRegisterService.register(any(), any()) }
            verify(exactly = 0) { householdRegisterService.create(any()) }
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:test --tests "*RegisterFirstHouseholdScenarioTest*"`
Expected: コンパイルエラー(`RegisterFirstHouseholdScenario` 未定義)。

- [ ] **Step 3: 最小実装を書く**

`backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenario.kt`:

```kotlin
package net.brightroom.mindstock.application.scenario.onboarding

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

/**
 * 初回サインインのオンボーディング。User + Household + OWNER membership を 1 ユースケースで揃える。
 *
 * 冪等: 既に当該 identity の User が存在する場合は何も作らず既存 Profile を返す。
 * これにより二重サインアップ(ダブルサブミット等)で世帯が重複しない。トランザクション境界は
 * 呼び出し側(Controller の `tx()`)が張る。
 */
class RegisterFirstHouseholdScenario(
    private val userRepository: UserRepository,
    private val userRegisterService: UserRegisterService,
    private val householdRegisterService: HouseholdRegisterService,
) {
    fun run(
        identity: AuthIdentity,
        displayName: DisplayName,
    ): Profile {
        val existing =
            try {
                userRepository.findProfileByAuthIdentity(identity)
            } catch (e: ResourceNotFoundException) {
                null
            }
        if (existing != null) return existing

        val profile = userRegisterService.register(identity, displayName)
        householdRegisterService.create(profile.userId)
        return profile
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "*RegisterFirstHouseholdScenarioTest*"`
Expected: PASS(2 tests)。

- [ ] **Step 5: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenario.kt \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenarioTest.kt
git commit -m "feat(onboarding): RegisterFirstHouseholdScenario で User+Household を原子的・冪等に作る

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: UserPublicController を Scenario 経由に差し替え

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicControllerTest.kt`

`UserPublicControllerFactory.kt`(`fun interface UserPublicControllerFactory { fun create(session: MindstockSession): UserPublicController }`)は **変更不要**(constructor 引数は DI closure 内で組み立てるため、interface は不変)。

- [ ] **Step 1: 既存テストを Scenario 依存に書き換える(失敗させる)**

`backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicControllerTest.kt` を以下で全置換:

```kotlin
package net.brightroom.mindstock.presentation.rpc.user

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.scenario.onboarding.RegisterFirstHouseholdScenario
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserPublicControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("register は session の AuthIdentity を使い Scenario に委譲する") {
            val scenario = mockk<RegisterFirstHouseholdScenario>()
            val database = mockk<Database>()
            val displayName = DisplayName("Alice")
            val authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1"))
            val expected =
                Profile(
                    userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    displayName = displayName,
                )
            val session =
                MindstockSession(
                    identity = authIdentity,
                    userId = null,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )

            every { scenario.run(authIdentity, displayName) } returns expected

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Profile>(any(), any(), any())
            } coAnswers {
                val block = arg<suspend () -> RpcResult<Profile, RpcError>>(2)
                block()
            }

            val impl = UserPublicController(scenario, session, database)
            impl.register(displayName) shouldBe RpcResult.Ok(expected)
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*UserPublicControllerTest*"`
Expected: コンパイルエラー(`UserPublicController` の constructor が `RegisterFirstHouseholdScenario` を受け取らない)。

- [ ] **Step 3: UserPublicController を Scenario 経由に変更**

`backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicController.kt` を全置換:

```kotlin
package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.scenario.onboarding.RegisterFirstHouseholdScenario
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserPublicRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserPublicController(
    private val registerFirstHouseholdScenario: RegisterFirstHouseholdScenario,
    private val session: MindstockSession,
    private val database: Database,
) : UserPublicRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<Profile, RpcError> =
        tx(database, session) {
            RpcResult.Ok(registerFirstHouseholdScenario.run(session.identity, displayName))
        }
}
```

- [ ] **Step 4: DI を更新する**

`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt` を 2 箇所変更する。

4a. import を追加(他の `application.scenario` import が無いので新規追加):

```kotlin
import net.brightroom.mindstock.application.scenario.onboarding.RegisterFirstHouseholdScenario
```

4b. `// Controller Factory (30)` コメントの直前(Stock Service の provide 群の後)に Scenario の provide を追加:

```kotlin
        // Scenario (25) — 複数 Service をまたぐユースケース
        provide<RegisterFirstHouseholdScenario> {
            RegisterFirstHouseholdScenario(resolve(), resolve(), resolve())
        }
```

4c. 既存の `provide<UserPublicControllerFactory> { ... }` ブロックを以下で置換:

```kotlin
        provide<UserPublicControllerFactory> {
            val scenario = resolve<RegisterFirstHouseholdScenario>()
            val db = resolve<Database>()
            UserPublicControllerFactory { session -> UserPublicController(scenario, session, db) }
        }
```

（`RegisterFirstHouseholdScenario(resolve(), resolve(), resolve())` の 3 つの `resolve()` は宣言順に `UserRepository` / `UserRegisterService` / `HouseholdRegisterService` を解決する。いずれも既に provide 済み。）

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*UserPublicControllerTest*"`
Expected: PASS(1 test)。

- [ ] **Step 6: コミット**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicController.kt \
        backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/user/UserPublicControllerTest.kt
git commit -m "feat(onboarding): UserPublicController.register を RegisterFirstHouseholdScenario 経由に

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: SessionResponse(wire DTO)+ resolveSession(純粋ロジック)

session endpoint のレスポンス型と「`MindstockSession` → レスポンス」変換ロジックを、route 配線から切り離して単体テスト可能にする。route 自体のテストは JWKS 認証モックが重いため Task 4 では手動検証に留め、ロジックの正しさは本タスクの単体テストで担保する。

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/http/session/SessionResponse.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/http/session/SessionResponseTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/http/session/SessionResponseTest.kt`:

```kotlin
package net.brightroom.mindstock.presentation.http.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SessionResponseTest :
    FunSpec({

        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1"))

        fun session(userId: UserId?) =
            MindstockSession(
                identity = identity,
                userId = userId,
                exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                callId = Uuid.random(),
            )

        test("userId が null(未登録)なら registered=false を返し Service を呼ばない") {
            val userService = mockk<UserService>()
            val householdService = mockk<HouseholdService>()

            val result = resolveSession(session(userId = null), userService, householdService)

            result shouldBe SessionResponse(registered = false)
        }

        test("登録済みなら displayName と householdId を載せた registered=true を返す") {
            val userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001"))
            val householdId = HouseholdId(Uuid.parse("00000000-0000-0000-0000-0000000000aa"))
            val userService = mockk<UserService>()
            val householdService = mockk<HouseholdService>()

            every { userService.findById(userId) } returns Profile(userId, DisplayName("Alice"))
            every { householdService.findOf(userId) } returns Household(householdId, HouseholdMembers(emptyList()))

            val result = resolveSession(session(userId = userId), userService, householdService)

            result shouldBe
                SessionResponse(
                    registered = true,
                    displayName = "Alice",
                    householdId = householdId.toString(),
                )
        }
    })
```

> 注: `HouseholdMembers(val list: List<HouseholdMember>)` / `Household(val id, val members)` はいずれも data class(確認済み)。`HouseholdMembers(emptyList())` でよい。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*SessionResponseTest*"`
Expected: コンパイルエラー(`SessionResponse` / `resolveSession` 未定義)。

- [ ] **Step 3: 最小実装を書く**

`backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/http/session/SessionResponse.kt`:

```kotlin
package net.brightroom.mindstock.presentation.http.session

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.configuration.auth.MindstockSession

/**
 * 起動時ブートストラップ用の薄い HTTP レスポンス(`GET /api/v1/auth/session`)。
 *
 * CustomJson(`ClassDiscriminatorMode.NONE` + snake_case + `encodeDefaults`)でシリアライズされるため、
 * sealed polymorphic を避けフラットな data class で表現する。wire のキーは `registered` /
 * `display_name` / `household_id`。registered=false のとき displayName / householdId は空文字(意味なし)。
 */
@Serializable
data class SessionResponse(
    val registered: Boolean,
    val displayName: String = "",
    val householdId: String = "",
)

/**
 * [MindstockSession] からブートストラップ用レスポンスを組み立てる。
 *
 * - userId が null(JWT 有効・User 未登録)→ registered=false
 * - userId 非 null(登録済み)→ displayName / householdId を載せる
 *   (オンボーディング設計上、登録済みなら世帯は必ず存在する)
 *
 * DB アクセスを含むため、呼び出し側が transaction 境界を張る。
 */
fun resolveSession(
    session: MindstockSession,
    userService: UserService,
    householdService: HouseholdService,
): SessionResponse {
    val userId = session.userId ?: return SessionResponse(registered = false)
    val profile = userService.findById(userId)
    val household = householdService.findOf(userId)
    return SessionResponse(
        registered = true,
        displayName = profile.displayName(),
        householdId = household.id.toString(),
    )
}
```

> 注: `profile.displayName` は `DisplayName` VO。文字列は `profile.displayName()`(`operator fun invoke(): String`)で取り出す。`household.id.toString()` は `HouseholdId.toString()`(内部 Uuid の文字列)。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*SessionResponseTest*"`
Expected: PASS(2 tests)。

- [ ] **Step 5: コミット**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/http/session/SessionResponse.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/http/session/SessionResponseTest.kt
git commit -m "feat(onboarding): session bootstrap の SessionResponse と resolveSession を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: session endpoint の route 配線(GET /api/v1/auth/session)

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt`

route のロジックは Task 3 の `resolveSession` で単体テスト済み。本タスクは配線のみで、自動テストは付けない(`MindstockAuthPlugin` の JWKS 検証を通す TestApplication は重く、本プランの ROI に合わない)。代わりに Step 4 で手動検証する。

- [ ] **Step 1: import と依存解決を追加**

`RoutingConfiguration.kt` の import 群に以下を追加:

```kotlin
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.presentation.http.session.resolveSession
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
```

関数冒頭の `by dependencies` 群(`val database: Database by dependencies` がある箇所)に 2 つ追加:

```kotlin
    val userService: UserService by dependencies
    val householdService: HouseholdService by dependencies
```

- [ ] **Step 2: route を追加**

`route("/api/v1") { install(MindstockAuthPlugin) { ... } ... }` の中、`install(MindstockAuthPlugin) { ... }` ブロックの**直後**かつ `route("/user/public") { ... }` の**直前**に以下を挿入(= `MindstockAuthPlugin` の内側 / `RequireRegisteredUserPlugin` の外側):

```kotlin
            // 起動時ブートストラップ。JWT 有効なら 200(registered で登録判定)、無効なら
            // MindstockAuthPlugin が 401 を返す。RequireRegisteredUserPlugin の外側に置くことで
            // 未登録 User も registered=false を受け取れる。
            get("/auth/session") {
                val session = call.attributes[MindstockSessionKey]
                val response =
                    newSuspendedTransaction(db = database) {
                        resolveSession(session, userService, householdService)
                    }
                call.respond(response)
            }
```

（`MindstockSessionKey` は既に import 済み。JWT 無効時は `MindstockAuthPlugin` が `call.respond(HttpStatusCode.Unauthorized)` 済みのため、この handler に到達した時点で session は必ず存在する。）

- [ ] **Step 3: ビルドが通ることを確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 手動検証(ローカル起動 + curl)**

`MindstockAuthPlugin` は token を `Authorization` ヘッダではなく **`Sec-WebSocket-Protocol`**(`mindstock.bearer.<base64url(jwt)>`)からのみ抽出する(`WsBearerTokenExtractor`。REST/Authorization 経路は持たない)。HTTP GET でもこのヘッダは送れる(frontend の `RpcClientFactory.openRaw` が同じ経路を実証済み)ので、session endpoint も同じヘッダで叩く。

ローカルの PostgreSQL + Zitadel を起動した上で backend を起動する(`compose.yml` 利用)。

Run: `./gradlew :backend:api:run`(別ターミナル)

検証(`<token>` は Zitadel から取得した有効な access token):

```bash
# token を base64url(padding 無し)に変換。frontend の Base64.UrlSafe.trimEnd('=') と同じ。
TOKEN='<有効な access token>'
B64=$(python3 -c "import base64,sys;print(base64.urlsafe_b64encode(sys.argv[1].encode()).decode().rstrip('='))" "$TOKEN")

# 1) token 無し → 401
curl -i http://localhost:8080/api/v1/auth/session
# Expected: HTTP/1.1 401

# 2) 有効トークン・未登録 User → 200 {"registered":false,...}
curl -i \
  -H "Sec-WebSocket-Protocol: mindstock.v1" \
  -H "Sec-WebSocket-Protocol: mindstock.bearer.$B64" \
  http://localhost:8080/api/v1/auth/session
# Expected: HTTP/1.1 200, body に "registered":false(snake_case: display_name / household_id)
```

> 補足: 登録済み User(registered=true)の検証は、frontend(Plan 1b)で register を通した後に同じ curl を打つか、1b の E2E に委ねる。route ロジック自体は Task 3 の単体テストで担保済み。

- [ ] **Step 5: コミット**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt
git commit -m "feat(onboarding): GET /api/v1/auth/session ブートストラップ endpoint を配線

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 完了条件

- [ ] `./gradlew :backend:core:test :backend:api:test` がグリーン(`-Dkotest.tags.exclude` のデフォルトで integration を除外した状態)
- [ ] `./gradlew :backend:api:compileKotlin` が成功
- [ ] 手動検証(Task 4 Step 4)で 401 / registered=false が確認できる
- [ ] `RegisterFirstHouseholdScenario` の冪等性テストがグリーン(2 回実行相当=登録済みパスで register/create が呼ばれない)

## このプランで扱わないこと(後続)

- frontend の AuthViewModel / AppSession / RPC 統合 / navigation-compose 導入は **Plan 1b(frontend)** で扱う。frontend から session endpoint を叩く配線(`Sec-WebSocket-Protocol` ヘッダ付き HTTP GET)も 1b のスコープ。
