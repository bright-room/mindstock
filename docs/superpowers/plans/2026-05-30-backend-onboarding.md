# Backend オンボーディング Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 初回サインインで User + 世帯(デフォルト名)+ OWNER membership を原子的・冪等に作る `RegisterFirstHouseholdScenario` と、WS 経路でセッション初期化情報を返す `bootstrap()` RPC を追加する。あわせて世帯名(`HouseholdName` / `household_names` 事実テーブル)を導入する。

**Architecture:** Phase A で世帯名ドメイン(VO + 事実テーブル + repository 変更)を入れ、Phase B でオンボーディング(初の Scenario)と初期化 RPC を組む。認証は既存の WS subprotocol 一本を維持し、bootstrap は「JWT 有効なら未登録でも通る」既存 route(旧 `/user/public`)を `OnboardingRpcService` として再定義して乗せる。登録判定は専用フラグを持たず、user 行 + OWNER membership の存在から導出する。

**Tech Stack:** Kotlin/JVM, Ktor, Ktor DI, Exposed(`newSuspendedTransaction` / Exposed migration plugin), kotlinx-rpc(`KrpcJson` = POLYMORPHIC discriminator), Kotest(FunSpec) + MockK, Testcontainers(integration)。

**親 spec:** [docs/superpowers/specs/2026-05-30-frontend-onboarding-foundation-design.md](../specs/2026-05-30-frontend-onboarding-foundation-design.md)

---

## 前提・重要な約束

- **冪等性は Scenario レベル**で担保する(`household_memberships` に `UNIQUE(user_id)` を張らない。将来の複数ユーザー世帯を塞がない)。
- 「不在」判定は `try { ... } catch (e: ResourceNotFoundException) { null }`(`MindstockAuthPlugin` の既存慣行)。
- **`Household` の constructor が `(id, members)` → `(id, name, members)` に変わる破壊的変更**。生成箇所は `hydrateHousehold`(本体)と各テストの `Household(...)` 直書き。Phase A で漏れなく追従する。
- 世帯名テーブルは `user_display_names` と同型(`HistoryTable` ベース、`household_id` 参照、最新行が現在値)。`latestHouseholdNames()` は `latestDisplayNames()` の写し。
- migration は **Exposed migration plugin が Table 定義から生成**する(手書きしない)。append-only role は `ALTER DEFAULT PRIVILEGES`(`V00000000000000__append_only_role.sql`)で新テーブルに SELECT/INSERT + sequence USAGE が自動付与される。
- **`HouseholdName` 最大長 = 100**(`DisplayName` に合わせる)。デフォルト世帯名 = `"${displayName().take(97)}の家"`(100 を超えない)。
- bootstrap の戻り値 `SessionBootstrap` は sealed。RPC は `KrpcJson`(POLYMORPHIC discriminator)なので sealed polymorphic が安全に通る。

---

## File Structure

**Phase A(世帯名ドメイン)**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdName.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdNameTest.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`(`name` 追加)
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdNamesTable.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/LatestHouseholdNames.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdHydration.kt`(`name` 追加)
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/household/HouseholdRegisterRepository.kt`(`create` シグネチャ)
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/household/HouseholdRegisterService.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdRegisterDataSource.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSource.kt`(`findOf`/`findById` で name hydrate)
- Create: `backend/core/src/main/resources/db/migration/V<timestamp>__household_names.sql`(生成)

**Phase B(オンボーディング + bootstrap)**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenario.kt`
- Create: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenarioTest.kt`
- Rename: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/UserPublicRpcService.kt` → `OnboardingRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/SessionBootstrap.kt`
- Rename/Modify: `backend/api/.../presentation/rpc/user/UserPublicController.kt` → `onboarding/OnboardingController.kt`(+ Factory)
- Modify: `backend/api/.../configuration/di/DependenciesConfiguration.kt`
- Modify: `backend/api/.../configuration/routing/RoutingConfiguration.kt`
- Modify: `backend/api/.../presentation/rpc/user/UserPublicControllerTest.kt` → onboarding 配下へ

---

# Phase A — 世帯名ドメイン

## Task A1: HouseholdName VO

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdName.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdNameTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HouseholdNameTest :
    FunSpec({
        test("値を保持し toString / invoke で取り出せる") {
            val name = HouseholdName("我が家")
            name.toString() shouldBe "我が家"
            name() shouldBe "我が家"
        }

        test("空白のみは拒否する") {
            shouldThrow<IllegalArgumentException> { HouseholdName(" ") }
        }

        test("100 文字超は拒否する") {
            shouldThrow<IllegalArgumentException> { HouseholdName("あ".repeat(101)) }
        }
    })
```

- [ ] **Step 2: テスト失敗を確認**

Run: `./gradlew :domain:allTests --tests "*HouseholdNameTest*"`
Expected: コンパイルエラー(`HouseholdName` 未定義)。

- [ ] **Step 3: 実装(`DisplayName` の写し)**

```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * 世帯名。空白のみ(空文字含む)禁止、最大 100 文字。後から変更可能(履歴は household_names 事実テーブル)。
 */
@Serializable
@JvmInline
value class HouseholdName(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "household name must not be blank" }
        require(value.length <= 100) { "household name length ${value.length} > 100" }
    }

    override fun toString(): String = value

    operator fun invoke(): String = value
}
```

- [ ] **Step 4: テスト成功を確認**

Run: `./gradlew :domain:allTests --tests "*HouseholdNameTest*"`
Expected: PASS(3 tests)。

- [ ] **Step 5: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdName.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdNameTest.kt
git commit -m "feat(domain): HouseholdName VO を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task A2: Household に name を追加(破壊的変更の追従)

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdHydration.kt`

- [ ] **Step 1: Household に name を追加**

`Household.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

/**
 * 世帯集約。世帯名とアクティブなメンバー一覧を持つ。
 */
@Serializable
data class Household(
    val id: HouseholdId,
    val name: HouseholdName,
    val members: HouseholdMembers,
)
```

- [ ] **Step 2: コンパイルを走らせて全壊箇所を洗い出す**

Run: `./gradlew :domain:compileKotlinJvm :backend:core:compileKotlin`
Expected: `Household(...)` の呼び出し箇所が「引数不足」でコンパイルエラー。**ここで出たエラー箇所が Task A4/A5 で直す対象**。`hydrateHousehold` が主。

- [ ] **Step 3: hydrateHousehold に name を通す**

`HouseholdHydration.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.household.HouseholdName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun hydrateHousehold(
    householdId: Uuid,
    name: HouseholdName,
    members: List<HouseholdMember>,
): Household =
    Household(
        id = HouseholdId(householdId),
        name = name,
        members = HouseholdMembers(members),
    )
```

- [ ] **Step 4: コミット(コンパイルはまだ赤でよい。A4/A5 で緑にする)**

> 注: `hydrateHousehold` の呼び出し元(`HouseholdRegisterDataSource` / `HouseholdDataSource`)は A4/A5 で直す。ここでは domain の確定だけコミットする。

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdHydration.kt
git commit -m "feat(domain): Household に HouseholdName を追加(以降のタスクで生成箇所を追従)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task A3: household_names テーブル + 最新値クエリ

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdNamesTable.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/LatestHouseholdNames.kt`

- [ ] **Step 1: HouseholdNamesTable(`UserDisplayNamesTable` の写し)**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.infrastructure.datasource.HistoryTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object HouseholdNamesTable : HistoryTable("household_names") {
    val household_id = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", 100)
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, household_id, id)
    }
}
```

- [ ] **Step 2: LatestHouseholdNames(`LatestDisplayNames` の写し)**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.household

import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class LatestHouseholdNames(
    val alias: QueryAlias,
    val householdId: ExpressionWithColumnType<Uuid>,
    val maxId: ExpressionWithColumnType<Long?>,
)

@OptIn(ExperimentalUuidApi::class)
internal fun latestHouseholdNames(): LatestHouseholdNames {
    val maxIdExpr = HouseholdNamesTable.id.max().alias("max_household_name_id")
    val alias =
        HouseholdNamesTable
            .select(HouseholdNamesTable.household_id, maxIdExpr)
            .groupBy(HouseholdNamesTable.household_id)
            .alias("latest_household_names")
    return LatestHouseholdNames(
        alias = alias,
        householdId = alias[HouseholdNamesTable.household_id],
        maxId = alias[maxIdExpr],
    )
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: `HouseholdNamesTable` / `latestHouseholdNames` 自体は通る(`HouseholdRegisterDataSource` / `HouseholdDataSource` は A2 の影響でまだ赤)。

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdNamesTable.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/LatestHouseholdNames.kt
git commit -m "feat(infra): household_names テーブルと最新値クエリを追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task A4: create に世帯名を追加(repository / service / datasource)

**Files:**
- Modify: `backend/core/.../application/repository/household/HouseholdRegisterRepository.kt`
- Modify: `backend/core/.../application/service/household/HouseholdRegisterService.kt`
- Modify: `backend/core/.../infrastructure/datasource/household/HouseholdRegisterDataSource.kt`
- Test: `backend/api/src/test/.../infrastructure/datasource/repository/household/HouseholdRegisterDataSourceIntegrationTest`(既存があれば追従。無ければ `HouseholdDataSourceIntegrationTest` に追記)

- [ ] **Step 1: interface のシグネチャ変更**

`HouseholdRegisterRepository.kt` の `create` を変更:

```kotlin
import net.brightroom.mindstock.domain.model.household.HouseholdName
// ...
    /** households + household_names + 初回 household_memberships(OWNER)を 1 トランザクションで INSERT。 */
    fun create(
        ownerId: UserId,
        name: HouseholdName,
    ): Household
```

- [ ] **Step 2: Service の委譲を変更**

`HouseholdRegisterService.kt`:

```kotlin
import net.brightroom.mindstock.domain.model.household.HouseholdName
// ...
    fun create(
        ownerId: UserId,
        name: HouseholdName,
    ): Household = householdRegisterRepository.create(ownerId, name)
```

- [ ] **Step 3: DataSource を変更(household_names INSERT + hydrate に name)**

`HouseholdRegisterDataSource.kt` の `create` を以下に置換(import に `HouseholdName` を追加):

```kotlin
    override fun create(
        ownerId: UserId,
        name: HouseholdName,
    ): Household {
        val newHouseholdId =
            HouseholdsTable.insert {
                // id は default uuidv7()
            } get HouseholdsTable.id

        HouseholdNamesTable.insert {
            it[household_id] = newHouseholdId
            it[this.name] = name()
        }

        HouseholdMembershipsTable.insert {
            it[household_id] = newHouseholdId
            it[user_id] = ownerId()
            it[role] = HouseholdMemberRole.OWNER
        }

        val latest = latestDisplayNames()
        val ownerProfile =
            UsersTable
                .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
                .join(UserDisplayNamesTable, JoinType.INNER) {
                    (UserDisplayNamesTable.user_id eq latest.userId) and
                        (UserDisplayNamesTable.id eq latest.maxId)
                }.selectAll()
                .where { UsersTable.id eq ownerId() }
                .single()
                .toProfile()

        return hydrateHousehold(
            householdId = newHouseholdId,
            name = name,
            members = listOf(HouseholdMember(ownerProfile, HouseholdMemberRole.OWNER)),
        )
    }
```

- [ ] **Step 4: 既存の Household 統合テストを世帯名つきに追従させる**

`HouseholdDataSourceIntegrationTest.kt`(既存)の `householdRegister.create(owner.userId)` 呼び出しを `householdRegister.create(owner.userId, HouseholdName("テスト世帯"))` に全置換し、`import net.brightroom.mindstock.domain.model.household.HouseholdName` を追加する。

- [ ] **Step 5: コンパイル + 既存統合テストを確認**

Run: `./gradlew :backend:api:integrationTest --tests "*HouseholdDataSourceIntegrationTest*" --max-workers=1`
Expected: PASS(`findById`/`findOf` は A5 で name hydrate を入れるまで `name` が読めず落ちる場合がある → A5 と連続で実施し、A5 完了後に再実行して緑にする)。

- [ ] **Step 6: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/household/HouseholdRegisterRepository.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/household/HouseholdRegisterService.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdRegisterDataSource.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSourceIntegrationTest.kt
git commit -m "feat(infra): 世帯作成時に household_names へ INSERT し名前付き Household を返す

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task A5: findOf / findById で世帯名を hydrate

**Files:**
- Modify: `backend/core/.../infrastructure/datasource/household/HouseholdDataSource.kt`

`findOf` / `findById` は現在 `hydrateHousehold(householdId, members)` を呼んでいる(A2 で引数不足の赤)。最新世帯名を引いて渡す。

- [ ] **Step 1: 最新世帯名を引くヘルパーを使い、hydrate に name を渡す**

`HouseholdDataSource.kt` の `findOf` / `findById` 双方で、members を組み立てた後の `hydrateHousehold(...)` 呼び出し直前に最新世帯名を取得する。両メソッド共通の private 関数を足す:

```kotlin
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import org.jetbrains.exposed.v1.core.SortOrder
// ...

    private fun latestHouseholdNameOf(householdId: Uuid): HouseholdName =
        HouseholdNamesTable
            .selectAll()
            .where { HouseholdNamesTable.household_id eq householdId }
            .orderBy(HouseholdNamesTable.id, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let { HouseholdName(it[HouseholdNamesTable.name]) }
            ?: throw ResourceNotFoundException("household name not found: $householdId")
```

そして `findOf` / `findById` の `return hydrateHousehold(householdId = householdId, members = members)` を以下に変更:

```kotlin
        return hydrateHousehold(
            householdId = householdId,
            name = latestHouseholdNameOf(householdId),
            members = members,
        )
```

> 注: `findById` の「revoked-only(members 空)」テストケースでも世帯名は引ける(household_names は membership とは独立)。`findOf` 側の `householdId` 変数名は既存コードに合わせる(`rows.first()[HouseholdMembershipsTable.household_id]` 等)。

- [ ] **Step 2: Household 統合テストを緑にする**

Run: `./gradlew :backend:api:integrationTest --tests "*HouseholdDataSourceIntegrationTest*" --max-workers=1`
Expected: PASS（A4 の `create(..., HouseholdName(...))` と合わせ、`findOf`/`findById` が名前付き Household を返す）。

- [ ] **Step 3: backend 全体のコンパイルを確認(A2 の赤が解消)**

Run: `./gradlew :backend:core:compileKotlin :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL（`SessionResponseTest` 等で `Household(...)` を直書きしている箇所があれば、それも `name` 付きに直す）。

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSource.kt
git commit -m "feat(infra): findOf/findById で最新の世帯名を hydrate する

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task A6: migration 生成(household_names)

**Files:**
- Create: `backend/core/src/main/resources/db/migration/V<timestamp>__household_names.sql`(生成)

- [ ] **Step 1: Exposed migration plugin で差分 SQL を生成**

Run: `./gradlew :backend:core:generateMigrationScript`
（タスク名はプラグインに依存。`./gradlew :backend:core:tasks --group=migration` で正確名を確認し、`HouseholdNamesTable` を含む差分 SQL を `src/main/resources/db/migration/` に生成する。）

- [ ] **Step 2: 生成 SQL を確認**

生成された `V<timestamp>__*.sql` が以下に相当することを確認(既存 `user_display_names` と同形):

```sql
CREATE TABLE IF NOT EXISTS household_names (id BIGSERIAL PRIMARY KEY, household_id uuid NOT NULL, "name" VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, CONSTRAINT fk_household_names_household_id__id FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE RESTRICT ON UPDATE RESTRICT);
CREATE INDEX household_names_household_id_id ON household_names (household_id, id);
CREATE SEQUENCE IF NOT EXISTS household_names_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807;
```

> append-only role: `V00000000000000__append_only_role.sql` の `ALTER DEFAULT PRIVILEGES` により、本テーブルへ `mindstock_app` の SELECT/INSERT と sequence USAGE が自動付与される(追加の GRANT 文は不要)。生成 SQL に余計な `DROP`/`ALTER` が混ざっていないかだけ確認する。

- [ ] **Step 3: 適用が通ることを統合テストで確認(Flyway がクリーン DB に全 migration 適用)**

Run: `./gradlew :backend:api:integrationTest --tests "*HouseholdDataSourceIntegrationTest*" --max-workers=1`
Expected: PASS（Testcontainers の新規 schema に household_names が作られ、A4/A5 が通る）。

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/resources/db/migration/
git commit -m "feat(db): household_names テーブルの migration を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

# Phase B — オンボーディング + bootstrap

## Task B1: RegisterFirstHouseholdScenario

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenario.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenarioTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.application.scenario.onboarding

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdName
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

        test("未登録なら User を作り、デフォルト世帯名で Household を作って Profile を返す") {
            val userRepository = mockk<UserRepository>()
            val userRegisterService = mockk<UserRegisterService>()
            val householdRegisterService = mockk<HouseholdRegisterService>(relaxed = true)

            every { userRepository.findProfileByAuthIdentity(identity) } throws
                ResourceNotFoundException("user not found")
            every { userRegisterService.register(identity, displayName) } returns profile

            val scenario =
                RegisterFirstHouseholdScenario(userRepository, userRegisterService, householdRegisterService)

            scenario.run(identity, displayName) shouldBe profile

            val nameSlot = slot<HouseholdName>()
            verify(exactly = 1) { userRegisterService.register(identity, displayName) }
            verify(exactly = 1) { householdRegisterService.create(profile.userId, capture(nameSlot)) }
            nameSlot.captured() shouldBe "Aliceの家"
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
            verify(exactly = 0) { householdRegisterService.create(any(), any()) }
        }
    })
```

- [ ] **Step 2: テスト失敗を確認**

Run: `./gradlew :backend:core:test --tests "*RegisterFirstHouseholdScenarioTest*"`
Expected: コンパイルエラー(`RegisterFirstHouseholdScenario` 未定義)。

- [ ] **Step 3: 実装**

```kotlin
package net.brightroom.mindstock.application.scenario.onboarding

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

/**
 * 初回サインインのオンボーディング。User + Household(デフォルト名)+ OWNER membership を 1 ユースケースで揃える。
 *
 * 冪等: 既に当該 identity の User が存在する場合は何も作らず既存 Profile を返す。
 * トランザクション境界は呼び出し側(Controller の `tx()`)が張る。
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
        householdRegisterService.create(profile.userId, defaultHouseholdName(displayName))
        return profile
    }

    /** 表示名から導出するデフォルト世帯名。HouseholdName(100) を超えないよう表示名を丸める。 */
    private fun defaultHouseholdName(displayName: DisplayName): HouseholdName =
        HouseholdName("${displayName().take(97)}の家")
}
```

- [ ] **Step 4: テスト成功を確認**

Run: `./gradlew :backend:core:test --tests "*RegisterFirstHouseholdScenarioTest*"`
Expected: PASS(2 tests)。

- [ ] **Step 5: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenario.kt \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/onboarding/RegisterFirstHouseholdScenarioTest.kt
git commit -m "feat(onboarding): RegisterFirstHouseholdScenario(デフォルト世帯名・冪等)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task B2: UserPublicRpcService → OnboardingRpcService 改名 + register を Scenario 経由

**Files:**
- Rename: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/UserPublicRpcService.kt` → `OnboardingRpcService.kt`
- Rename: `backend/api/.../presentation/rpc/user/UserPublicController.kt` → `presentation/rpc/onboarding/OnboardingController.kt`
- Rename: `backend/api/.../presentation/rpc/user/UserPublicControllerFactory.kt` → `presentation/rpc/onboarding/OnboardingControllerFactory.kt`
- Modify: `backend/api/.../configuration/di/DependenciesConfiguration.kt`
- Modify: `backend/api/.../configuration/routing/RoutingConfiguration.kt`
- Move/Modify test: `backend/api/.../presentation/rpc/user/UserPublicControllerTest.kt` → `presentation/rpc/onboarding/OnboardingControllerTest.kt`

> bootstrap メソッドは Task B4 で足す。本タスクは「改名 + register の Scenario 化」まで。

- [ ] **Step 1: RPC interface を改名**

`rpc/.../OnboardingRpcService.kt`(旧 `UserPublicRpcService.kt` を改名):

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

/**
 * 未登録(JWT 有効・User 未登録)でも通る初期化 service。
 * 認証は WS subprotocol 一本。AuthIdentity は session(Principal)から取得する(なりすまし防止)。
 */
@Rpc
interface OnboardingRpcService {
    suspend fun register(displayName: DisplayName): RpcResult<Profile, RpcError>
}
```

- [ ] **Step 2: Controller を改名 + Scenario 経由に**

`backend/api/.../presentation/rpc/onboarding/OnboardingController.kt`:

```kotlin
package net.brightroom.mindstock.presentation.rpc.onboarding

import net.brightroom.mindstock.application.scenario.onboarding.RegisterFirstHouseholdScenario
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.OnboardingRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class OnboardingController(
    private val registerFirstHouseholdScenario: RegisterFirstHouseholdScenario,
    private val session: MindstockSession,
    private val database: Database,
) : OnboardingRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<Profile, RpcError> =
        tx(database, session) {
            RpcResult.Ok(registerFirstHouseholdScenario.run(session.identity, displayName))
        }
}
```

`OnboardingControllerFactory.kt`:

```kotlin
package net.brightroom.mindstock.presentation.rpc.onboarding

import net.brightroom.mindstock.configuration.auth.MindstockSession

fun interface OnboardingControllerFactory {
    fun create(session: MindstockSession): OnboardingController
}
```

旧 `presentation/rpc/user/UserPublicController.kt` と `UserPublicControllerFactory.kt` は削除する。

- [ ] **Step 3: DI を更新**

`DependenciesConfiguration.kt`:
- import を更新: `UserPublicController`/`UserPublicControllerFactory` → `onboarding.OnboardingController`/`onboarding.OnboardingControllerFactory`、`RegisterFirstHouseholdScenario` を追加。
- Scenario を provide(`// Controller Factory (30)` の直前):

```kotlin
        // Scenario (25)
        provide<RegisterFirstHouseholdScenario> {
            RegisterFirstHouseholdScenario(resolve(), resolve(), resolve())
        }
```

- 旧 `provide<UserPublicControllerFactory> { ... }` を置換:

```kotlin
        provide<OnboardingControllerFactory> {
            val scenario = resolve<RegisterFirstHouseholdScenario>()
            val db = resolve<Database>()
            OnboardingControllerFactory { session -> OnboardingController(scenario, session, db) }
        }
```

- [ ] **Step 4: routing を更新**

`RoutingConfiguration.kt`:
- import: `UserPublicControllerFactory` → `onboarding.OnboardingControllerFactory`、`UserPublicRpcService` → `OnboardingRpcService`。
- `val userPublicFactory: UserPublicControllerFactory by dependencies` → `val onboardingFactory: OnboardingControllerFactory by dependencies`。
- `route("/user/public") { rpc { ... registerService<UserPublicRpcService> { userPublicFactory.create(session) } } }` を以下に置換(route パスは据え置きで frontend 改修を最小化):

```kotlin
            // 未登録でも通る初期化 route(register + bootstrap)
            route("/user/public") {
                rpc {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<OnboardingRpcService> { onboardingFactory.create(session) }
                }
            }
```

> route パス `/user/public` は据え置き(改名は §6 未決。frontend の `open("user/public")` を変えずに済む)。

- [ ] **Step 5: テストを移設 + Scenario mock に**

`presentation/rpc/onboarding/OnboardingControllerTest.kt`(旧 `UserPublicControllerTest.kt` を移動して書き換え):

```kotlin
package net.brightroom.mindstock.presentation.rpc.onboarding

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
class OnboardingControllerTest :
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

            val impl = OnboardingController(scenario, session, database)
            impl.register(displayName) shouldBe RpcResult.Ok(expected)
        }
    })
```

旧 `UserPublicControllerTest.kt` は削除する。

- [ ] **Step 6: ビルド + テスト確認**

Run: `./gradlew :rpc:compileKotlinJvm :backend:api:test --tests "*OnboardingControllerTest*"`
Expected: PASS(1 test)。`UserPublic*` を参照する箇所が他に無いことも確認(`grep -rn UserPublic backend rpc` が 0 件)。

- [ ] **Step 7: コミット**

```bash
git add -A
git commit -m "refactor(rpc): UserPublicRpcService を OnboardingRpcService に改名し register を Scenario 経由に

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task B3: SessionBootstrap 型 + bootstrap ロジック(純粋関数)

bootstrap の「session → SessionBootstrap」変換を route/Controller から切り離し、単体テスト可能にする。

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/SessionBootstrap.kt`
- Create: `backend/api/.../presentation/rpc/onboarding/ResolveSessionBootstrap.kt`
- Test: `backend/api/.../presentation/rpc/onboarding/ResolveSessionBootstrapTest.kt`

- [ ] **Step 1: SessionBootstrap 型(`:rpc`)**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.profile.DisplayName

/**
 * 起動時セッション初期化情報。`bootstrap()` の戻り値。
 *
 * KrpcJson(POLYMORPHIC discriminator)で wire を通るため sealed で表現でき、nullable を持たない。
 */
@Serializable
sealed interface SessionBootstrap {
    @Serializable
    data object Unregistered : SessionBootstrap

    @Serializable
    data class Registered(
        val displayName: DisplayName,
        val householdId: HouseholdId,
        val householdName: HouseholdName,
    ) : SessionBootstrap
}
```

- [ ] **Step 2: 失敗するテストを書く**

`ResolveSessionBootstrapTest.kt`:

```kotlin
package net.brightroom.mindstock.presentation.rpc.onboarding

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
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.SessionBootstrap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ResolveSessionBootstrapTest :
    FunSpec({

        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1"))

        fun session(userId: UserId?) =
            MindstockSession(
                identity = identity,
                userId = userId,
                exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                callId = Uuid.random(),
            )

        test("userId が null(未登録)なら Unregistered を返し Service を呼ばない") {
            val userService = mockk<UserService>()
            val householdService = mockk<HouseholdService>()

            resolveSessionBootstrap(session(userId = null), userService, householdService) shouldBe
                SessionBootstrap.Unregistered
        }

        test("登録済みなら Registered(displayName / householdId / householdName)を返す") {
            val userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001"))
            val householdId = HouseholdId(Uuid.parse("00000000-0000-0000-0000-0000000000aa"))
            val userService = mockk<UserService>()
            val householdService = mockk<HouseholdService>()

            every { userService.findById(userId) } returns Profile(userId, DisplayName("Alice"))
            every { householdService.findOf(userId) } returns
                Household(householdId, HouseholdName("Aliceの家"), HouseholdMembers(emptyList()))

            resolveSessionBootstrap(session(userId = userId), userService, householdService) shouldBe
                SessionBootstrap.Registered(
                    displayName = DisplayName("Alice"),
                    householdId = householdId,
                    householdName = HouseholdName("Aliceの家"),
                )
        }
    })
```

- [ ] **Step 3: テスト失敗を確認**

Run: `./gradlew :backend:api:test --tests "*ResolveSessionBootstrapTest*"`
Expected: コンパイルエラー(`resolveSessionBootstrap` / `SessionBootstrap` 未解決)。

- [ ] **Step 4: 実装**

`ResolveSessionBootstrap.kt`:

```kotlin
package net.brightroom.mindstock.presentation.rpc.onboarding

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.rpc.SessionBootstrap

/**
 * session から起動時初期化情報を組み立てる。
 *
 * - userId が null(JWT 有効・User 未登録)→ Unregistered
 * - userId 非 null(登録済み)→ Registered(displayName / householdId / householdName)
 *   登録済みなら世帯は必ず存在する(オンボーディングが原子的に作る)。
 *
 * DB アクセスを含むため、呼び出し側が transaction 境界を張る。
 */
fun resolveSessionBootstrap(
    session: MindstockSession,
    userService: UserService,
    householdService: HouseholdService,
): SessionBootstrap {
    val userId = session.userId ?: return SessionBootstrap.Unregistered
    val profile = userService.findById(userId)
    val household = householdService.findOf(userId)
    return SessionBootstrap.Registered(
        displayName = profile.displayName,
        householdId = household.id,
        householdName = household.name,
    )
}
```

- [ ] **Step 5: テスト成功を確認**

Run: `./gradlew :backend:api:test --tests "*ResolveSessionBootstrapTest*"`
Expected: PASS(2 tests)。

- [ ] **Step 6: コミット**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/SessionBootstrap.kt \
        backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/onboarding/ResolveSessionBootstrap.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/onboarding/ResolveSessionBootstrapTest.kt
git commit -m "feat(onboarding): SessionBootstrap 型と resolveSessionBootstrap ロジックを追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task B4: bootstrap() を OnboardingRpcService に追加 + 配線

**Files:**
- Modify: `rpc/.../OnboardingRpcService.kt`
- Modify: `backend/api/.../presentation/rpc/onboarding/OnboardingController.kt`(+ Factory に Service 注入)
- Modify: `backend/api/.../configuration/di/DependenciesConfiguration.kt`
- Test: `backend/api/.../presentation/rpc/onboarding/OnboardingControllerTest.kt`

- [ ] **Step 1: interface に bootstrap を追加**

`OnboardingRpcService.kt` に追記:

```kotlin
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
// SessionBootstrap は同 :rpc パッケージ
// ...
    suspend fun bootstrap(): RpcResult<SessionBootstrap, RpcError>
```

- [ ] **Step 2: Controller に bootstrap を実装(UserService/HouseholdService を注入)**

`OnboardingController.kt` の constructor に `userService` / `householdService` を追加し、bootstrap を実装:

```kotlin
class OnboardingController(
    private val registerFirstHouseholdScenario: RegisterFirstHouseholdScenario,
    private val userService: net.brightroom.mindstock.application.service.user.UserService,
    private val householdService: net.brightroom.mindstock.application.service.household.HouseholdService,
    private val session: MindstockSession,
    private val database: Database,
) : OnboardingRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<Profile, RpcError> =
        tx(database, session) {
            RpcResult.Ok(registerFirstHouseholdScenario.run(session.identity, displayName))
        }

    override suspend fun bootstrap(): RpcResult<net.brightroom.mindstock.rpc.SessionBootstrap, RpcError> =
        tx(database, session) {
            RpcResult.Ok(resolveSessionBootstrap(session, userService, householdService))
        }
}
```

- [ ] **Step 3: DI を更新(Factory に Service 注入)**

`DependenciesConfiguration.kt` の `provide<OnboardingControllerFactory>` を置換:

```kotlin
        provide<OnboardingControllerFactory> {
            val scenario = resolve<RegisterFirstHouseholdScenario>()
            val us = resolve<UserService>()
            val hs = resolve<HouseholdService>()
            val db = resolve<Database>()
            OnboardingControllerFactory { session -> OnboardingController(scenario, us, hs, session, db) }
        }
```

(`UserService` / `HouseholdService` は既に provide 済み。import 追加が必要なら足す。)

- [ ] **Step 4: Controller テストに bootstrap ケースを追加**

`OnboardingControllerTest.kt` に test を追加(constructor 変更に伴い既存 test の `OnboardingController(...)` 生成も引数を合わせる):

```kotlin
        test("bootstrap は未登録なら Unregistered を返す") {
            val scenario = mockk<RegisterFirstHouseholdScenario>()
            val userService = mockk<net.brightroom.mindstock.application.service.user.UserService>()
            val householdService = mockk<net.brightroom.mindstock.application.service.household.HouseholdService>()
            val database = mockk<Database>()
            val session =
                MindstockSession(
                    identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
                    userId = null,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<net.brightroom.mindstock.rpc.SessionBootstrap>(any(), any(), any())
            } coAnswers {
                val block = arg<suspend () -> RpcResult<net.brightroom.mindstock.rpc.SessionBootstrap, RpcError>>(2)
                block()
            }

            val impl = OnboardingController(scenario, userService, householdService, session, database)
            impl.bootstrap() shouldBe RpcResult.Ok(net.brightroom.mindstock.rpc.SessionBootstrap.Unregistered)
        }
```

既存の register テストの `OnboardingController(scenario, session, database)` を `OnboardingController(scenario, mockk(), mockk(), session, database)` に直す。

- [ ] **Step 5: ビルド + テスト確認**

Run: `./gradlew :rpc:compileKotlinJvm :backend:api:test --tests "*OnboardingControllerTest*"`
Expected: PASS(2 tests)。

- [ ] **Step 6: 手動検証(任意 / ローカル)**

bootstrap は WS RPC なので、frontend(Plan 1b)接続時に検証するのが本筋。backend 単体では in-memory kRPC transport テスト or 起動 + frontend で確認。本タスクでは単体テストでロジックを担保済みとし、E2E は Plan 1b に委ねる。

- [ ] **Step 7: コミット**

```bash
git add -A
git commit -m "feat(onboarding): bootstrap() RPC を追加し UserService/HouseholdService を配線

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 完了条件

- [ ] `./gradlew :domain:allTests :backend:core:test :backend:api:test` がグリーン(integration 除外のデフォルト)
- [ ] `./gradlew :backend:api:integrationTest --tests "*HouseholdDataSourceIntegrationTest*" --max-workers=1` がグリーン(household_names migration 適用 + 世帯名 hydrate)
- [ ] `grep -rn "UserPublic" backend rpc`(build ディレクトリ除く)が 0 件
- [ ] `RegisterFirstHouseholdScenario` の冪等性テスト + `resolveSessionBootstrap` の 2 状態テストがグリーン

## このプランで扱わないこと(後続)

- frontend(`App.kt` の `register` 呼び出しと、ping → `bootstrap()` 置き換え、AuthViewModel / nav-compose / AppSession)は **Plan 1b**。route パス `/api/v1/user/public` は据え置いたので frontend の `open("user/public")` 変更は不要(改名するなら 1b で同時に)。
- `OnboardingRpcService` の route パス改名(§6 未決)、`HouseholdName` 最大長の最終確定(本プランは 100)。
- `CatalogItem.unit` → `Product` 移動は独立 Plan。
