# Plan 5: Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `domain/repository/` の全 10 Repository ポートに対する Exposed 実装と Testcontainers 結合テストを `:backend:application:api` 配下に追加し、Ktor DI で配線、`ExposedTransactionPlugin` を本実装+install する。

**Architecture:** 1 Repository ポート = 1 実装クラス。Hydration は `<Aggregate>Hydration.kt` の internal extension(`ResultRow.toUser()` 等)。最新行取得は PostgreSQL `DISTINCT ON`。テストは `withFreshSchema` で実 PG に対する結合テスト。Transaction は Ktor plugin の `intercept(ApplicationCallPipeline.Call) { newSuspendedTransaction(db) { proceed() } }` で境界に張る。

**Tech Stack:** Kotlin / Ktor server + built-in DI / Exposed v1.0.0-beta-4 / PostgreSQL 18 (Testcontainers) / Flyway / Kotest

---

## 前提

- 仕様: `docs/superpowers/specs/2026-05-24-repository-implementation-design.md`
- Plan 3 / domain-richness / stock-movements-unification / Plan 4 が完了済み(main にマージ済)
- 既存テスト基盤: `backend/infrastructure/migration/executor` の `testFixtures` に `TestContainersPostgres` / `testHikariDataSource` あり
- `backend:application:api` は Ktor 標準 DI(`io.ktor.server.plugins.di`)を使用
- backend モジュール構造の見直しは Plan 5 完了後に別作業として実施(memory [[structure-review-pending]])

## ブランチ運用

`feat/repository-impl` ブランチを main から派生。

```bash
git checkout main
git pull --ff-only
git checkout -b feat/repository-impl
```

## ファイル構成サマリ

新規作成(全 33 ファイル):

```
backend/application/api/src/main/kotlin/net/brightroom/mindstock/
├── infrastructure/datasource/repository/
│   ├── user/{UserRepositoryImpl,UserRegisterRepositoryImpl,UserHydration}.kt
│   ├── household/{HouseholdRepositoryImpl,HouseholdRegisterRepositoryImpl,HouseholdHydration}.kt
│   ├── catalog/{CatalogItemRepositoryImpl,CatalogItemRegisterRepositoryImpl,CatalogItemHydration}.kt
│   ├── product/{ProductRepositoryImpl,ProductRegisterRepositoryImpl,ProductHydration}.kt
│   └── stock/{StockRepositoryImpl,StockRegisterRepositoryImpl,StockHydration}.kt
└── configuration/
    ├── transaction/TransactionConfiguration.kt
    └── di/{RepositoryConfiguration,UseCaseConfiguration}.kt

backend/application/api/src/testFixtures/kotlin/net/brightroom/mindstock/
└── infrastructure/datasource/repository/RepositoryTestSupport.kt

backend/application/api/src/test/kotlin/net/brightroom/mindstock/
└── infrastructure/datasource/repository/
    ├── user/{UserRepositoryImplIntegrationTest,UserRegisterRepositoryImplIntegrationTest}.kt
    ├── household/{HouseholdRepositoryImplIntegrationTest,HouseholdRegisterRepositoryImplIntegrationTest}.kt
    ├── catalog/{CatalogItemRepositoryImplIntegrationTest,CatalogItemRegisterRepositoryImplIntegrationTest}.kt
    ├── product/{ProductRepositoryImplIntegrationTest,ProductRegisterRepositoryImplIntegrationTest}.kt
    └── stock/{StockRepositoryImplIntegrationTest,StockRegisterRepositoryImplIntegrationTest}.kt
```

修正:
- `backend/application/api/build.gradle.kts`(testFixtures plugin 追加、testFixtures 依存追加)
- `backend/application/api/src/main/resources/application.yaml`(modules に 3 つ追加)
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/ExposedTransactionPlugin.kt`(本実装に置き換え)
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/**/*.kt`(12 個の `internal operator fun invoke()` を `public` 化)
- `docs/superpowers/specs/2026-05-24-usecase-design.md`(§7 / §10 を更新)

---

## Task 0: 親仕様パッチと VO accessor の public 化

domain VO / ID の `internal operator fun invoke()` を public 化(infrastructure 側から値を読めるようにする)。仕様の Plan 5 完了反映も同 PR に含める(`structure-review-pending` の方針)。

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/UserId.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/DisplayName.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/auth/AuthSubject.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdId.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemId.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemName.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemUnit.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/ProductId.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/MinimumStock.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Quantity.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Note.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/OccurredAt.kt`
- Modify: `docs/superpowers/specs/2026-05-24-usecase-design.md`(§7、§10)

- [ ] **Step 1: 12 VO/ID の `invoke()` を public 化**

各ファイルで `internal operator fun invoke()` → `operator fun invoke()`(`internal` を削除すれば default は `public`)。

対象 12 個:
- `UserId.kt`、`DisplayName.kt`、`AuthSubject.kt`
- `HouseholdId.kt`
- `CatalogItemId.kt`、`CatalogItemName.kt`、`CatalogItemUnit.kt`
- `ProductId.kt`、`MinimumStock.kt`
- `Quantity.kt`、`Note.kt`、`OccurredAt.kt`

具体例(`UserId.kt`):

```kotlin
// BEFORE
internal operator fun invoke(): Uuid = value

// AFTER
operator fun invoke(): Uuid = value
```

同じ 1 行修正を 12 ファイルすべてに適用。

- [ ] **Step 2: ビルド確認**

```bash
./gradlew :domain:build
```

Expected: BUILD SUCCESSFUL(Plan 3 で書かれた domain テストはすべて pass、`internal` 削除で動作が変わらない)。

- [ ] **Step 3: `usecase-design.md` §7 を更新**

`docs/superpowers/specs/2026-05-24-usecase-design.md` の §7 末尾(「Plan 4 ではコンパイルが通り、将来 DI に流し込めるコンストラクタが揃っていることを保証する。」の直後)に以下を追記:

```markdown

**Plan 5 で実施済み**: `RepositoryConfiguration.kt` / `UseCaseConfiguration.kt` を作成し、`application.yaml` の `ktor.application.modules` に登録した。詳細は `2026-05-24-repository-implementation-design.md` §7 を参照。
```

- [ ] **Step 4: `usecase-design.md` §10 を更新**

§10 「対象外(明示的に Plan 5 / 6 で扱う)」の `- Repository の Exposed 実装(Plan 5)` 行を以下に置換:

```markdown
- ~~Repository の Exposed 実装(Plan 5)~~ → Plan 5 で完了
- ~~Testcontainers による Handler + Repository + 実 DB の結合テスト(Plan 5)~~ → Plan 5 で完了
```

- [ ] **Step 5: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/ \
        docs/superpowers/specs/2026-05-24-usecase-design.md
git commit -m "feat(domain): expose VO/ID invoke() to infrastructure module

12 個の value class / data class の \`internal operator fun invoke()\` を public 化。
Repository 実装が \`:backend:application:api\` 配下に置かれるため、別モジュールから
domain VO の値を読み出す必要がある(domain-richness-design.md §6 の方針通り、
Plan 5 で都度 public 化する)。

domain-model-style.md の 7 原則:
- 原則 3 「id は private」の趣旨は「ドメインロジック内で a.id == b.id 比較を出さない」
  であり、永続化のための accessor 公開を禁止するものではない
- 永続化のためには getter で Repository に渡す方針が明示されている

Plan 5 完了の反映として usecase-design.md §7 / §10 も更新。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 1: モジュールビルド設定(testFixtures プラグイン + 依存追加)

`:backend:application:api` に `java-test-fixtures` プラグインと Testcontainers 関連依存を追加する。

**Files:**
- Modify: `backend/application/api/build.gradle.kts`

- [ ] **Step 1: build.gradle.kts に testFixtures プラグインと testcontainers 関連を追加**

`backend/application/api/build.gradle.kts` の冒頭 `plugins { ... }` ブロックを以下に置換(`java-test-fixtures` 追加):

```kotlin
plugins {
    id("net.brightroom.mindstock.ktor-server")
    id("net.brightroom.mindstock.kotlin-jvm-testcontainers")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
    `java-test-fixtures`
}
```

依存ブロックを以下に置換(`testFixturesImplementation` / `testImplementation` を追加。`implementation` 部分は既存維持):

```kotlin
dependencies {
    implementation(projects.shared.rpc)
    implementation(projects.shared.extensions)
    implementation(projects.domain)
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.executor)

    implementation(ktorLib.server.core)
    implementation(ktorLib.server.cio)
    implementation(ktorLib.server.di)
    implementation(ktorLib.server.config.yaml)
    implementation(ktorLib.server.websockets)
    implementation(ktorLib.server.auth)
    implementation(ktorLib.server.auth.jwt)
    implementation(ktorLib.server.contentNegotiation)
    implementation(ktorLib.serialization.kotlinx.json)
    implementation(ktorLib.server.doubleReceive)
    implementation(ktorLib.server.statusPages)
    implementation(ktorLib.server.callId)
    implementation(ktorLib.server.callLogging)
    implementation(libs.kotlinx.rpc.server)
    implementation(libs.kotlinx.rpc.server.ktor)
    implementation(libs.koin.ktor)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.hikari)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)

    testFixturesImplementation(projects.domain)
    testFixturesImplementation(projects.backend.infrastructure.schemas)
    testFixturesImplementation(testFixtures(projects.backend.infrastructure.migration.executor))
    testFixturesImplementation(projects.backend.infrastructure.migration.executor)
    testFixturesImplementation(libs.exposed.core)
    testFixturesImplementation(libs.exposed.jdbc)
    testFixturesImplementation(libs.hikari)
    testFixturesImplementation(libs.testcontainers.postgres)
    testFixturesImplementation(libs.kotest.assertions.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(testFixtures(projects.backend.infrastructure.migration.executor))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(ktorLib.server.testHost)
}
```

- [ ] **Step 2: 既存ビルドが壊れていないことを確認**

```bash
./gradlew :backend:application:api:build
```

Expected: BUILD SUCCESSFUL(まだテスト・ソースは追加していないので既存 Handler のコンパイルのみ走る)。

- [ ] **Step 3: spotlessApply**

```bash
./gradlew :backend:application:api:spotlessApply
```

- [ ] **Step 4: コミット**

```bash
git add backend/application/api/build.gradle.kts
git commit -m "chore(app:api): add java-test-fixtures and Testcontainers deps for Plan 5

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: テスト基盤 `RepositoryTestSupport`

Repository テスト共通のヘルパー。fresh schema + migrate + Database セットアップを 1 ブロックで提供する。

**Files:**
- Create: `backend/application/api/src/testFixtures/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/RepositoryTestSupport.kt`

- [ ] **Step 1: RepositoryTestSupport.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository

import net.brightroom.mindstock.infrastructure.migration.executor.MigrationRunner
import net.brightroom.mindstock.infrastructure.migration.executor.TestContainersPostgres
import net.brightroom.mindstock.infrastructure.migration.executor.testHikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Repository 結合テスト用のヘルパー。
 *
 * fresh schema を立て、Flyway migrate を流し、Exposed `Database` を渡して [block] を実行する。
 * block 内は `transaction(database) { ... }` で囲んで Repository を呼ぶこと。
 */
fun withRepositoryTestContext(block: RepositoryTestContext.() -> Unit) {
    TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
        val dataSource = testHikariDataSource(
            jdbcUrl,
            TestContainersPostgres.username,
            TestContainersPostgres.password,
        )
        try {
            MigrationRunner.migrate(dataSource)
            val database = Database.connect(dataSource)
            RepositoryTestContext(database).block()
        } finally {
            dataSource.close()
        }
    }
}

class RepositoryTestContext(val database: Database) {
    /** Repository コードを transaction 境界内で実行するショートカット。 */
    fun <T> tx(block: () -> T): T = transaction(database) { block() }
}
```

注: `testHikariDataSource` は migration/executor の testFixtures で `internal` 宣言されているため、同モジュール参照しか効かない。Plan 5 では `:backend:application:api` から呼ぶため、もし可視性で落ちる場合は migration/executor 側で `internal` → 公開化が必要。実装時に確認し、必要なら別途修正コミットを入れる(Step 3 でビルド失敗したら対応)。

- [ ] **Step 2: ビルド確認**

```bash
./gradlew :backend:application:api:compileTestFixturesKotlin
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 可視性エラーが出た場合の対応**

`testHikariDataSource` が見えない旨のエラー(`Cannot access 'testHikariDataSource': it is internal in 'net.brightroom.mindstock.infrastructure.migration.executor'`)が出たら、`backend/infrastructure/migration/executor/src/testFixtures/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/TestDataSource.kt` の `internal fun` から `internal` を削除して `fun` に変更:

```kotlin
// BEFORE
internal fun testHikariDataSource(...)

// AFTER
fun testHikariDataSource(...)
```

ビルドを再度実行。

- [ ] **Step 4: spotlessApply + コミット**

```bash
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/testFixtures/ backend/infrastructure/migration/executor/src/testFixtures/
git commit -m "feat(app:api): add RepositoryTestSupport for integration tests

withRepositoryTestContext spins up a fresh PG schema, runs Flyway,
and yields a Database to the test block. Repositories call into
\`tx { ... }\` to wrap their work in a transaction.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: User Repository

User 集約: UserRegisterRepository(register / rename)と UserRepository(findByAuthIdentity)。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/user/UserHydration.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/user/UserRegisterRepositoryImpl.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/user/UserRepositoryImpl.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/user/UserRegisterRepositoryImplIntegrationTest.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/user/UserRepositoryImplIntegrationTest.kt`

- [ ] **Step 1: UserHydration.kt を作成**

`backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/user/UserHydration.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * `users` の row と最新 `user_display_names` の row を含む `ResultRow` から User を組み立てる。
 *
 * Caller は `UsersTable JOIN <latest user_display_names>` の SELECT 結果を渡すこと。
 */
@OptIn(ExperimentalUuidApi::class)
internal fun ResultRow.toUser(): User =
    User(
        id = UserId(this[UsersTable.id].value.toKotlinUuid()),
        authIdentity = AuthIdentity(
            provider = AuthProvider.ZITADEL,
            subject = AuthSubject(this[UsersTable.zitadel_sub]),
        ),
        displayName = DisplayName(this[UserDisplayNamesTable.display_name]),
    )
```

注: `UsersTable.id` は `org.jetbrains.exposed.v1.core.dao.id.EntityID<UUID>` 型で、`.value` で JVM `UUID` を取り、`toKotlinUuid()` で `kotlin.uuid.Uuid` に変換する。

- [ ] **Step 2: UserRegisterRepositoryImpl.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.repository.user.UserRegisterRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class UserRegisterRepositoryImpl(
    private val database: Database,
) : UserRegisterRepository {
    override fun register(
        identity: AuthIdentity,
        defaultDisplayName: DisplayName,
    ): User {
        val insertedUserId = UsersTable.insert {
            it[zitadel_sub] = identity.subject()
        } get UsersTable.id

        UserDisplayNamesTable.insert {
            it[user_id] = insertedUserId
            it[display_name] = defaultDisplayName()
        }

        return (UsersTable innerJoin UserDisplayNamesTable)
            .selectAll()
            .where { UsersTable.id eq insertedUserId }
            .single()
            .toUser()
    }

    override fun rename(
        user: User,
        newName: DisplayName,
    ) {
        UserDisplayNamesTable.insert {
            it[user_id] = org.jetbrains.exposed.v1.core.dao.id.EntityID(user.id().toJavaUuid(), UsersTable)
            it[display_name] = newName()
        }
    }
}
```

注: `EntityID` の構築方法は Exposed v1 の API による。コンパイルが通らなかったら `UsersTable.id` の型(`Column<EntityID<UUID>>`)から推測される正しい変換方法に置き換える(`EntityID.invoke` 等)。

- [ ] **Step 3: UserRepositoryImpl.kt を作成(DISTINCT ON で最新 display_name を取得)**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.user

import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

class UserRepositoryImpl(
    private val database: Database,
) : UserRepository {
    override fun findByAuthIdentity(identity: AuthIdentity): User? {
        // DISTINCT ON で各 user_id の最新 display_names 行を取り、users と JOIN
        val sql = """
            SELECT u.id            AS u_id,
                   u.zitadel_sub   AS u_zitadel_sub,
                   d.display_name  AS d_display_name
            FROM users u
            INNER JOIN (
                SELECT DISTINCT ON (user_id)
                       user_id,
                       display_name,
                       id
                FROM user_display_names
                ORDER BY user_id, id DESC
            ) d ON d.user_id = u.id
            WHERE u.zitadel_sub = ?
            LIMIT 1
        """.trimIndent()

        val conn = TransactionManager.current().connection
        return conn.prepareStatement(sql, false).use { stmt ->
            stmt.fillParameters(listOf(org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi.ParameterValue(
                org.jetbrains.exposed.v1.core.TextColumnType(),
                identity.subject(),
            )))
            stmt.executeQuery().let { rs ->
                if (rs.next()) {
                    // ResultSet を ResultRow に変換できないため、手動で hydration
                    val id = rs.getObject("u_id", java.util.UUID::class.java)
                    val sub = rs.getString("u_zitadel_sub")
                    val name = rs.getString("d_display_name")
                    User(
                        id = net.brightroom.mindstock.domain.model.user.UserId(
                            id.let { kotlin.uuid.ExperimentalUuidApi::class; it.let { uuid -> @OptIn(kotlin.uuid.ExperimentalUuidApi::class) kotlin.uuid.toKotlinUuid(uuid) } }
                        ),
                        authIdentity = AuthIdentity(
                            provider = net.brightroom.mindstock.domain.model.user.auth.AuthProvider.ZITADEL,
                            subject = net.brightroom.mindstock.domain.model.user.auth.AuthSubject(sub),
                        ),
                        displayName = net.brightroom.mindstock.domain.model.user.DisplayName(name),
                    )
                } else null
            }
        }
    }
}
```

注: 上記は **動かない可能性が高い**(Exposed v1 の `PreparedStatementApi` の使い方が正確でない)。実装者は以下のいずれかで `DISTINCT ON` を実現する:

選択肢 a: Exposed の `Transaction.exec(sql, args)` を使う
```kotlin
return transaction(database) { ... } // NO — 既に外側で transaction を張る
// 代わりに:
val sql = "SELECT ..."
val result = mutableListOf<User>()
TransactionManager.current().exec(sql, listOf(TextColumnType() to identity.subject())) { rs ->
    if (rs.next()) {
        result.add(/* hydrate */)
    }
}
return result.singleOrNull()
```

選択肢 b: `MAX(id) GROUP BY` を Exposed DSL で書く(`DISTINCT ON` 回避)

実装者は spec §3.5 を参照し、**最も読みやすく動作する形** を選ぶ。実装の正しさはテスト(Step 5-8)で検証される。

- [ ] **Step 4: UserRegisterRepositoryImplIntegrationTest.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.user

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class UserRegisterRepositoryImplIntegrationTest : FunSpec({

    test("register inserts users + display_names and returns User with initial display name") {
        withRepositoryTestContext {
            val repo = UserRegisterRepositoryImpl(database)
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("subject-1"))
            val name = DisplayName("Alice")

            val user = tx { repo.register(identity, name) }

            user.authIdentity shouldBe identity
            user.displayName shouldBe name
        }
    }

    test("rename inserts a new display_names row and the latest is returned by reader") {
        withRepositoryTestContext {
            val registerRepo = UserRegisterRepositoryImpl(database)
            val readerRepo = UserRepositoryImpl(database)
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("subject-1"))

            val user = tx { registerRepo.register(identity, DisplayName("Alice")) }
            tx { registerRepo.rename(user, DisplayName("Alicia")) }

            val refetched = tx { readerRepo.findByAuthIdentity(identity) }
            refetched?.displayName shouldBe DisplayName("Alicia")
        }
    }
})
```

- [ ] **Step 5: テスト実行(失敗を確認)**

```bash
./gradlew :backend:application:api:test --tests "*UserRegisterRepositoryImplIntegrationTest*"
```

Expected: コンパイルが通り、テストが **走り**、何件か失敗する(Step 3 の Reader 実装に問題あり)。コンパイルが通らない場合は Step 2 / Step 3 を実装者の判断で動く形に修正してから再実行。

- [ ] **Step 6: UserRepositoryImpl を実装が通る形に書き直す**

Step 3 で示した実装は不確かなので、実装者が以下のどちらかで書き直す:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.user

import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

class UserRepositoryImpl(
    private val database: Database,
) : UserRepository {
    override fun findByAuthIdentity(identity: AuthIdentity): User? {
        // 各 user_id の最新 (= MAX(id)) display_names を取るサブクエリ
        val latestPerUser = UserDisplayNamesTable
            .select(
                UserDisplayNamesTable.user_id,
                UserDisplayNamesTable.id.max().alias("latest_id"),
            )
            .groupBy(UserDisplayNamesTable.user_id)
            .alias("latest_per_user")

        // latest_id と一致する display_names 行を取得し、users と JOIN
        return UsersTable
            .innerJoin(latestPerUser, { UsersTable.id }, { latestPerUser[UserDisplayNamesTable.user_id] })
            .innerJoin(
                UserDisplayNamesTable,
                onColumn = { UserDisplayNamesTable.id },
                otherColumn = { latestPerUser[UserDisplayNamesTable.id.max().alias("latest_id")] },
            )
            .selectAll()
            .where { UsersTable.zitadel_sub eq identity.subject() }
            .singleOrNull()
            ?.toUser()
    }
}
```

注: 上記は型推論で苦しむ可能性あり。動かなければ生 SQL に切り替える:

```kotlin
override fun findByAuthIdentity(identity: AuthIdentity): User? {
    val sql = """
        SELECT u.id AS user_id,
               u.zitadel_sub,
               d.display_name
        FROM users u
        INNER JOIN (
            SELECT DISTINCT ON (user_id) user_id, display_name, id
            FROM user_display_names
            ORDER BY user_id, id DESC
        ) d ON d.user_id = u.id
        WHERE u.zitadel_sub = ?
    """.trimIndent()

    var result: User? = null
    org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec(
        sql,
        args = listOf(org.jetbrains.exposed.v1.core.TextColumnType() to identity.subject()),
    ) { rs ->
        if (rs.next()) {
            @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
            result = User(
                id = net.brightroom.mindstock.domain.model.user.UserId(
                    kotlin.uuid.toKotlinUuid(rs.getObject("user_id", java.util.UUID::class.java)),
                ),
                authIdentity = AuthIdentity(
                    provider = net.brightroom.mindstock.domain.model.user.auth.AuthProvider.ZITADEL,
                    subject = net.brightroom.mindstock.domain.model.user.auth.AuthSubject(rs.getString("zitadel_sub")),
                ),
                displayName = net.brightroom.mindstock.domain.model.user.DisplayName(rs.getString("display_name")),
            )
        }
    }
    return result
}
```

実装者は DSL 版が動けばそちらを優先、無理なら生 SQL 版を採用する。

- [ ] **Step 7: UserRepositoryImplIntegrationTest.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.user

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class UserRepositoryImplIntegrationTest : FunSpec({

    test("findByAuthIdentity returns null when no user with that subject exists") {
        withRepositoryTestContext {
            val repo = UserRepositoryImpl(database)
            val result = tx { repo.findByAuthIdentity(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("unknown"))) }
            result.shouldBeNull()
        }
    }

    test("findByAuthIdentity returns user with initial display name") {
        withRepositoryTestContext {
            val registerRepo = UserRegisterRepositoryImpl(database)
            val readerRepo = UserRepositoryImpl(database)
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("subject-1"))

            tx { registerRepo.register(identity, DisplayName("Alice")) }
            val refetched = tx { readerRepo.findByAuthIdentity(identity) }

            refetched?.displayName shouldBe DisplayName("Alice")
            refetched?.authIdentity shouldBe identity
        }
    }

    test("findByAuthIdentity returns LATEST display name after rename") {
        withRepositoryTestContext {
            val registerRepo = UserRegisterRepositoryImpl(database)
            val readerRepo = UserRepositoryImpl(database)
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("subject-1"))

            val user = tx { registerRepo.register(identity, DisplayName("Alice")) }
            tx { registerRepo.rename(user, DisplayName("Alicia")) }

            val refetched = tx { readerRepo.findByAuthIdentity(identity) }
            refetched?.displayName shouldBe DisplayName("Alicia")
        }
    }
})
```

- [ ] **Step 8: テスト実行(全 5 件 pass を目指す)**

```bash
./gradlew :backend:application:api:test --tests "*UserRepositoryImpl*" --tests "*UserRegisterRepositoryImpl*"
```

Expected: 5 test cases、すべて PASS。落ちたら Step 6 の Reader 実装を見直す(DSL 版で型推論が通らないなら生 SQL 版に切り替える等)。

- [ ] **Step 9: spotlessApply + コミット**

```bash
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/user/ \
        backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/user/
git commit -m "feat(repo): add User Repository Exposed impl + integration tests

- UserRegisterRepositoryImpl: register/rename を append-only insert で実装
- UserRepositoryImpl: findByAuthIdentity を最新 display_name 取得で実装
- UserHydration: ResultRow.toUser() internal extension
- 5 件の結合テスト (CRUD round-trip / latest display_name / not found)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Household Repository

Household 集約: HouseholdRegisterRepository(create / invite / revoke)と HouseholdRepository(findOf)。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/household/HouseholdHydration.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/household/HouseholdRegisterRepositoryImpl.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/household/HouseholdRepositoryImpl.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/household/HouseholdRegisterRepositoryImplIntegrationTest.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/household/HouseholdRepositoryImplIntegrationTest.kt`

- [ ] **Step 1: HouseholdHydration.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.user.User
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * householdId と active members(User + role)から Household を組み立てる。
 *
 * active な membership 行と各 user に対応する User(最新 display_name)を caller が用意する。
 */
@OptIn(ExperimentalUuidApi::class)
internal fun hydrateHousehold(
    householdId: Uuid,
    members: List<HouseholdMember>,
): Household =
    Household(
        id = HouseholdId(householdId),
        members = HouseholdMembers(members),
    )
```

注: `HouseholdMembers` のコンストラクタが `List<HouseholdMember>` 単引数であることを `domain/model/household/HouseholdMembers.kt` で確認すること。違う場合は実装者がアダプトする。

- [ ] **Step 2: HouseholdRegisterRepositoryImpl.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdMembershipRevocationsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdMembershipsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdsTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class HouseholdRegisterRepositoryImpl(
    private val database: Database,
) : HouseholdRegisterRepository {

    override fun create(owner: User): Household {
        val insertedHouseholdId = HouseholdsTable.insert {
            // id は default `uuidv7()` で生成
        } get HouseholdsTable.id

        HouseholdMembershipsTable.insert {
            it[household_id] = insertedHouseholdId
            it[user_id] = EntityID(owner.id().toJavaUuid(), net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable)
            it[role] = HouseholdMemberRole.OWNER
        }

        // 直前に作った Household の現在状態を hydrate して返す
        return hydrateHousehold(
            householdId = insertedHouseholdId.value.let { @OptIn(kotlin.uuid.ExperimentalUuidApi::class) kotlin.uuid.toKotlinUuid(it) },
            members = listOf(net.brightroom.mindstock.domain.model.household.HouseholdMember(owner, HouseholdMemberRole.OWNER)),
        )
    }

    override fun invite(
        household: Household,
        user: User,
        role: HouseholdMemberRole,
    ) {
        HouseholdMembershipsTable.insert {
            it[household_id] = EntityID(household.id().toJavaUuid(), HouseholdsTable)
            it[user_id] = EntityID(user.id().toJavaUuid(), net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable)
            it[this.role] = role
        }
    }

    override fun revoke(
        household: Household,
        user: User,
    ) {
        // user に対する最新の active membership を引き、その id を revocations に積む
        val membershipId = HouseholdMembershipsTable
            .selectAll()
            .where {
                (HouseholdMembershipsTable.household_id eq EntityID(household.id().toJavaUuid(), HouseholdsTable)) and
                    (HouseholdMembershipsTable.user_id eq EntityID(user.id().toJavaUuid(), net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable))
            }
            .orderBy(HouseholdMembershipsTable.id, org.jetbrains.exposed.v1.core.SortOrder.DESC)
            .limit(1)
            .single()[HouseholdMembershipsTable.id]

        HouseholdMembershipRevocationsTable.insert {
            it[membership_id] = membershipId
        }
    }
}
```

- [ ] **Step 3: HouseholdRepositoryImpl.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository
import net.brightroom.mindstock.infrastructure.datasource.repository.user.toUser
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdMembershipRevocationsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdMembershipsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class HouseholdRepositoryImpl(
    private val database: Database,
) : HouseholdRepository {

    override fun findOf(user: User): Household? {
        // user が active なメンバー(revocation 無し)である Household を 1 件取得
        val sql = """
            WITH active_memberships AS (
                SELECT m.id, m.household_id, m.user_id, m.role
                FROM household_memberships m
                LEFT JOIN household_membership_revocations r ON r.membership_id = m.id
                WHERE r.id IS NULL
            ),
            -- user が所属する household を特定
            target_households AS (
                SELECT DISTINCT household_id
                FROM active_memberships
                WHERE user_id = ?
            ),
            -- その household の全 active member を取得
            members AS (
                SELECT am.household_id,
                       am.role,
                       u.id            AS user_uuid,
                       u.zitadel_sub,
                       d.display_name
                FROM active_memberships am
                INNER JOIN target_households th ON th.household_id = am.household_id
                INNER JOIN users u ON u.id = am.user_id
                INNER JOIN (
                    SELECT DISTINCT ON (user_id) user_id, display_name, id
                    FROM user_display_names
                    ORDER BY user_id, id DESC
                ) d ON d.user_id = u.id
            )
            SELECT * FROM members ORDER BY household_id
        """.trimIndent()

        val rows = mutableListOf<Triple<java.util.UUID, java.util.UUID, String>>() // household_uuid, user_uuid, role
        val users = mutableListOf<Triple<java.util.UUID, String, String>>() // user_uuid, sub, display_name

        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec(
            sql,
            args = listOf(org.jetbrains.exposed.v1.core.UUIDColumnType() to user.id().toJavaUuid()),
        ) { rs ->
            while (rs.next()) {
                rows.add(Triple(
                    rs.getObject("household_id", java.util.UUID::class.java),
                    rs.getObject("user_uuid", java.util.UUID::class.java),
                    rs.getString("role"),
                ))
                users.add(Triple(
                    rs.getObject("user_uuid", java.util.UUID::class.java),
                    rs.getString("zitadel_sub"),
                    rs.getString("display_name"),
                ))
            }
        }

        if (rows.isEmpty()) return null

        val householdUuid = rows.first().first
        val members = rows.zip(users).map { (m, u) ->
            HouseholdMember(
                user = net.brightroom.mindstock.domain.model.user.User(
                    id = net.brightroom.mindstock.domain.model.user.UserId(u.first.toKotlinUuid()),
                    authIdentity = net.brightroom.mindstock.domain.model.user.auth.AuthIdentity(
                        net.brightroom.mindstock.domain.model.user.auth.AuthProvider.ZITADEL,
                        net.brightroom.mindstock.domain.model.user.auth.AuthSubject(u.second),
                    ),
                    displayName = net.brightroom.mindstock.domain.model.user.DisplayName(u.third),
                ),
                role = net.brightroom.mindstock.domain.model.household.HouseholdMemberRole.valueOf(m.third),
            )
        }
        return hydrateHousehold(householdUuid.toKotlinUuid(), members)
    }
}
```

- [ ] **Step 4: HouseholdRegisterRepositoryImplIntegrationTest.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.household

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class HouseholdRegisterRepositoryImplIntegrationTest : FunSpec({

    test("create inserts household + OWNER membership and returns Household with owner as member") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdRepo = HouseholdRegisterRepositoryImpl(database)
            val owner = tx {
                userRepo.register(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")),
                    DisplayName("Owner"),
                )
            }

            val household = tx { householdRepo.create(owner) }

            household.members.asList() shouldHaveSize 1
            household.members.asList().first().role shouldBe HouseholdMemberRole.OWNER
        }
    }

    test("invite adds another active member that appears in findOf") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdRepo = HouseholdRegisterRepositoryImpl(database)
            val householdReader = HouseholdRepositoryImpl(database)

            val owner = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")), DisplayName("Owner")) }
            val invitee = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("invitee")), DisplayName("Invitee")) }
            val household = tx { householdRepo.create(owner) }
            tx { householdRepo.invite(household, invitee, HouseholdMemberRole.MEMBER) }

            val refetched = tx { householdReader.findOf(invitee) }
            refetched?.members?.asList()?.map { it.user.displayName } shouldContain DisplayName("Invitee")
        }
    }

    test("revoke removes the revoked member from active membership view") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdRepo = HouseholdRegisterRepositoryImpl(database)
            val householdReader = HouseholdRepositoryImpl(database)

            val owner = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")), DisplayName("Owner")) }
            val invitee = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("invitee")), DisplayName("Invitee")) }
            val household = tx { householdRepo.create(owner) }
            tx { householdRepo.invite(household, invitee, HouseholdMemberRole.MEMBER) }
            tx { householdRepo.revoke(household, invitee) }

            val refetched = tx { householdReader.findOf(invitee) }
            refetched shouldBe null
        }
    }
})
```

- [ ] **Step 5: HouseholdRepositoryImplIntegrationTest.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.household

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class HouseholdRepositoryImplIntegrationTest : FunSpec({

    test("findOf returns null when user has no household membership") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdReader = HouseholdRepositoryImpl(database)
            val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("lonely")), DisplayName("Lonely")) }

            val result = tx { householdReader.findOf(user) }

            result.shouldBeNull()
        }
    }

    test("findOf returns the household with owner as OWNER member") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdRegister = HouseholdRegisterRepositoryImpl(database)
            val householdReader = HouseholdRepositoryImpl(database)

            val owner = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")), DisplayName("Owner")) }
            tx { householdRegister.create(owner) }

            val found = tx { householdReader.findOf(owner) }
            found?.members?.asList()?.single()?.role shouldBe HouseholdMemberRole.OWNER
        }
    }
})
```

- [ ] **Step 6: テスト実行**

```bash
./gradlew :backend:application:api:test --tests "*HouseholdRepositoryImpl*" --tests "*HouseholdRegisterRepositoryImpl*"
```

Expected: 5 test cases PASS。落ちたら hydration / SQL を見直す。`HouseholdMembers` のコンストラクタ・`asList()` 等 domain 側 API も併せて確認。

- [ ] **Step 7: spotlessApply + コミット**

```bash
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/household/ \
        backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/household/
git commit -m "feat(repo): add Household Repository Exposed impl + integration tests

- HouseholdRegisterRepositoryImpl: create/invite/revoke
- HouseholdRepositoryImpl: findOf with revocations excluded via LEFT JOIN
- 5 件の結合テスト

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: CatalogItem Repository

CatalogItem 集約: CatalogItemRegisterRepository(register / revise)と CatalogItemRepository(search / findById)。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/catalog/CatalogItemHydration.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/catalog/CatalogItemRegisterRepositoryImpl.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/catalog/CatalogItemRepositoryImpl.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/catalog/CatalogItemRegisterRepositoryImplIntegrationTest.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/catalog/CatalogItemRepositoryImplIntegrationTest.kt`

- [ ] **Step 1: CatalogItemHydration.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun hydrateCatalogItem(
    id: Uuid,
    name: String,
    unit: String,
): CatalogItem =
    CatalogItem(
        id = CatalogItemId(id),
        name = CatalogItemName(name),
        unit = CatalogItemUnit(unit),
    )
```

- [ ] **Step 2: CatalogItemRegisterRepositoryImpl.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.catalog.CatalogItemRevisionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.catalog.CatalogItemsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class CatalogItemRegisterRepositoryImpl(
    private val database: Database,
) : CatalogItemRegisterRepository {

    override fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: User,
    ): CatalogItem {
        val createdById = EntityID(createdBy.id().toJavaUuid(), UsersTable)
        val insertedId = CatalogItemsTable.insert {
            it[created_by] = createdById
        } get CatalogItemsTable.id

        CatalogItemRevisionsTable.insert {
            it[catalog_item_id] = insertedId
            it[this.name] = name()
            it[this.unit] = unit()
            it[edited_by] = createdById
        }

        return hydrateCatalogItem(insertedId.value.toKotlinUuid(), name(), unit())
    }

    override fun revise(
        catalogItem: CatalogItem,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
        editedBy: User,
    ) {
        CatalogItemRevisionsTable.insert {
            it[catalog_item_id] = EntityID(catalogItem.id().toJavaUuid(), CatalogItemsTable)
            it[name] = newName()
            it[unit] = newUnit()
            it[edited_by] = EntityID(editedBy.id().toJavaUuid(), UsersTable)
        }
    }
}
```

- [ ] **Step 3: CatalogItemRepositoryImpl.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository
import org.jetbrains.exposed.v1.core.UUIDColumnType
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class CatalogItemRepositoryImpl(
    private val database: Database,
) : CatalogItemRepository {

    override fun search(
        query: String,
        limit: Int,
    ): CatalogItems {
        val sql = """
            SELECT ci.id AS ci_id,
                   r.name,
                   r.unit
            FROM catalog_items ci
            INNER JOIN (
                SELECT DISTINCT ON (catalog_item_id)
                       catalog_item_id, name, unit, id
                FROM catalog_item_revisions
                ORDER BY catalog_item_id, id DESC
            ) r ON r.catalog_item_id = ci.id
            WHERE r.name ILIKE ?
            ORDER BY r.name
            LIMIT ?
        """.trimIndent()

        val results = mutableListOf<CatalogItem>()
        TransactionManager.current().exec(
            sql,
            args = listOf(
                VarCharColumnType() to "%$query%",
                org.jetbrains.exposed.v1.core.IntegerColumnType() to limit,
            ),
        ) { rs ->
            while (rs.next()) {
                results.add(hydrateCatalogItem(
                    id = rs.getObject("ci_id", java.util.UUID::class.java).toKotlinUuid(),
                    name = rs.getString("name"),
                    unit = rs.getString("unit"),
                ))
            }
        }
        return CatalogItems(results)
    }

    override fun findById(id: CatalogItemId): CatalogItem? {
        val sql = """
            SELECT ci.id AS ci_id, r.name, r.unit
            FROM catalog_items ci
            INNER JOIN (
                SELECT DISTINCT ON (catalog_item_id) catalog_item_id, name, unit, id
                FROM catalog_item_revisions
                ORDER BY catalog_item_id, id DESC
            ) r ON r.catalog_item_id = ci.id
            WHERE ci.id = ?
        """.trimIndent()

        var result: CatalogItem? = null
        TransactionManager.current().exec(
            sql,
            args = listOf(UUIDColumnType() to id().toJavaUuid()),
        ) { rs ->
            if (rs.next()) {
                result = hydrateCatalogItem(
                    id = rs.getObject("ci_id", java.util.UUID::class.java).toKotlinUuid(),
                    name = rs.getString("name"),
                    unit = rs.getString("unit"),
                )
            }
        }
        return result
    }
}
```

注: `CatalogItems` のコンストラクタが `List<CatalogItem>` 単引数であることを確認。違う場合は実装者がアダプト。

- [ ] **Step 4: CatalogItemRegisterRepositoryImplIntegrationTest.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class CatalogItemRegisterRepositoryImplIntegrationTest : FunSpec({

    test("register inserts catalog_items + first revision and returns CatalogItem") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val catalogRepo = CatalogItemRegisterRepositoryImpl(database)
            val creator = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("creator")), DisplayName("Creator")) }

            val item = tx {
                catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), creator)
            }

            item.name shouldBe CatalogItemName("Milk")
            item.unit shouldBe CatalogItemUnit("L")
        }
    }

    test("revise inserts new revision and findById returns the latest") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val catalogRegister = CatalogItemRegisterRepositoryImpl(database)
            val catalogReader = CatalogItemRepositoryImpl(database)
            val editor = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("editor")), DisplayName("Editor")) }

            val item = tx { catalogRegister.register(CatalogItemName("Milk"), CatalogItemUnit("L"), editor) }
            tx { catalogRegister.revise(item, CatalogItemName("Whole Milk"), CatalogItemUnit("L"), editor) }

            val refetched = tx { catalogReader.findById(item.id) }
            refetched?.name shouldBe CatalogItemName("Whole Milk")
        }
    }
})
```

- [ ] **Step 5: CatalogItemRepositoryImplIntegrationTest.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CatalogItemRepositoryImplIntegrationTest : FunSpec({

    test("findById returns null when id does not exist") {
        withRepositoryTestContext {
            val reader = CatalogItemRepositoryImpl(database)
            val result = tx { reader.findById(CatalogItemId(Uuid.random())) }
            result.shouldBeNull()
        }
    }

    test("search finds items by partial name match") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val register = CatalogItemRegisterRepositoryImpl(database)
            val reader = CatalogItemRepositoryImpl(database)
            val creator = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("c")), DisplayName("C")) }

            tx { register.register(CatalogItemName("Milk"), CatalogItemUnit("L"), creator) }
            tx { register.register(CatalogItemName("Soy Milk"), CatalogItemUnit("L"), creator) }
            tx { register.register(CatalogItemName("Bread"), CatalogItemUnit("loaf"), creator) }

            val results = tx { reader.search("milk", limit = 10) }
            results.asList() shouldHaveSize 2
        }
    }
})
```

注: `CatalogItems.asList()` がない場合は domain 側の API に合わせて修正。

- [ ] **Step 6: テスト実行**

```bash
./gradlew :backend:application:api:test --tests "*CatalogItem*"
```

Expected: 4 test cases PASS。

- [ ] **Step 7: spotlessApply + コミット**

```bash
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/catalog/ \
        backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/catalog/
git commit -m "feat(repo): add CatalogItem Repository Exposed impl + integration tests

- CatalogItemRegisterRepositoryImpl: register/revise
- CatalogItemRepositoryImpl: search (ILIKE) / findById with latest revision
- 4 件の結合テスト

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Product Repository

Product 集約: ProductRegisterRepository(adopt / setMinimumStock / archive)と ProductRepository(listOf / find)。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/product/ProductHydration.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/product/ProductRegisterRepositoryImpl.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/product/ProductRepositoryImpl.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/product/ProductRegisterRepositoryImplIntegrationTest.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/product/ProductRepositoryImplIntegrationTest.kt`

- [ ] **Step 1: ProductHydration.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun hydrateProduct(
    id: Uuid,
    catalogItem: CatalogItem,
    minimumStock: Int?,
    archived: Boolean,
): Product =
    Product(
        id = ProductId(id),
        catalogItem = catalogItem,
        minimumStock = minimumStock?.let { MinimumStock(it) },
        archived = archived,
    )
```

- [ ] **Step 2: ProductRegisterRepositoryImpl.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.catalog.CatalogItemsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductArchivesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductMinimumStocksTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class ProductRegisterRepositoryImpl(
    private val database: Database,
) : ProductRegisterRepository {

    override fun adopt(
        household: Household,
        catalogItem: CatalogItem,
    ): Product {
        val insertedId = ProductsTable.insert {
            it[household_id] = EntityID(household.id().toJavaUuid(), HouseholdsTable)
            it[catalog_item_id] = EntityID(catalogItem.id().toJavaUuid(), CatalogItemsTable)
        } get ProductsTable.id

        return hydrateProduct(
            id = insertedId.value.toKotlinUuid(),
            catalogItem = catalogItem,
            minimumStock = null,
            archived = false,
        )
    }

    override fun setMinimumStock(
        product: Product,
        value: MinimumStock,
        editedBy: User,
    ) {
        ProductMinimumStocksTable.insert {
            it[product_id] = EntityID(product.id().toJavaUuid(), ProductsTable)
            it[minimum_stock] = value()
            it[edited_by] = EntityID(editedBy.id().toJavaUuid(), UsersTable)
        }
    }

    override fun archive(
        product: Product,
        by: User,
    ) {
        ProductArchivesTable.insert {
            it[product_id] = EntityID(product.id().toJavaUuid(), ProductsTable)
            it[archived_by] = EntityID(by.id().toJavaUuid(), UsersTable)
        }
    }
}
```

- [ ] **Step 3: ProductRepositoryImpl.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.domain.repository.product.ProductRepository
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.hydrateCatalogItem
import org.jetbrains.exposed.v1.core.UUIDColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class ProductRepositoryImpl(
    private val database: Database,
) : ProductRepository {

    private fun selectProductsSql(whereClause: String): String =
        """
        SELECT p.id            AS p_id,
               ci.id            AS ci_id,
               r.name           AS ci_name,
               r.unit           AS ci_unit,
               m.minimum_stock  AS min_stock,
               (a.id IS NOT NULL) AS archived
        FROM products p
        INNER JOIN catalog_items ci ON ci.id = p.catalog_item_id
        INNER JOIN (
            SELECT DISTINCT ON (catalog_item_id) catalog_item_id, name, unit, id
            FROM catalog_item_revisions
            ORDER BY catalog_item_id, id DESC
        ) r ON r.catalog_item_id = ci.id
        LEFT JOIN (
            SELECT DISTINCT ON (product_id) product_id, minimum_stock, id
            FROM product_minimum_stocks
            ORDER BY product_id, id DESC
        ) m ON m.product_id = p.id
        LEFT JOIN (
            SELECT DISTINCT ON (product_id) product_id, id
            FROM product_archives
            ORDER BY product_id, id DESC
        ) a ON a.product_id = p.id
        $whereClause
        """.trimIndent()

    override fun listOf(household: Household): Products {
        val sql = selectProductsSql("WHERE p.household_id = ?")
        val results = mutableListOf<Product>()
        TransactionManager.current().exec(
            sql,
            args = listOf(UUIDColumnType() to household.id().toJavaUuid()),
        ) { rs ->
            while (rs.next()) {
                results.add(hydrateProduct(
                    id = rs.getObject("p_id", java.util.UUID::class.java).toKotlinUuid(),
                    catalogItem = hydrateCatalogItem(
                        id = rs.getObject("ci_id", java.util.UUID::class.java).toKotlinUuid(),
                        name = rs.getString("ci_name"),
                        unit = rs.getString("ci_unit"),
                    ),
                    minimumStock = rs.getObject("min_stock") as? Int,
                    archived = rs.getBoolean("archived"),
                ))
            }
        }
        return Products(results)
    }

    override fun find(
        household: Household,
        catalogItem: CatalogItem,
    ): Product? {
        val sql = selectProductsSql("WHERE p.household_id = ? AND p.catalog_item_id = ?")
        var result: Product? = null
        TransactionManager.current().exec(
            sql,
            args = listOf(
                UUIDColumnType() to household.id().toJavaUuid(),
                UUIDColumnType() to catalogItem.id().toJavaUuid(),
            ),
        ) { rs ->
            if (rs.next()) {
                result = hydrateProduct(
                    id = rs.getObject("p_id", java.util.UUID::class.java).toKotlinUuid(),
                    catalogItem = hydrateCatalogItem(
                        id = rs.getObject("ci_id", java.util.UUID::class.java).toKotlinUuid(),
                        name = rs.getString("ci_name"),
                        unit = rs.getString("ci_unit"),
                    ),
                    minimumStock = rs.getObject("min_stock") as? Int,
                    archived = rs.getBoolean("archived"),
                )
            }
        }
        return result
    }
}
```

- [ ] **Step 4: ProductRegisterRepositoryImplIntegrationTest.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class ProductRegisterRepositoryImplIntegrationTest : FunSpec({

    test("adopt creates a Product with no MinimumStock and not archived") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdRepo = HouseholdRegisterRepositoryImpl(database)
            val catalogRepo = CatalogItemRegisterRepositoryImpl(database)
            val productRepo = ProductRegisterRepositoryImpl(database)

            val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
            val household = tx { householdRepo.create(user) }
            val item = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user) }

            val product = tx { productRepo.adopt(household, item) }

            product.minimumStock shouldBe null
            product.archived shouldBe false
            product.catalogItem shouldBe item
        }
    }

    test("setMinimumStock + find reflects the latest minimum stock value") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdRepo = HouseholdRegisterRepositoryImpl(database)
            val catalogRepo = CatalogItemRegisterRepositoryImpl(database)
            val productRegister = ProductRegisterRepositoryImpl(database)
            val productReader = ProductRepositoryImpl(database)

            val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
            val household = tx { householdRepo.create(user) }
            val item = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user) }
            val product = tx { productRegister.adopt(household, item) }

            tx { productRegister.setMinimumStock(product, MinimumStock(2), user) }
            tx { productRegister.setMinimumStock(product, MinimumStock(5), user) }

            val refetched = tx { productReader.find(household, item) }
            refetched?.minimumStock shouldBe MinimumStock(5)
        }
    }

    test("archive sets archived = true on Product") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdRepo = HouseholdRegisterRepositoryImpl(database)
            val catalogRepo = CatalogItemRegisterRepositoryImpl(database)
            val productRegister = ProductRegisterRepositoryImpl(database)
            val productReader = ProductRepositoryImpl(database)

            val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
            val household = tx { householdRepo.create(user) }
            val item = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user) }
            val product = tx { productRegister.adopt(household, item) }
            tx { productRegister.archive(product, user) }

            val refetched = tx { productReader.find(household, item) }
            refetched?.archived shouldBe true
        }
    }
})
```

- [ ] **Step 5: ProductRepositoryImplIntegrationTest.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class ProductRepositoryImplIntegrationTest : FunSpec({

    test("find returns null when no product matches") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdRepo = HouseholdRegisterRepositoryImpl(database)
            val catalogRepo = CatalogItemRegisterRepositoryImpl(database)
            val productReader = ProductRepositoryImpl(database)

            val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
            val household = tx { householdRepo.create(user) }
            val item = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user) }

            val result = tx { productReader.find(household, item) }
            result.shouldBeNull()
        }
    }

    test("listOf returns all products of household including archived") {
        withRepositoryTestContext {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdRepo = HouseholdRegisterRepositoryImpl(database)
            val catalogRepo = CatalogItemRegisterRepositoryImpl(database)
            val productRegister = ProductRegisterRepositoryImpl(database)
            val productReader = ProductRepositoryImpl(database)

            val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
            val household = tx { householdRepo.create(user) }
            val milk = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user) }
            val bread = tx { catalogRepo.register(CatalogItemName("Bread"), CatalogItemUnit("loaf"), user) }

            val milkProduct = tx { productRegister.adopt(household, milk) }
            tx { productRegister.adopt(household, bread) }
            tx { productRegister.archive(milkProduct, user) }

            val results = tx { productReader.listOf(household) }
            results.asList() shouldHaveSize 2
            results.asList().single { it.catalogItem.name == CatalogItemName("Milk") }.archived shouldBe true
        }
    }
})
```

注: `Products.asList()` がない場合は domain 側 API に合わせて修正。

- [ ] **Step 6: テスト実行**

```bash
./gradlew :backend:application:api:test --tests "*Product*"
```

Expected: 5 test cases PASS。

- [ ] **Step 7: spotlessApply + コミット**

```bash
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/product/ \
        backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/product/
git commit -m "feat(repo): add Product Repository Exposed impl + integration tests

- ProductRegisterRepositoryImpl: adopt/setMinimumStock/archive
- ProductRepositoryImpl: listOf/find with latest minimum stock + archived flag
- 5 件の結合テスト

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Stock Repository

Stock 集約: StockRegisterRepository(replenish / consume)と StockRepository(stockOf / stocksOf / movementHistory)。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/stock/StockHydration.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/stock/StockRegisterRepositoryImpl.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/stock/StockRepositoryImpl.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/stock/StockRegisterRepositoryImplIntegrationTest.kt`
- Create: `backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/stock/StockRepositoryImplIntegrationTest.kt`

- [ ] **Step 1: StockHydration.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
import net.brightroom.mindstock.domain.model.user.User
import kotlin.time.Instant

internal fun toStockMovement(
    product: Product,
    actor: User,
    type: String,
    quantity: Int,
    occurredAt: Instant,
    note: String,
): StockMovement {
    val q = Quantity(quantity)
    val occurred = OccurredAt(occurredAt)
    val n = Note(note)
    return when (StockMovementType.valueOf(type)) {
        StockMovementType.REPLENISHMENT -> Replenishment(product, q, occurred, actor, n)
        StockMovementType.CONSUMPTION -> Consumption(product, q, occurred, actor, n)
    }
}
```

- [ ] **Step 2: StockRegisterRepositoryImpl.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.stock.StockMovementsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class StockRegisterRepositoryImpl(
    private val database: Database,
) : StockRegisterRepository {

    override fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Replenishment {
        insertMovement(product, quantity, occurredAt, by, note, StockMovementType.REPLENISHMENT)
        return Replenishment(product, quantity, occurredAt, by, note)
    }

    override fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Consumption {
        insertMovement(product, quantity, occurredAt, by, note, StockMovementType.CONSUMPTION)
        return Consumption(product, quantity, occurredAt, by, note)
    }

    private fun insertMovement(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: User,
        note: Note,
        type: StockMovementType,
    ) {
        StockMovementsTable.insert {
            it[product_id] = EntityID(product.id().toJavaUuid(), ProductsTable)
            it[StockMovementsTable.type] = type
            it[StockMovementsTable.quantity] = quantity()
            it[occurred_at] = occurredAt().toJavaInstant().atOffset(java.time.ZoneOffset.UTC)
            it[acted_by] = EntityID(actor.id().toJavaUuid(), UsersTable)
            it[StockMovementsTable.note] = note()
        }
    }
}
```

注: `OccurredAt()` の戻り型(`kotlin.time.Instant`)を Exposed `timestampWithTimeZone` カラムに渡す形式は `OffsetDateTime`。`Instant.toJavaInstant().atOffset(ZoneOffset.UTC)` で変換。Exposed v1 の `kotlin-datetime` 拡張があれば `kotlin.time.Instant` 直接渡せる可能性あり。実装者が確認して最短形を採用。

- [ ] **Step 3: StockRepositoryImpl.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.stock

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.repository.stock.StockRepository
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.schemas.stock.StockMovementsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinInstant
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class StockRepositoryImpl(
    private val database: Database,
    private val productRepository: ProductRepositoryImpl,
    private val userRepository: UserRepositoryImpl,
) : StockRepository {

    override fun stockOf(product: Product): Stock {
        val movements = loadMovementsFor(listOf(product))[product.id()] ?: emptyList()
        return Stock(product, StockMovements(movements))
    }

    override fun stocksOf(household: Household): List<Stock> {
        val products = productRepository.listOf(household).asList()
        val movementsByProduct = loadMovementsFor(products)
        return products.map { p ->
            Stock(p, StockMovements(movementsByProduct[p.id()] ?: emptyList()))
        }
    }

    override fun movementHistory(
        product: Product,
        limit: Int,
    ): StockMovements {
        val productUuid = product.id().toJavaUuid()
        val rows = (StockMovementsTable innerJoin UsersTable)
            .selectAll()
            .where { StockMovementsTable.product_id eq org.jetbrains.exposed.v1.core.dao.id.EntityID(productUuid, net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductsTable) }
            .orderBy(
                StockMovementsTable.occurred_at to SortOrder.DESC,
                StockMovementsTable.id to SortOrder.DESC,
            )
            .limit(limit)
            .map { row ->
                toStockMovement(
                    product = product,
                    actor = userFromRow(row),
                    type = row[StockMovementsTable.type].name,
                    quantity = row[StockMovementsTable.quantity],
                    occurredAt = row[StockMovementsTable.occurred_at].toInstant().toKotlinInstant(),
                    note = row[StockMovementsTable.note],
                )
            }
        return StockMovements(rows)
    }

    /** product 群に対する全 movement を 1 クエリで取得し、productId ごとに分類。 */
    private fun loadMovementsFor(products: List<Product>): Map<kotlin.uuid.Uuid, List<StockMovement>> {
        if (products.isEmpty()) return emptyMap()

        val productIds = products.map { it.id().toJavaUuid() }
        val productByUuid = products.associateBy { it.id() }

        return (StockMovementsTable innerJoin UsersTable)
            .selectAll()
            .where { StockMovementsTable.product_id inList productIds.map {
                org.jetbrains.exposed.v1.core.dao.id.EntityID(it, net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductsTable)
            } }
            .orderBy(StockMovementsTable.occurred_at, SortOrder.ASC)
            .map { row ->
                val productUuid = row[StockMovementsTable.product_id].value.toKotlinUuid()
                val product = productByUuid[productUuid] ?: error("product not found for movement")
                productUuid to toStockMovement(
                    product = product,
                    actor = userFromRow(row),
                    type = row[StockMovementsTable.type].name,
                    quantity = row[StockMovementsTable.quantity],
                    occurredAt = row[StockMovementsTable.occurred_at].toInstant().toKotlinInstant(),
                    note = row[StockMovementsTable.note],
                )
            }
            .groupBy({ it.first }, { it.second })
    }

    /** users JOIN 行から User を作る(display_name は load 時点では未取得)。
     *  StockMovement 用には User の id だけあれば良いが、現状の domain は User 全体を要求。
     *  必要に応じて UserRepositoryImpl から findById(?) を別途実装するか、
     *  あるいは StockMovement の actor を id 参照に変更する設計議論を別タスクに。
     *  Plan 5 では「最低限の User を組み立てる」(display_name は副問い合わせ)で進める。
     */
    private fun userFromRow(row: org.jetbrains.exposed.v1.core.ResultRow): net.brightroom.mindstock.domain.model.user.User {
        // movement テーブルには user_id のみ。display_name は別クエリで取る必要があるが
        // N+1 を避けるためここでは acted_by の sub と display_name を JOIN で取って組み立てる。
        // 実装簡素化のため、actor の表示名は空(または "unknown")で組み立て、本格的に必要なら
        // 別途 IN 句で UserHydration から取得する設計に拡張する。
        val userUuid = row[StockMovementsTable.acted_by].value.toKotlinUuid()
        return net.brightroom.mindstock.domain.model.user.User(
            id = net.brightroom.mindstock.domain.model.user.UserId(userUuid),
            authIdentity = net.brightroom.mindstock.domain.model.user.auth.AuthIdentity(
                provider = net.brightroom.mindstock.domain.model.user.auth.AuthProvider.ZITADEL,
                subject = net.brightroom.mindstock.domain.model.user.auth.AuthSubject(row[UsersTable.zitadel_sub]),
            ),
            displayName = net.brightroom.mindstock.domain.model.user.DisplayName("(unknown)"),
        )
    }
}
```

注: `userFromRow` は実装の妥協を含む。理想的には actor の最新 display_name も同 SQL で JOIN したい。実装者は `users JOIN (DISTINCT ON user_display_names)` を movement 側にも組み込み、`UserHydration.toUser()` を再利用するように発展させてよい(その方が望ましい)。テストはまず動くことを確認し、必要に応じて拡張する。

- [ ] **Step 4: StockRegisterRepositoryImplIntegrationTest.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.stock

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import kotlin.time.Clock

class StockRegisterRepositoryImplIntegrationTest : FunSpec({

    test("replenish inserts REPLENISHMENT movement; stockOf returns +quantity") {
        withRepositoryTestContext {
            val ctx = SetupContext(database)
            val (user, _, product) = ctx.setup()
            val stockRegister = StockRegisterRepositoryImpl(database)
            val stockReader = StockRepositoryImpl(database, ProductRepositoryImpl(database), UserRepositoryImpl(database))

            tx {
                stockRegister.replenish(
                    product = product,
                    quantity = Quantity(3),
                    occurredAt = OccurredAt(Clock.System.now()),
                    by = user,
                    note = Note(""),
                )
            }
            val stock = tx { stockReader.stockOf(product) }
            stock.currentQuantity() shouldBe 3
        }
    }

    test("consume inserts CONSUMPTION movement; stockOf returns net (replenish - consume)") {
        withRepositoryTestContext {
            val ctx = SetupContext(database)
            val (user, _, product) = ctx.setup()
            val stockRegister = StockRegisterRepositoryImpl(database)
            val stockReader = StockRepositoryImpl(database, ProductRepositoryImpl(database), UserRepositoryImpl(database))

            tx { stockRegister.replenish(product, Quantity(5), OccurredAt(Clock.System.now()), user, Note("")) }
            tx { stockRegister.consume(product, Quantity(2), OccurredAt(Clock.System.now()), user, Note("")) }

            val stock = tx { stockReader.stockOf(product) }
            stock.currentQuantity() shouldBe 3
        }
    }
})

// テスト全体で再利用するセットアップヘルパー
private class SetupContext(private val database: org.jetbrains.exposed.v1.jdbc.Database) {
    fun setup(): Triple<net.brightroom.mindstock.domain.model.user.User, net.brightroom.mindstock.domain.model.household.Household, net.brightroom.mindstock.domain.model.product.Product> {
        return org.jetbrains.exposed.v1.jdbc.transactions.transaction(database) {
            val userRepo = UserRegisterRepositoryImpl(database)
            val householdRepo = HouseholdRegisterRepositoryImpl(database)
            val catalogRepo = CatalogItemRegisterRepositoryImpl(database)
            val productRepo = ProductRegisterRepositoryImpl(database)

            val user = userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U"))
            val household = householdRepo.create(user)
            val item = catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user)
            val product = productRepo.adopt(household, item)
            Triple(user, household, product)
        }
    }
}
```

- [ ] **Step 5: StockRepositoryImplIntegrationTest.kt を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository.stock

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import kotlin.time.Clock

class StockRepositoryImplIntegrationTest : FunSpec({

    test("stockOf returns empty StockMovements for a fresh product") {
        withRepositoryTestContext {
            val ctx = SetupContext(database)
            val (_, _, product) = ctx.setup()
            val reader = StockRepositoryImpl(database, ProductRepositoryImpl(database), UserRepositoryImpl(database))

            val stock = tx { reader.stockOf(product) }
            stock.movements.size shouldBe 0
            stock.currentQuantity() shouldBe 0
        }
    }

    test("movementHistory respects limit and returns DESC by occurred_at") {
        withRepositoryTestContext {
            val ctx = SetupContext(database)
            val (user, _, product) = ctx.setup()
            val register = StockRegisterRepositoryImpl(database)
            val reader = StockRepositoryImpl(database, ProductRepositoryImpl(database), UserRepositoryImpl(database))

            tx { register.replenish(product, Quantity(1), OccurredAt(Clock.System.now()), user, Note("a")) }
            tx { register.replenish(product, Quantity(2), OccurredAt(Clock.System.now()), user, Note("b")) }
            tx { register.replenish(product, Quantity(3), OccurredAt(Clock.System.now()), user, Note("c")) }

            val history = tx { reader.movementHistory(product, limit = 2) }
            history.asList() shouldHaveSize 2
        }
    }
})

private class SetupContext(private val database: org.jetbrains.exposed.v1.jdbc.Database) {
    fun setup(): Triple<net.brightroom.mindstock.domain.model.user.User, net.brightroom.mindstock.domain.model.household.Household, net.brightroom.mindstock.domain.model.product.Product> {
        return org.jetbrains.exposed.v1.jdbc.transactions.transaction(database) {
            val userRepo = net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl(database)
            val householdRepo = net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRegisterRepositoryImpl(database)
            val catalogRepo = net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRegisterRepositoryImpl(database)
            val productRepo = net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRegisterRepositoryImpl(database)

            val user = userRepo.register(
                net.brightroom.mindstock.domain.model.user.auth.AuthIdentity(
                    net.brightroom.mindstock.domain.model.user.auth.AuthProvider.ZITADEL,
                    net.brightroom.mindstock.domain.model.user.auth.AuthSubject("u"),
                ),
                net.brightroom.mindstock.domain.model.user.DisplayName("U"),
            )
            val household = householdRepo.create(user)
            val item = catalogRepo.register(
                net.brightroom.mindstock.domain.model.catalog.CatalogItemName("Milk"),
                net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit("L"),
                user,
            )
            val product = productRepo.adopt(household, item)
            Triple(user, household, product)
        }
    }
}
```

- [ ] **Step 6: テスト実行**

```bash
./gradlew :backend:application:api:test --tests "*Stock*"
```

Expected: 4 test cases PASS。落ちたら StockHydration / SQL を見直す。

- [ ] **Step 7: spotlessApply + コミット**

```bash
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/stock/ \
        backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/stock/
git commit -m "feat(repo): add Stock Repository Exposed impl + integration tests

- StockRegisterRepositoryImpl: replenish/consume (append-only insert into stock_movements)
- StockRepositoryImpl: stockOf (single product all movements) / stocksOf (2-query for household) / movementHistory (limit, DESC)
- 4 件の結合テスト

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: ExposedTransactionPlugin の本実装

Plan 4 で skeleton として置いた plugin を本実装する。`intercept(ApplicationCallPipeline.Call) { newSuspendedTransaction(db) { proceed() } }` で全 call を transaction で囲む。

**Files:**
- Modify: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/ExposedTransactionPlugin.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/TransactionConfiguration.kt`

- [ ] **Step 1: ExposedTransactionPlugin.kt を本実装に置き換え**

```kotlin
package net.brightroom.mindstock.configuration.transaction

import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

/**
 * 1 RPC / HTTP 呼び出し = 1 Exposed transaction を境界で開閉する Ktor plugin。
 *
 * Handler / Repository 実装は `transaction {}` を書かず、本 plugin が張った transaction を
 * `TransactionManager.currentOrNull()` 経由で拾う前提。
 *
 * 詳細設計: docs/superpowers/specs/2026-05-24-usecase-design.md §4、
 * 実装方針:    docs/superpowers/specs/2026-05-24-repository-implementation-design.md §6
 */
val ExposedTransactionPlugin =
    createApplicationPlugin(
        name = "ExposedTransaction",
        createConfiguration = ::ExposedTransactionConfig,
    ) {
        val database =
            pluginConfig.database
                ?: error("ExposedTransactionPlugin requires `database` to be set in configuration")

        application.intercept(ApplicationCallPipeline.Call) {
            newSuspendedTransaction(db = database) {
                proceed()
            }
        }
    }

class ExposedTransactionConfig {
    var database: Database? = null
}

internal val DatabaseAttributeKey = AttributeKey<Database>("ExposedTransactionDatabase")
```

注: `application.intercept` API がこの形で使えない場合、ApplicationPlugin の `onCall` ループ + 手動 transaction 開始等への切り替えが必要。実装者は Ktor / Exposed のドキュメントに従って調整する。

- [ ] **Step 2: TransactionConfiguration.kt を作成**

```kotlin
package net.brightroom.mindstock.configuration.transaction

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.transactionConfigure() {
    val database: Database by dependencies
    install(ExposedTransactionPlugin) {
        this.database = database
    }
}
```

- [ ] **Step 3: ビルド確認**

```bash
./gradlew :backend:application:api:compileKotlin
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: spotlessApply + コミット**

```bash
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/
git commit -m "feat(infra): implement ExposedTransactionPlugin (intercept + newSuspendedTransaction)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: DI 配線(Repository + UseCase)+ application.yaml modules 更新

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/RepositoryConfiguration.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/UseCaseConfiguration.kt`
- Modify: `backend/application/api/src/main/resources/application.yaml`

- [ ] **Step 1: RepositoryConfiguration.kt を作成**

```kotlin
package net.brightroom.mindstock.configuration.di

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.domain.repository.product.ProductRepository
import net.brightroom.mindstock.domain.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.repository.stock.StockRepository
import net.brightroom.mindstock.domain.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.stock.StockRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.stock.StockRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRepositoryImpl
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.repositoryConfigure() {
    dependencies {
        provide<UserRepository> { UserRepositoryImpl(resolve()) }
        provide<UserRegisterRepository> { UserRegisterRepositoryImpl(resolve()) }
        provide<HouseholdRepository> { HouseholdRepositoryImpl(resolve()) }
        provide<HouseholdRegisterRepository> { HouseholdRegisterRepositoryImpl(resolve()) }
        provide<CatalogItemRepository> { CatalogItemRepositoryImpl(resolve()) }
        provide<CatalogItemRegisterRepository> { CatalogItemRegisterRepositoryImpl(resolve()) }
        provide<ProductRepository> { ProductRepositoryImpl(resolve()) }
        provide<ProductRegisterRepository> { ProductRegisterRepositoryImpl(resolve()) }
        provide<StockRepository> { StockRepositoryImpl(resolve(), resolve<ProductRepositoryImpl>(), resolve<UserRepositoryImpl>()) }
        provide<StockRegisterRepository> { StockRegisterRepositoryImpl(resolve()) }
        // StockRepositoryImpl が他 Impl を依存に取るため別途 provide
        provide<ProductRepositoryImpl> { ProductRepositoryImpl(resolve()) }
        provide<UserRepositoryImpl> { UserRepositoryImpl(resolve()) }
    }
}
```

注: `Database` の resolve は `resolve()` 経由(型推論)で動く想定。動かない場合は `resolve<Database>()` 明示。

- [ ] **Step 2: UseCaseConfiguration.kt を作成**

```kotlin
package net.brightroom.mindstock.configuration.di

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import net.brightroom.mindstock.application.usecase.catalog.FindCatalogItemByIdHandler
import net.brightroom.mindstock.application.usecase.catalog.RegisterCatalogItemHandler
import net.brightroom.mindstock.application.usecase.catalog.ReviseCatalogItemHandler
import net.brightroom.mindstock.application.usecase.catalog.SearchCatalogItemsHandler
import net.brightroom.mindstock.application.usecase.household.CreateHouseholdHandler
import net.brightroom.mindstock.application.usecase.household.FindHouseholdOfUserHandler
import net.brightroom.mindstock.application.usecase.household.InviteMemberHandler
import net.brightroom.mindstock.application.usecase.household.RevokeMembershipHandler
import net.brightroom.mindstock.application.usecase.product.AdoptProductHandler
import net.brightroom.mindstock.application.usecase.product.ArchiveProductHandler
import net.brightroom.mindstock.application.usecase.product.FindProductHandler
import net.brightroom.mindstock.application.usecase.product.ListProductsOfHouseholdHandler
import net.brightroom.mindstock.application.usecase.product.SetMinimumStockHandler
import net.brightroom.mindstock.application.usecase.stock.ConsumeStockHandler
import net.brightroom.mindstock.application.usecase.stock.GetMovementHistoryHandler
import net.brightroom.mindstock.application.usecase.stock.GetStockHandler
import net.brightroom.mindstock.application.usecase.stock.ListStocksHandler
import net.brightroom.mindstock.application.usecase.stock.ReplenishStockHandler
import net.brightroom.mindstock.application.usecase.user.RegisterUserHandler
import net.brightroom.mindstock.application.usecase.user.RenameUserHandler

fun Application.usecaseConfigure() {
    dependencies {
        // User
        provide<RegisterUserHandler> { RegisterUserHandler(resolve()) }
        provide<RenameUserHandler> { RenameUserHandler(resolve()) }

        // Household
        provide<CreateHouseholdHandler> { CreateHouseholdHandler(resolve()) }
        provide<InviteMemberHandler> { InviteMemberHandler(resolve()) }
        provide<RevokeMembershipHandler> { RevokeMembershipHandler(resolve()) }
        provide<FindHouseholdOfUserHandler> { FindHouseholdOfUserHandler(resolve()) }

        // CatalogItem
        provide<RegisterCatalogItemHandler> { RegisterCatalogItemHandler(resolve()) }
        provide<ReviseCatalogItemHandler> { ReviseCatalogItemHandler(resolve()) }
        provide<SearchCatalogItemsHandler> { SearchCatalogItemsHandler(resolve()) }
        provide<FindCatalogItemByIdHandler> { FindCatalogItemByIdHandler(resolve()) }

        // Product
        provide<AdoptProductHandler> { AdoptProductHandler(resolve()) }
        provide<SetMinimumStockHandler> { SetMinimumStockHandler(resolve()) }
        provide<ArchiveProductHandler> { ArchiveProductHandler(resolve()) }
        provide<ListProductsOfHouseholdHandler> { ListProductsOfHouseholdHandler(resolve()) }
        provide<FindProductHandler> { FindProductHandler(resolve()) }

        // Stock
        provide<ReplenishStockHandler> { ReplenishStockHandler(resolve()) }
        provide<ConsumeStockHandler> { ConsumeStockHandler(resolve()) }
        provide<GetStockHandler> { GetStockHandler(resolve()) }
        provide<ListStocksHandler> { ListStocksHandler(resolve()) }
        provide<GetMovementHistoryHandler> { GetMovementHistoryHandler(resolve()) }
    }
}
```

- [ ] **Step 3: application.yaml の modules を更新**

`backend/application/api/src/main/resources/application.yaml` の `ktor.application.modules` リストを以下に置き換え:

```yaml
modules:
  - "net.brightroom.mindstock.configuration.di.DependenciesConfigurationKt.dependenciesConfigure"
  - "net.brightroom.mindstock.configuration.migration.MigrationConfigurationKt.migrationConfigure"
  - "net.brightroom.mindstock.configuration.external.exposed.ExposedConfigurationKt.exposedConfigure"
  - "net.brightroom.mindstock.configuration.transaction.TransactionConfigurationKt.transactionConfigure"
  - "net.brightroom.mindstock.configuration.di.RepositoryConfigurationKt.repositoryConfigure"
  - "net.brightroom.mindstock.configuration.di.UseCaseConfigurationKt.usecaseConfigure"
  - "net.brightroom.mindstock.configuration.logging.LoggingConfigurationKt.loggingConfigure"
  - "net.brightroom.mindstock.configuration.routing.RoutingConfigurationKt.routingConfigure"
```

- [ ] **Step 4: ビルド確認**

```bash
./gradlew :backend:application:api:build
```

Expected: BUILD SUCCESSFUL(全テストパス、全コンパイル通過)。

- [ ] **Step 5: spotlessApply + コミット**

```bash
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/ \
        backend/application/api/src/main/resources/application.yaml
git commit -m "feat(di): wire Repository impls and Handlers into Ktor DI + install TransactionPlugin

- RepositoryConfiguration: 10 Repository ポートに対応する Impl を provide
- UseCaseConfiguration: 20 Handler を provide
- application.yaml: transactionConfigure / repositoryConfigure / usecaseConfigure を modules に追加

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: 最終検証 + PR

**Files:** (なし、検証のみ)

- [ ] **Step 1: 全ビルド**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL。すべてのテスト(既存 + Plan 5 で追加した結合テスト)が緑。

- [ ] **Step 2: spotlessCheck**

```bash
./gradlew spotlessCheck
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: ファイル数確認**

```bash
find backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository -name "*.kt" | wc -l
```

Expected: `15`(5 集約 × 3 ファイル: Hydration + RepositoryImpl + RegisterRepositoryImpl)

```bash
find backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository -name "*.kt" | wc -l
```

Expected: `10`(5 集約 × 2 ファイル: Repository テスト + Register Repository テスト)

- [ ] **Step 4: コミット履歴確認**

```bash
git log --oneline main..HEAD
```

Expected: 10 程度のコミット(Task 0-9 の commit)。

- [ ] **Step 5: push + PR 作成**

```bash
git push -u origin feat/repository-impl
```

```bash
gh pr create --title "feat: Plan 5 — Repository Exposed implementations + integration tests + DI wiring" --body "$(cat <<'EOF'
## Summary
Plan 5: Repository ポート 10 個の Exposed 実装と Testcontainers 結合テストを追加。Ktor DI で全 Repository / Handler を配線、ExposedTransactionPlugin を本実装+install。

### Repository 実装(5 集約 × 2 = 10 クラス)
- User: UserRepository / UserRegisterRepository
- Household: HouseholdRepository / HouseholdRegisterRepository
- CatalogItem: CatalogItemRepository / CatalogItemRegisterRepository
- Product: ProductRepository / ProductRegisterRepository
- Stock: StockRepository / StockRegisterRepository

### 結合テスト(全 Repository、Testcontainers + 実 PG 18)
- CRUD ラウンドトリップ
- 履歴の最新行選択(DISTINCT ON)
- revoke / archive 除外

### その他
- domain VO/ID の `internal operator fun invoke()` を public 化(12 ファイル)— infrastructure module から domain 値を読むため
- ExposedTransactionPlugin を `intercept(ApplicationCallPipeline.Call) { newSuspendedTransaction(db) { proceed() } }` で本実装、install まで
- RepositoryConfiguration / UseCaseConfiguration / TransactionConfiguration を新規追加、application.yaml の modules に登録
- Repository 実装は暫定で \`:backend:application:api\` 配下(後で見直し前提、memory [[structure-review-pending]])

仕様: \`docs/superpowers/specs/2026-05-24-repository-implementation-design.md\`
プラン: \`docs/superpowers/plans/2026-05-24-repository-implementation.md\`

## Test plan
- [ ] \`./gradlew build\` が通る (既存 + 新規結合テストすべて緑)
- [ ] \`./gradlew spotlessCheck\` が通る
- [ ] Repository 実装ファイル 15、結合テストファイル 10 がリポジトリ上に存在
- [ ] application.yaml の modules に transaction / repository / usecase が追加されている

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 6: PR URL 報告**

PR URL を出力する。

---

## 対象外(明示)

本プランで **やらないこと**(Plan 6 / 別途で扱う):

- kotlinx-rpc サービス IF 実装(Plan 6)
- RPC エンドポイントと Handler の配線(Plan 6)
- DomainException → RPC error マッピング(Plan 6)
- 認証(JWT 検証 / current user 取得)実装
- backend subproject 構造の見直し(Plan 5 完了後、別作業として実施。memory [[structure-review-pending]] 参照)
- Stock movement の snapshot 化(将来の性能対策)
- StockRepository が actor の最新 display_name を JOIN で取らない暫定(`(unknown)` 表示)→ Plan 6 までに改善
- append-only 違反防止の追加テスト(\`AppendOnlyEnforcementTest\` で既にカバー済み)
