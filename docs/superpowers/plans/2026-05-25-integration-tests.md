# Plan 7: RPC e2e Integration Tests — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** kotlinx-rpc クライアントから testApplication 越しに 6 RPC service を実 Repository + 実 PostgreSQL に対して動かす結合テスト群を整備し、Plan 6 で導入した transaction / 認証 / StatusPages / Krpc routing が e2e で機能することを実証する。

**Architecture:** `e2eTest { }` scaffold が fresh Testcontainers schema + testApplication + Krpc HTTP client を 1 セットアップし、各テストが `seedX()` ヘルパーで DB をシード → `rpcClient<T>(asUser = ...)` で Bearer 認証付き service handle を取り、RPC method を呼んで戻り値と副作用を assert する。

**Tech Stack:** Kotlin, Ktor 3.5 (server-test-host + client-cio + client-websockets), kotlinx-rpc 0.10.2 (krpc client + server-ktor), Kotest FunSpec, Testcontainers PostgreSQL 18, Exposed v1 JDBC, Flyway (via existing MigrationRunner)

**Spec:** `docs/superpowers/specs/2026-05-25-integration-tests-design.md`

---

## 全体注意

- 作業ブランチは既に `feat/integration-tests`。spec の commit (`78acf74`) が既に乗っている
- パッケージ: `net.brightroom.mindstock.*`
- 配置: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/`
- Plan 5 の `TestContainersPostgres.withFreshSchema` を再利用(`testFixtures(:backend:infrastructure:migration:executor)` 経由でアクセス済)
- 既存 RPC service interface のメソッドシグネチャを `shared/rpc/src/commonMain/kotlin/net/brightroom/mindstock/presentation/rpc/*.kt` で確認しながら書く
- Handler 引数順 / 戻り値型は `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/<area>/*.kt` を直接読む
- Kotest FunSpec(既存 backend テストパターン)で書く。`io.kotest.matchers.shouldBe` などを使う
- 各タスク終端で commit、spotless が落ちないよう注意

---

## File Structure

新規作成:

- `backend/application/api/build.gradle.kts` — modify(test deps 追加)
- `gradle/libs.versions.toml` — modify if needed(`kotlinx-rpc-client/client-ktor`, `ktor.client.cio/websockets/contentNegotiation` が未エイリアス化なら追加)
- `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/E2eTestSupport.kt` — create
- `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/Fixtures.kt` — create
- `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserPublicRpcServiceE2eTest.kt` — create
- `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserRpcServiceE2eTest.kt` — create
- `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/household/HouseholdRpcServiceE2eTest.kt` — create
- `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/catalog/CatalogRpcServiceE2eTest.kt` — create
- `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/product/ProductRpcServiceE2eTest.kt` — create
- `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/stock/StockRpcServiceE2eTest.kt` — create

---

## Task 1: テスト用依存追加

**目的:** Ktor test host + krpc client + WebSocket client を test classpath に揃える。

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `backend/application/api/build.gradle.kts`

- [ ] **Step 1: 既存の alias を確認**

```bash
grep -nE "ktor-client|kotlinx-rpc-client" gradle/libs.versions.toml
grep -nE "ktor.*Lib.client" backend/application/api/build.gradle.kts
```

- [ ] **Step 2: 未定義の alias を `libs.versions.toml` に追加**

`[libraries]` に以下を追加(既にあるならスキップ — diff 取って必要分だけ):

```toml
ktor-client-cio              = { module = "io.ktor:ktor-client-cio",                version.ref = "ktor" }
ktor-client-websockets       = { module = "io.ktor:ktor-client-websockets",         version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
```

`kotlinx-rpc-client` / `kotlinx-rpc-client-ktor` / `kotlinx-rpc-serialization-json` は既存(`libs.versions.toml` 確認済)。

ktor の version catalog が `libs.versions.toml` ではなく `ktorLib` というカスタム catalog(`settings.gradle.kts` で `versionCatalogs { create("ktorLib") }` 経由)で管理されている可能性がある — `settings.gradle.kts` を見て該当 catalog 経由のエイリアスがあれば、それを利用する(`ktorLib.client.cio` 形式)。

- [ ] **Step 3: `backend/application/api/build.gradle.kts` の `dependencies` に追加**

```kotlin
testImplementation(libs.kotlinx.rpc.client)
testImplementation(libs.kotlinx.rpc.client.ktor)
// serialization-json は既存 main 依存なので test では不要(transitive)
testImplementation(ktorLib.client.cio)
testImplementation(ktorLib.client.websockets)
testImplementation(ktorLib.client.contentNegotiation)
// ktorLib.server.testHost は既存
```

カタログ名が違えば実物に合わせる。

- [ ] **Step 4: build 確認**

```
cd /Users/nonaka.koki/dev/ghq/github.com/bright-room/mindstock
./gradlew :backend:application:api:dependencies --configuration testRuntimeClasspath 2>&1 | grep -E "kotlinx-rpc-krpc-client|ktor-client-cio"
```

`kotlinx-rpc-krpc-client` と `ktor-client-cio` が表示されれば OK。

- [ ] **Step 5: Commit**

```
git add gradle/libs.versions.toml backend/application/api/build.gradle.kts
git commit -m "build(test): add kotlinx-rpc client + Ktor WebSocket client deps for e2e tests"
```

---

## Task 2: E2eTestSupport scaffold + smoke test

**目的:** testApplication + Testcontainers + Krpc client を組み上げる 1 ファイルの scaffold を作り、最小スモークテスト(`UserPublicRpcService.register` を 1 回呼ぶ)で全 wiring が動くことを示す。

**Files:**
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/E2eTestSupport.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/SmokeE2eTest.kt`(後で削除する一時ファイル)

- [ ] **Step 1: `E2eTestSupport.kt` を作成**

```kotlin
package net.brightroom.mindstock.e2e

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.testApplication
import javax.sql.DataSource
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json as krpcJson
import kotlinx.rpc.withService
import net.brightroom.mindstock.configuration.di.DependenciesConfigurationKt.dependenciesConfigure
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.infrastructure.migration.executor.MigrationRunner
import net.brightroom.mindstock.infrastructure.migration.executor.TestContainersPostgres
import net.brightroom.mindstock.infrastructure.migration.executor.testHikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * 1 e2e test = 1 fresh PostgreSQL schema + 1 testApplication + 1 krpc HttpClient.
 *
 * 使い方:
 * ```
 * test("...") {
 *     e2eTest {
 *         val owner = seedUser()
 *         val rpc = rpcClient<UserRpcService>(asUser = owner, path = "user")
 *         rpc.rename(DisplayName("Bob"))
 *         // assertions...
 *     }
 * }
 * ```
 */
fun e2eTest(block: suspend E2eContext.() -> Unit) {
    TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
        val dataSource = testHikariDataSource(
            jdbcUrl,
            TestContainersPostgres.username,
            TestContainersPostgres.password,
        )
        try {
            MigrationRunner.migrate(dataSource)
            val database = Database.connect(dataSource)
            testApplication {
                environment {
                    config = MapApplicationConfig(
                        "external.datasource.database.jdbc-url" to jdbcUrl,
                        "external.datasource.database.username" to TestContainersPostgres.username,
                        "external.datasource.database.password" to TestContainersPostgres.password,
                        // application.yaml の他の値は default で OK
                    )
                }
                // application.yaml をベースに上書き
                // (ApplicationConfig YAML 自動 load を testApplication が行うので merge は不要なケースあり。
                //  失敗時は environment { config = ApplicationConfig("application.yaml").mergeWith(...) } を試す)
                val client = createClient {
                    installKrpc {
                        serialization { krpcJson(CustomJson) }
                    }
                    install(WebSockets)
                }
                runBlocking {
                    val ctx = E2eContext(client, database, dataSource)
                    ctx.block()
                }
            }
        } finally {
            dataSource.close()
        }
    }
}

class E2eContext(
    val httpClient: HttpClient,
    val database: Database,
    val dataSource: DataSource,
) {
    /** Bearer = user.id を付与した service handle。 */
    suspend inline fun <reified T : Any> rpcClient(asUser: User, path: String): T =
        httpClient.rpc("/api/v1/$path") {
            headers.append("Authorization", "Bearer ${asUser.id.value}")
        }.withService<T>()

    /** 認証なしの service handle。 */
    suspend inline fun <reified T : Any> publicRpcClient(path: String): T =
        httpClient.rpc("/api/v1/$path").withService<T>()
}
```

**注意点:**
- `installKrpc` と `install(WebSockets)` の順序は `installKrpc` 内で `install(WebSockets)` も呼ぶので、外側 `install(WebSockets)` は不要かもしれない — 実際の挙動を見て調整
- `testApplication` の `application.yaml` 自動 load が environment override と競合する場合、`environment { config = ApplicationConfig("application.yaml").mergeWith(MapApplicationConfig(...)) }` 形式で明示マージ
- `UserId.value` の publicness: `value class UserId(val value: Uuid)` なら `user.id.value` で取れる。`fun invoke(): Uuid` のみなら `user.id().toString()`

実装着手時、UserId VO ファイルを Read で確認:

```bash
cat domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/UserId.kt
```

- [ ] **Step 2: smoke テストを作成**

`backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/SmokeE2eTest.kt`:

```kotlin
package net.brightroom.mindstock.e2e

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService

class SmokeE2eTest : FunSpec({
    test("UserPublicRpcService.register persists a User end-to-end") {
        e2eTest {
            val rpc = publicRpcClient<UserPublicRpcService>(path = "user/public")
            val user = rpc.register(
                displayName = DisplayName("Smoke Test"),
                authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("smoke-sub")),
            )
            user.displayName shouldBe DisplayName("Smoke Test")
            user.authIdentity.subject shouldBe AuthSubject("smoke-sub")
        }
    }
})
```

- [ ] **Step 3: 実行と debugging loop**

```
./gradlew :backend:application:api:test --tests "net.brightroom.mindstock.e2e.SmokeE2eTest"
```

期待: PASS。失敗時の典型と対処:
1. `application.yaml` の jdbc-url override が効かない → ApplicationConfig マージ方式に変更
2. `installKrpc` が `install(WebSockets)` を含むか → 重複 install エラーなら外側を消す
3. Krpc client が server 側の `Krpc` plugin と negotiation できない → server も client も `serialization { json(CustomJson) }` を同じ Json で揃える
4. `Bearer` header が server に届かない → `headers.append(HttpHeaders.Authorization, "Bearer ...")` の Ktor 定数を使う
5. testApplication の `application { module() }` 呼び出しが必要かもしれない — `environment.config = ...` だけだと module 起動しない場合、明示的に `application { ... }` ブロックで module orchestration を呼ぶ。`application.yaml` の modules リストを評価する `EngineMain` 相当が必要

問題が解決できなければ NEEDS_CONTEXT で escalate。

- [ ] **Step 4: smoke テスト後に削除予定の旨を確認、コミット**

smoke テストは今後の Task 4 の正式 `UserPublicRpcServiceE2eTest` で吸収するので、Task 4 完了時に削除する。Plan 7 完了時点で smoke ファイルは残らない。

```
git add backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/E2eTestSupport.kt \
        backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/SmokeE2eTest.kt
git commit -m "test(e2e): add E2eTestSupport scaffold + smoke test proving full RPC wiring"
```

---

## Task 3: Fixtures.kt — シードヘルパー

**目的:** 各テストが共通利用できる、DB に直接データを投入する fixture 関数群。

**Files:**
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/Fixtures.kt`

- [ ] **Step 1: Repository を直接使う方針で実装**

`E2eContext` のメソッドとして提供。Repository の `register*` を呼ぶことで domain invariants も検証される。

```kotlin
package net.brightroom.mindstock.e2e

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@OptIn(ExperimentalUuidApi::class)
private fun shortRandom(): String =
    Uuid.random().toString().replace("-", "").take(8)

fun E2eContext.seedUser(
    displayName: String = "User-${shortRandom()}",
    provider: AuthProvider = AuthProvider.ZITADEL,
    subject: String = "sub-${shortRandom()}",
): User =
    transaction(database) {
        UserRegisterRepositoryImpl().register(
            displayName = DisplayName(displayName),
            authIdentity = AuthIdentity(provider, AuthSubject(subject)),
        )
    }

fun E2eContext.seedHousehold(owner: User): Household =
    transaction(database) {
        HouseholdRegisterRepositoryImpl().create(owner)
    }

fun E2eContext.seedCatalogItem(
    name: String = "Item-${shortRandom()}",
    unit: String = "個",
    registeredBy: User,
): CatalogItem =
    transaction(database) {
        CatalogItemRegisterRepositoryImpl().register(
            name = CatalogItemName(name),
            unit = CatalogItemUnit(unit),
            registeredBy = registeredBy,
        )
    }

fun E2eContext.seedProduct(
    household: Household,
    catalogItem: CatalogItem,
    adoptedBy: User,
): Product =
    transaction(database) {
        ProductRegisterRepositoryImpl().adopt(household, catalogItem, adoptedBy)
    }
```

**注意点:**
- `UserRegisterRepositoryImpl.register(...)` 等の正確なシグネチャを `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/<area>/` で確認。引数名・型が違えば修正
- `HouseholdRegisterRepositoryImpl.create(owner)` のシグネチャ確認(`Plan 6 Task 7` で確認した「`CreateHouseholdHandler.handle(owner)`」なら Repository は `create(owner)`)
- もし Repository が `register(... )` 等の引数順を求める場合は実装に合わせる
- `ProductRegisterRepositoryImpl.adopt(...)` の正確な戻り型(`Product` か `ProductId` か等)を確認

- [ ] **Step 2: 既存の smoke test を fixture を使う形に書き換えて動作確認(任意)**

スキップ可。次タスクでフルテストに含まれる。

- [ ] **Step 3: コンパイル確認**

```
./gradlew :backend:application:api:compileTestKotlin
```

通れば OK。

- [ ] **Step 4: Commit**

```
git add backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/Fixtures.kt
git commit -m "test(e2e): add seed helpers (seedUser/seedHousehold/seedCatalogItem/seedProduct)"
```

---

## Task 4: UserPublicRpcServiceE2eTest

**目的:** 認証不要 RPC の e2e テスト。Smoke テストを正式化する。

**Files:**
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserPublicRpcServiceE2eTest.kt`
- Delete: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/SmokeE2eTest.kt`(Task 2 で作った一時ファイル)

**カバーするケース(2)**:
1. happy: register が User を返し DB に persist される(別 query で読み戻すか find via UserRepository)
2. domain invariant: 空 `DisplayName` で `register` 呼ぶ → 400 相当(`IllegalArgumentException` がクライアントにどう届くか確認)

- [ ] **Step 1: テストファイルを作成**

```kotlin
package net.brightroom.mindstock.e2e.user

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRepositoryImpl
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class UserPublicRpcServiceE2eTest : FunSpec({

    test("register persists a new User and returns it with assigned id") {
        e2eTest {
            val rpc = publicRpcClient<UserPublicRpcService>(path = "user/public")
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("user-1"))
            val user = rpc.register(
                displayName = DisplayName("Alice"),
                authIdentity = identity,
            )

            user.displayName shouldBe DisplayName("Alice")
            user.authIdentity shouldBe identity

            // DB に persist されたことを別経路で確認
            val readBack = transaction(database) {
                (UserRepositoryImpl() as UserRepository).findById(user.id)
            }
            readBack.shouldNotBeNull()
            readBack.displayName shouldBe DisplayName("Alice")
        }
    }

    test("register with empty displayName fails with a domain invariant error") {
        e2eTest {
            val rpc = publicRpcClient<UserPublicRpcService>(path = "user/public")
            // Domain VO は空文字を弾く想定。DisplayName("") で IllegalArgumentException、
            // それが RPC 越しに何らかの例外として client に届く(kotlinx-rpc の例外伝播)。
            shouldThrowAny {
                rpc.register(
                    displayName = DisplayName(""),
                    authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
                )
            }
        }
    }
})
```

**注意**:
- 2 番目のテストで `DisplayName("")` が **client 側で投げる** か **server 側で投げる**かを確認。`DisplayName` の factory が `require(...)` してるなら client 側で即例外。これは「server 越しのエラー伝播」を検証してないのでテスト価値が低い。
- もし client 側で投げるなら、代わりに「client 側を bypass する `Json.encodeToString` で空文字 DisplayName を直接サーバへ送る」みたいなテストになる。これは複雑なので、**代替案**: `CatalogRpcServiceE2eTest` の `register(空 name)` を server-side invariant ケースとして用意し、UserPublic はテスト 1 本(happy のみ)に減らす。
- 実装時、`DisplayName("")` の挙動を見て、テスト 2 の意味があるか判断。なければ削除して 1 テストにする。

- [ ] **Step 2: 実行**

```
./gradlew :backend:application:api:test --tests "net.brightroom.mindstock.e2e.user.UserPublicRpcServiceE2eTest"
```

期待: PASS(または上記注意に従い 1 テストに削減してから PASS)。

- [ ] **Step 3: Smoke テスト削除**

```bash
git rm backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/SmokeE2eTest.kt
```

- [ ] **Step 4: Commit**

```
git add backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserPublicRpcServiceE2eTest.kt
git rm backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/SmokeE2eTest.kt
git commit -m "test(e2e): cover UserPublicRpcService.register and drop smoke placeholder"
```

---

## Task 5: UserRpcServiceE2eTest

**目的:** 認証必須 RPC の最小例。Bearer token wiring と `ActorResolver` の動作を検証。

**Files:**
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserRpcServiceE2eTest.kt`

**カバーするケース(3)**:
1. happy: 認証済みで `rename` を呼ぶ → displayName が更新される
2. 認証失敗(header なし): `httpClient.rpc()` を Bearer 無しで呼ぶ → 401 / RPC 越しの例外
3. 認証失敗(未知 UserId): 存在しない UUID を Bearer に乗せる → 401

- [ ] **Step 1: テストファイル**

```kotlin
package net.brightroom.mindstock.e2e.user

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRepositoryImpl
import net.brightroom.mindstock.presentation.rpc.UserRpcService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserRpcServiceE2eTest : FunSpec({

    test("rename updates the actor's display name") {
        e2eTest {
            val user = seedUser(displayName = "Old Name")
            val rpc = rpcClient<UserRpcService>(asUser = user, path = "user")

            rpc.rename(DisplayName("New Name"))

            val readBack = transaction(database) {
                (UserRepositoryImpl() as UserRepository).findById(user.id)!!
            }
            readBack.displayName shouldBe DisplayName("New Name")
        }
    }

    test("rename without Authorization header is rejected") {
        e2eTest {
            // Bearer ヘッダ無しで rpc を構築
            val rpc = httpClient.rpc("/api/v1/user").withService<UserRpcService>()
            shouldThrowAny {
                rpc.rename(DisplayName("anyone"))
            }
        }
    }

    test("rename with unknown UserId Bearer is rejected") {
        e2eTest {
            val fakeUser = fakeUserWithId(Uuid.random())
            val rpc = rpcClient<UserRpcService>(asUser = fakeUser, path = "user")
            shouldThrowAny {
                rpc.rename(DisplayName("ghost"))
            }
        }
    }
})

/** ApplicationCall.actor が DB lookup に失敗するパスを試すための、DB に存在しない User インスタンス。 */
@OptIn(ExperimentalUuidApi::class)
private fun fakeUserWithId(uuid: Uuid): User =
    User(
        id = UserId(uuid),
        authIdentity = net.brightroom.mindstock.domain.model.user.auth.AuthIdentity(
            net.brightroom.mindstock.domain.model.user.auth.AuthProvider.ZITADEL,
            net.brightroom.mindstock.domain.model.user.auth.AuthSubject("ghost"),
        ),
        displayName = net.brightroom.mindstock.domain.model.user.DisplayName("ghost"),
    )
```

**注意**: kotlinx-rpc の例外伝播は、サーバ側例外を `SerializableRpcException` 等にラップして client 側に投げ直す。`shouldThrowAny` で曖昧に受けるのは、正確な例外型がドキュメント外に依存するため。実装中に「何の例外が来るか」を見て `shouldThrow<SpecificException>` に絞れるなら絞る(より精密)。

- [ ] **Step 2: 実行**

```
./gradlew :backend:application:api:test --tests "*UserRpcServiceE2eTest"
```

- [ ] **Step 3: Commit**

```
git add backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserRpcServiceE2eTest.kt
git commit -m "test(e2e): cover UserRpcService.rename happy/no-auth/unknown-id"
```

---

## Task 6: HouseholdRpcServiceE2eTest

**目的:** Household CRUD と NotFound を検証。

**カバーするケース(6)**:
1. `findOf` happy: owner 自分の世帯が見える
2. `findOf` 空: 世帯未所属なら null
3. `create` happy: 新規世帯作成 + owner が membership に追加される
4. `invite` happy: 既存世帯に member を invite
5. `invite` NotFound: 存在しない `householdId` → 404 相当の例外
6. `revoke` happy: 既存 member を revoke

**Files:**
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/household/HouseholdRpcServiceE2eTest.kt`

- [ ] **Step 1: テストファイル**

```kotlin
package net.brightroom.mindstock.e2e.household

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedHousehold
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.presentation.rpc.HouseholdRpcService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HouseholdRpcServiceE2eTest : FunSpec({

    test("findOf returns the household the actor owns") {
        e2eTest {
            val owner = seedUser()
            val expected = seedHousehold(owner)
            val rpc = rpcClient<HouseholdRpcService>(asUser = owner, path = "household")

            val found = rpc.findOf()

            found.shouldNotBeNull()
            found.id shouldBe expected.id
        }
    }

    test("findOf returns null when actor has no household") {
        e2eTest {
            val orphan = seedUser()
            val rpc = rpcClient<HouseholdRpcService>(asUser = orphan, path = "household")

            rpc.findOf().shouldBeNull()
        }
    }

    test("create makes a new household with the actor as owner") {
        e2eTest {
            val owner = seedUser(displayName = "Owner")
            val rpc = rpcClient<HouseholdRpcService>(asUser = owner, path = "household")

            val household = rpc.create()

            household.members.list.size shouldBe 1
            household.members.list[0].user.id shouldBe owner.id
            household.members.list[0].role shouldBe HouseholdMemberRole.OWNER
        }
    }

    test("invite adds a new member to an existing household") {
        e2eTest {
            val owner = seedUser()
            val household = seedHousehold(owner)
            val invitee = seedUser(displayName = "Invitee")
            val rpc = rpcClient<HouseholdRpcService>(asUser = owner, path = "household")

            rpc.invite(
                householdId = household.id,
                invitee = invitee.id,
                role = HouseholdMemberRole.MEMBER,
            )

            // 副作用検証: もう一度 findOf を invitee 認証で呼んで世帯が見えること
            val invRpc = rpcClient<HouseholdRpcService>(asUser = invitee, path = "household")
            val found = invRpc.findOf()
            found.shouldNotBeNull()
            found.id shouldBe household.id
        }
    }

    test("invite to an unknown householdId throws NotFound") {
        e2eTest {
            val owner = seedUser()
            val invitee = seedUser()
            val rpc = rpcClient<HouseholdRpcService>(asUser = owner, path = "household")

            shouldThrowAny {
                rpc.invite(
                    householdId = HouseholdId(Uuid.random()),
                    invitee = invitee.id,
                    role = HouseholdMemberRole.MEMBER,
                )
            }
        }
    }

    test("revoke removes a member from the household") {
        e2eTest {
            val owner = seedUser()
            val household = seedHousehold(owner)
            val member = seedUser()
            val rpc = rpcClient<HouseholdRpcService>(asUser = owner, path = "household")
            rpc.invite(household.id, member.id, HouseholdMemberRole.MEMBER)

            rpc.revoke(household.id, member.id)

            // 副作用: revoke された member が own household を持たない
            val memberRpc = rpcClient<HouseholdRpcService>(asUser = member, path = "household")
            memberRpc.findOf().shouldBeNull()
        }
    }
})
```

**注意**: `HouseholdRpcService.invite` の正確なシグネチャ確認(`shared/rpc/.../HouseholdRpcService.kt` を Read)。role 引数の有無、戻り型が `Unit` か `HouseholdMember` か等。

- [ ] **Step 2: 実行 → 期待: 6 テスト PASS**

```
./gradlew :backend:application:api:test --tests "*HouseholdRpcServiceE2eTest"
```

- [ ] **Step 3: Commit**

```
git add backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/household/
git commit -m "test(e2e): cover HouseholdRpcService (findOf/create/invite/revoke + NotFound)"
```

---

## Task 7: CatalogRpcServiceE2eTest

**目的:** Catalog の検索・登録・改訂を検証 + invariant 例外。

**カバーするケース(6)**:
1. `register` happy: 新規 CatalogItem が作成される
2. `findById` happy: 既存 CatalogItem が取得できる
3. `findById` 不在: null が返る
4. `search` happy: name 部分一致で結果が返る(リスト)
5. `revise` happy: name/unit を更新できる
6. domain invariant: `register` で空 name → 例外

**Files:**
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/catalog/CatalogRpcServiceE2eTest.kt`

- [ ] **Step 1: テストファイル**

Task 6 と同じパターンで作成。テンプレ:

```kotlin
package net.brightroom.mindstock.e2e.catalog

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedCatalogItem
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.presentation.rpc.CatalogRpcService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CatalogRpcServiceE2eTest : FunSpec({

    test("register creates a new CatalogItem and returns it") {
        e2eTest {
            val user = seedUser()
            val rpc = rpcClient<CatalogRpcService>(asUser = user, path = "catalog")
            val item = rpc.register(CatalogItemName("Milk"), CatalogItemUnit("L"))
            item.name shouldBe CatalogItemName("Milk")
            item.unit shouldBe CatalogItemUnit("L")
        }
    }

    test("findById returns the registered item") {
        e2eTest {
            val user = seedUser()
            val seeded = seedCatalogItem(name = "Bread", registeredBy = user)
            val rpc = rpcClient<CatalogRpcService>(asUser = user, path = "catalog")
            val found = rpc.findById(seeded.id)
            found.shouldNotBeNull()
            found.name shouldBe CatalogItemName("Bread")
        }
    }

    test("findById returns null for unknown id") {
        e2eTest {
            val user = seedUser()
            val rpc = rpcClient<CatalogRpcService>(asUser = user, path = "catalog")
            rpc.findById(CatalogItemId(Uuid.random())).shouldBeNull()
        }
    }

    test("search returns items whose name matches the query") {
        e2eTest {
            val user = seedUser()
            seedCatalogItem(name = "Apple Juice", registeredBy = user)
            seedCatalogItem(name = "Apple Pie", registeredBy = user)
            seedCatalogItem(name = "Banana", registeredBy = user)
            val rpc = rpcClient<CatalogRpcService>(asUser = user, path = "catalog")
            val results = rpc.search("Apple", limit = 50)
            results.list shouldHaveAtLeastSize 2
            results.list.map { it.name.value() } shouldBe results.list.map { it.name.value() }.filter { it.contains("Apple") }
        }
    }

    test("revise updates the name and unit of an existing item") {
        e2eTest {
            val user = seedUser()
            val item = seedCatalogItem(name = "Old", unit = "個", registeredBy = user)
            val rpc = rpcClient<CatalogRpcService>(asUser = user, path = "catalog")

            rpc.revise(item.id, CatalogItemName("New"), CatalogItemUnit("kg"))

            val updated = rpc.findById(item.id)!!
            updated.name shouldBe CatalogItemName("New")
            updated.unit shouldBe CatalogItemUnit("kg")
        }
    }

    test("register with empty name fails with a domain invariant error") {
        e2eTest {
            val user = seedUser()
            val rpc = rpcClient<CatalogRpcService>(asUser = user, path = "catalog")
            shouldThrowAny {
                rpc.register(CatalogItemName(""), CatalogItemUnit("個"))
            }
        }
    }
})
```

**注意**:
- `CatalogRpcService.search` の戻り型は `CatalogItems` wrapper(Plan 6 で確定)。`.list` でアクセス
- `CatalogItemName("").value()` などの VO API は domain 実装に合わせる
- `revise` の戻り型は `Unit`(Plan 6 で `Unit` 確定)

- [ ] **Step 2: 実行 + Commit**

```
./gradlew :backend:application:api:test --tests "*CatalogRpcServiceE2eTest"
git add backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/catalog/
git commit -m "test(e2e): cover CatalogRpcService (register/findById/search/revise + invariant)"
```

---

## Task 8: ProductRpcServiceE2eTest

**目的:** Product CRUD + transaction rollback の実機検証。

**カバーするケース(8)**:
1. `adopt` happy
2. `listOfHousehold` happy
3. `find(household, catalogItem)` happy
4. `setMinimumStock` happy
5. `archive` happy
6. `setMinimumStock` NotFound(未知 productId)
7. `archive` NotFound
8. transaction rollback: 例外を投げる method を 1 つ呼んで、DB 状態が前に戻ることを確認

**Files:**
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/product/ProductRpcServiceE2eTest.kt`

- [ ] **Step 1: テストファイル(雛形)**

```kotlin
package net.brightroom.mindstock.e2e.product

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedCatalogItem
import net.brightroom.mindstock.e2e.seedHousehold
import net.brightroom.mindstock.e2e.seedProduct
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.presentation.rpc.ProductRpcService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ProductRpcServiceE2eTest : FunSpec({

    test("adopt creates a new Product linking household and catalog item") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item = seedCatalogItem(name = "Soap", registeredBy = owner)
            val rpc = rpcClient<ProductRpcService>(asUser = owner, path = "product")

            val product = rpc.adopt(hh.id, item.id)

            product.catalogItem.id shouldBe item.id
            product.archived shouldBe false
        }
    }

    test("listOfHousehold returns all adopted products") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item1 = seedCatalogItem(name = "A", registeredBy = owner)
            val item2 = seedCatalogItem(name = "B", registeredBy = owner)
            seedProduct(hh, item1, owner)
            seedProduct(hh, item2, owner)
            val rpc = rpcClient<ProductRpcService>(asUser = owner, path = "product")

            val products = rpc.listOfHousehold(hh.id)
            products.list shouldHaveSize 2
        }
    }

    test("find returns the product for (household, catalogItem)") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item = seedCatalogItem(registeredBy = owner)
            val product = seedProduct(hh, item, owner)
            val rpc = rpcClient<ProductRpcService>(asUser = owner, path = "product")

            val found = rpc.find(hh.id, item.id)
            found.shouldNotBeNull()
            found.id shouldBe product.id
        }
    }

    test("setMinimumStock updates the threshold on an existing product") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item = seedCatalogItem(registeredBy = owner)
            val product = seedProduct(hh, item, owner)
            val rpc = rpcClient<ProductRpcService>(asUser = owner, path = "product")

            rpc.setMinimumStock(product.id, MinimumStock(3))

            val updated = rpc.find(hh.id, item.id)!!
            updated.minimumStock shouldBe MinimumStock(3)
        }
    }

    test("archive marks an existing product as archived") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item = seedCatalogItem(registeredBy = owner)
            val product = seedProduct(hh, item, owner)
            val rpc = rpcClient<ProductRpcService>(asUser = owner, path = "product")

            rpc.archive(product.id)

            val updated = rpc.find(hh.id, item.id)
            // archive 後は find が null を返す or archived = true で返す — 実装依存
            // Plan 6 の domain model:Product.archived がフラグなので read-back 可能なはず
            if (updated != null) {
                updated.archived shouldBe true
            }
        }
    }

    test("setMinimumStock with unknown productId throws NotFound") {
        e2eTest {
            val owner = seedUser()
            val rpc = rpcClient<ProductRpcService>(asUser = owner, path = "product")
            shouldThrowAny {
                rpc.setMinimumStock(ProductId(Uuid.random()), MinimumStock(1))
            }
        }
    }

    test("archive with unknown productId throws NotFound") {
        e2eTest {
            val owner = seedUser()
            val rpc = rpcClient<ProductRpcService>(asUser = owner, path = "product")
            shouldThrowAny {
                rpc.archive(ProductId(Uuid.random()))
            }
        }
    }

    test("a failing operation does not leave partial DB writes (transaction rollback)") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val rpc = rpcClient<ProductRpcService>(asUser = owner, path = "product")

            // 未知 catalogItemId で adopt を呼ぶ → adopt 内部で findById が null
            // → NotFoundException → tx(database) が rollback
            val initialCount = rpc.listOfHousehold(hh.id).list.size
            shouldThrowAny {
                rpc.adopt(hh.id, net.brightroom.mindstock.domain.model.catalog.CatalogItemId(Uuid.random()))
            }
            val afterCount = rpc.listOfHousehold(hh.id).list.size
            afterCount shouldBe initialCount  // 失敗時に何も追加されていない
        }
    }
})
```

**注意**:
- `archive` 後の `find` 挙動が「null を返す」か「archived=true で返す」かは実装次第。Plan 6 の `Product.archived: Boolean` を尊重 → 返ってくる前提で書いたが、`ProductRepository.findById` の実装次第。実機で確認して assert を調整
- transaction rollback テストは「失敗 RPC の前後で list の差分が無い」を見るシンプルな方式

- [ ] **Step 2: 実行 + Commit**

```
./gradlew :backend:application:api:test --tests "*ProductRpcServiceE2eTest"
git add backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/product/
git commit -m "test(e2e): cover ProductRpcService (CRUD + NotFound + tx rollback)"
```

---

## Task 9: StockRpcServiceE2eTest

**目的:** Stock の補充・消費・履歴 + polymorphic 戻り値 + domain invariant 検証。

**カバーするケース(9)**:
1. `replenish` happy
2. `consume` happy
3. `get` happy(補充後の残量取得)
4. `list(householdId)` happy
5. `movementHistory(productId, limit)` happy
6. `get` NotFound
7. `consume` NotFound
8. domain invariant: `consume(qty が在庫超過)` → 例外
9. polymorphic: `movementHistory` の結果が `Replenishment` と `Consumption` 両方を含み、polymorphic serializer が両方 round-trip できることを確認

**Files:**
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/stock/StockRpcServiceE2eTest.kt`

- [ ] **Step 1: テストファイル**

```kotlin
package net.brightroom.mindstock.e2e.stock

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedCatalogItem
import net.brightroom.mindstock.e2e.seedHousehold
import net.brightroom.mindstock.e2e.seedProduct
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.presentation.rpc.StockRpcService

@OptIn(ExperimentalUuidApi::class)
class StockRpcServiceE2eTest : FunSpec({

    test("replenish creates a Replenishment and updates current quantity") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item = seedCatalogItem(registeredBy = owner)
            val product = seedProduct(hh, item, owner)
            val rpc = rpcClient<StockRpcService>(asUser = owner, path = "stock")

            val rep = rpc.replenish(
                product.id,
                Quantity(5),
                OccurredAt(Instant.parse("2026-05-25T10:00:00Z")),
                Note(""),
            )
            rep.quantity shouldBe Quantity(5)

            rpc.get(product.id).currentQuantity() shouldBe 5
        }
    }

    test("consume reduces current quantity") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item = seedCatalogItem(registeredBy = owner)
            val product = seedProduct(hh, item, owner)
            val rpc = rpcClient<StockRpcService>(asUser = owner, path = "stock")
            rpc.replenish(product.id, Quantity(5), OccurredAt(Instant.parse("2026-05-25T10:00:00Z")), Note(""))

            rpc.consume(product.id, Quantity(2), OccurredAt(Instant.parse("2026-05-25T11:00:00Z")), Note("breakfast"))

            rpc.get(product.id).currentQuantity() shouldBe 3
        }
    }

    test("get returns a Stock with zero quantity for an untouched product") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item = seedCatalogItem(registeredBy = owner)
            val product = seedProduct(hh, item, owner)
            val rpc = rpcClient<StockRpcService>(asUser = owner, path = "stock")

            rpc.get(product.id).currentQuantity() shouldBe 0
        }
    }

    test("list returns all stocks for the household") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item1 = seedCatalogItem(name = "A", registeredBy = owner)
            val item2 = seedCatalogItem(name = "B", registeredBy = owner)
            seedProduct(hh, item1, owner)
            seedProduct(hh, item2, owner)
            val rpc = rpcClient<StockRpcService>(asUser = owner, path = "stock")

            val stocks = rpc.list(hh.id)
            stocks shouldHaveSize 2
        }
    }

    test("movementHistory returns all movements newest first (polymorphic Replenishment + Consumption)") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item = seedCatalogItem(registeredBy = owner)
            val product = seedProduct(hh, item, owner)
            val rpc = rpcClient<StockRpcService>(asUser = owner, path = "stock")

            rpc.replenish(product.id, Quantity(10), OccurredAt(Instant.parse("2026-05-25T08:00:00Z")), Note(""))
            rpc.consume(product.id, Quantity(3), OccurredAt(Instant.parse("2026-05-25T09:00:00Z")), Note(""))
            rpc.replenish(product.id, Quantity(2), OccurredAt(Instant.parse("2026-05-25T10:00:00Z")), Note(""))

            val history = rpc.movementHistory(product.id, limit = 10)
            history.list shouldHaveSize 3
            // 並び順(DESC) と polymorphic 型の混在を検証
            history.list[0].shouldBeInstanceOf<Replenishment>()
            history.list[1].shouldBeInstanceOf<Consumption>()
            history.list[2].shouldBeInstanceOf<Replenishment>()
        }
    }

    test("get with unknown productId throws NotFound") {
        e2eTest {
            val owner = seedUser()
            val rpc = rpcClient<StockRpcService>(asUser = owner, path = "stock")
            shouldThrowAny { rpc.get(ProductId(Uuid.random())) }
        }
    }

    test("consume with unknown productId throws NotFound") {
        e2eTest {
            val owner = seedUser()
            val rpc = rpcClient<StockRpcService>(asUser = owner, path = "stock")
            shouldThrowAny {
                rpc.consume(
                    ProductId(Uuid.random()),
                    Quantity(1),
                    OccurredAt(Instant.parse("2026-05-25T10:00:00Z")),
                    Note(""),
                )
            }
        }
    }

    test("consume that exceeds current quantity fails with a domain invariant error") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item = seedCatalogItem(registeredBy = owner)
            val product = seedProduct(hh, item, owner)
            val rpc = rpcClient<StockRpcService>(asUser = owner, path = "stock")
            rpc.replenish(product.id, Quantity(2), OccurredAt(Instant.parse("2026-05-25T08:00:00Z")), Note(""))

            shouldThrowAny {
                rpc.consume(
                    product.id,
                    Quantity(10),  // 在庫超過
                    OccurredAt(Instant.parse("2026-05-25T09:00:00Z")),
                    Note(""),
                )
            }
        }
    }

    test("movementHistory limit caps the returned size") {
        e2eTest {
            val owner = seedUser()
            val hh = seedHousehold(owner)
            val item = seedCatalogItem(registeredBy = owner)
            val product = seedProduct(hh, item, owner)
            val rpc = rpcClient<StockRpcService>(asUser = owner, path = "stock")
            repeat(5) { i ->
                rpc.replenish(product.id, Quantity(1), OccurredAt(Instant.parse("2026-05-25T1$i:00:00Z")), Note(""))
            }

            val history = rpc.movementHistory(product.id, limit = 3)
            history.list shouldHaveSize 3
        }
    }
})
```

**注意**:
- `ConsumeStockHandler` が「在庫超過時に例外を投げる」設計かは実装確認(現状 Plan 5 の `validation` commit で `Quantity` 系の boundary は導入された)。投げない実装なら domain invariant テストの assert を変える
- `movementHistory` の並び順は DESC `occurred_at`(Plan 5 で確定)
- `Instant.parse` の import は `kotlin.time.Instant`(Plan 6 で domain 統一)

- [ ] **Step 2: 実行 + Commit**

```
./gradlew :backend:application:api:test --tests "*StockRpcServiceE2eTest"
git add backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/stock/
git commit -m "test(e2e): cover StockRpcService (replenish/consume/get/list/history + edge cases)"
```

---

## Task 10: 全テスト実行と CI smoke

**目的:** 全 6 test class を一気に実行して green 確認。CI で動く前に local で完走を確認。

- [ ] **Step 1: 全 e2e テスト実行**

```
./gradlew :backend:application:api:test --tests "net.brightroom.mindstock.e2e.*"
```

期待:
- 約 34 テスト PASS
- 実行時間目安 15-30 秒

- [ ] **Step 2: 全ビルド**

```
./gradlew build
./gradlew check
```

両方 BUILD SUCCESSFUL。

- [ ] **Step 3: もし failing test があれば修正コミット**

修正は元のタスクに紐付ける形で:
```
git commit -m "fix(e2e): adjust <test> after observing actual <thing>"
```

- [ ] **Step 4: PR 作成準備**

ブランチ `feat/integration-tests` は既に origin に push する必要あり:
```
git push -u origin feat/integration-tests
```

PR 本文サマリ(Plan 6 PR と同様の構造)で作る — `gh pr create` の text は実装者の判断で書く。Plan 7 spec / plan ドキュメント へのリンクを含める。

---

## 完了条件

- [ ] 全 10 タスクのチェックボックスが完了
- [ ] `./gradlew check` 通過
- [ ] `./gradlew :backend:application:api:test --tests "*E2eTest"` で 34 前後の e2e テストが green
- [ ] Plan 6 transaction 修正の効果が `ProductRpcServiceE2eTest` の rollback テストで実機検証されている
- [ ] PR が CI green
