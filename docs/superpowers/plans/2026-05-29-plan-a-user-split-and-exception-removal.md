# Plan A: User クラス分離 + DomainException 廃止 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `User` 集約を `UserId` / `Profile` / `AuthIdentity` の 3 概念に分解し、composition は `Profile` 経由に統一する。同時に `DomainException` 階層を廃止し、原則 `IllegalArgumentException` に置き換える。

**Architecture:** `User` クラスは廃止し、`UserId` を identity の主役にする。`Profile(userId, displayName)` を `user/profile/` に新設し、`HouseholdMember.profile` / `StockMovement.actor: Profile` で composition する。`AuthIdentity` は `user/auth/` 直下に据え置き、`UserRepository` 経由でのみ `Profile` と紐付ける。`DomainException` は全 11 ケースが VO の値域違反なので、`init` 内の `require(...)` で `IllegalArgumentException` に置換し、ファイル自体を削除する。

**Tech Stack:** Kotlin Multiplatform / Exposed v1 / Ktor / kotlinx-rpc 0.10.2 / kotlinx-serialization / Kotest

**Spec:** `docs/superpowers/specs/2026-05-29-domain-cohesion-coupling-design.md`（所見 2.4 + 2.5 + 3.3 + 3.4）

**重要な前提**: この Plan は内部 refactor である。kotlinx-rpc の wire 形式互換性を維持する必要はない（spec 3.1 で domain = wire-format は現状維持、ただしクライアントを全て同時 deploy 可能と前提）。

---

## File Plan

### 新規作成

| パス | 責務 |
|---|---|
| `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/profile/Profile.kt` | `Profile(userId, displayName)` データクラス。`@Serializable` |

### 移動（中身は同一）

| 旧パス | 新パス |
|---|---|
| `domain/.../model/user/DisplayName.kt` | `domain/.../model/user/profile/DisplayName.kt` |

### 削除

| パス | 理由 |
|---|---|
| `domain/.../model/user/User.kt` | `UserId` で識別、`Profile` で表示文脈、`AuthIdentity` で認証文脈に分解 |
| `domain/.../exception/DomainException.kt` | 全 11 ケースが VO 値域違反 → IAE で十分 |
| `domain/src/commonTest/.../exception/DomainExceptionTest.kt` | 対象が消えるため |

### 修正（domain）

| パス | 変更内容 |
|---|---|
| `model/household/HouseholdMember.kt` | `user: User` → `profile: Profile` |
| `model/household/HouseholdMembers.kt` | API シグネチャを `User` → `Profile` に置換。`contains(userId: UserId)` を導入 |
| `model/stock/movement/StockMovement.kt` | `actor: User` → `actor: Profile` |
| `model/stock/movement/Replenishment.kt` | `actor: User` → `actor: Profile` |
| `model/stock/movement/Consumption.kt` | `actor: User` → `actor: Profile` |
| `model/stock/Quantity.kt` | `throw DomainException.InvalidQuantity(value)` → `require(...)` |
| `model/product/MinimumStock.kt` | 同上 |
| `model/stock/OccurredAt.kt` | 同上 |
| `model/user/DisplayName.kt`（移動先 `model/user/profile/DisplayName.kt`） | 同上 |
| `model/catalog/CatalogItemName.kt` | 同上 |
| `model/catalog/CatalogItemUnit.kt` | 同上 |
| `model/user/auth/AuthSubject.kt` | 同上 |

### 修正（backend/core - repository interface）

| パス | 変更内容 |
|---|---|
| `application/repository/user/UserRepository.kt` | `findByAuthIdentity(identity): User?` → `findProfileByAuthIdentity(identity): Profile?`、`findById(id): User?` → `findProfileById(id): Profile?` |
| `application/repository/user/UserRegisterRepository.kt` | `register(identity, displayName): User` → `: Profile`、`rename(user: User, ...)` → `rename(userId: UserId, ...)` |
| `application/repository/household/HouseholdRepository.kt` | `findOf(user: User)` → `findOf(userId: UserId)` |
| `application/repository/household/HouseholdRegisterRepository.kt` | 全 `actor: User` → `actor: UserId` |
| `application/repository/catalog/CatalogItemRegisterRepository.kt` | 同上 |
| `application/repository/product/ProductRegisterRepository.kt` | 同上 |
| `application/repository/stock/StockRegisterRepository.kt` | 同上 |

### 修正（backend/core - service）

| パス | 変更内容 |
|---|---|
| `application/service/user/UserRegisterService.kt` | `register(...): Profile`、`rename(userId: UserId, ...)` |
| `application/service/household/HouseholdService.kt` | `findOf(userId: UserId)` |
| `application/service/household/HouseholdRegisterService.kt` | 全 `actor: User` → `actor: UserId` |
| `application/service/catalog/CatalogItemRegisterService.kt` | 同上 |
| `application/service/product/ProductRegisterService.kt` | 同上 |
| `application/service/stock/StockRegisterService.kt` | 同上 |

### 修正（backend/core - infrastructure）

| パス | 変更内容 |
|---|---|
| `infrastructure/datasource/user/UserHydration.kt` | `ResultRow.toUser(): User` → `ResultRow.toProfile(): Profile`。`AuthIdentity` 構築コードは削除 |
| `infrastructure/datasource/user/UserDataSource.kt` | 戻り値 `Profile?`、`findByAuthIdentity` → `findProfileByAuthIdentity` 名変更含む |
| `infrastructure/datasource/user/UserRegisterDataSource.kt` | `register` 戻り値 `Profile`、`rename` 引数 `UserId` |
| `infrastructure/datasource/household/HouseholdDataSource.kt` | `findOf(user: User)` → `findOf(userId: UserId)`、`row.toUser()` → `row.toProfile()`、`HouseholdMember(user = ..., role = ...)` → `HouseholdMember(profile = ..., role = ...)` |
| `infrastructure/datasource/household/HouseholdRegisterDataSource.kt` | actor: User → UserId、`user.id()` → `userId()` |
| `infrastructure/datasource/catalog/CatalogItemRegisterDataSource.kt` | 同上 |
| `infrastructure/datasource/product/ProductRegisterDataSource.kt` | 同上 |
| `infrastructure/datasource/stock/StockRegisterDataSource.kt` | 同上 |
| `infrastructure/datasource/stock/StockHydration.kt` | **`User("(unknown)")` スタブを完全除去**。`UserDisplayNamesTable` を JOIN して `Profile` を hydrate（後述「StockHydration の JOIN 拡張」） |
| `infrastructure/datasource/stock/StockDataSource.kt` | `StockHydration` の新シグネチャに追従、JOIN 句を変更 |

### 修正（backend/api）

| パス | 変更内容 |
|---|---|
| `configuration/auth/ActorResolver.kt` | 戻り値 `Profile`、`findByAuthIdentity` → `findProfileByAuthIdentity` |
| `presentation/rpc/user/UserController.kt` | `lazy actor: User` → `Profile`、`actor` 渡し先のサービスシグネチャに合わせる |
| `presentation/rpc/user/UserPublicController.kt` | `register` 戻り値 `Profile` |
| `presentation/rpc/household/HouseholdController.kt` | `actor` 型変更、サービス呼び出し更新 |
| `presentation/rpc/catalog/CatalogController.kt` | 同上 |
| `presentation/rpc/product/ProductController.kt` | 同上 |
| `presentation/rpc/stock/StockController.kt` | 同上 |
| `test/.../e2e/E2eTestSupport.kt` | `seedUser` 等のヘルパが返す型を `Profile` に |
| `test/.../e2e/Fixtures.kt` | User 構築 → Profile 構築 |
| `test/.../e2e/**/*RpcServiceE2eTest.kt`（複数） | `User(...)` 構築 → `Profile(...)` 構築 |
| `test/.../infrastructure/**/*DataSourceIntegrationTest.kt`（複数） | 同上 |

### 修正（rpc）

| パス | 変更内容 |
|---|---|
| `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/UserPublicRpcService.kt` | `register(displayName): User` → `register(displayName): Profile` |

### 修正（frontend）

| パス | 変更内容 |
|---|---|
| `frontend/src/commonMain/.../App.kt`（または該当ファイル） | `User` 参照を `Profile` に置換。`displayName` 取り出し方は同じ |

### 修正（domain test）

| パス | 変更内容 |
|---|---|
| `commonTest/.../model/SerializationRoundTripTest.kt` | `User` シリアライズラウンドトリップを `Profile` に |
| `commonTest/.../model/household/HouseholdMembersTest.kt` | helper を `Profile` 構築に |
| `commonTest/.../model/stock/StockTest.kt` | 同上 |
| `commonTest/.../model/stock/movement/StockMovementsTest.kt` | 同上 |
| `commonTest/.../model/shopping/ShoppingListTest.kt` | 同上 |
| `commonTest/.../model/stock/QuantityTest.kt` | `shouldThrow<DomainException.InvalidQuantity>` → `shouldThrow<IllegalArgumentException>` |
| `commonTest/.../model/stock/NoteTest.kt` | 該当しない（init なし）。確認のみ |
| `commonTest/.../model/stock/OccurredAtTest.kt` | 同上（IAE 期待に変更） |
| `commonTest/.../model/product/MinimumStockTest.kt` | 同上 |
| `commonTest/.../model/catalog/CatalogItemNameTest.kt` | 同上 |
| `commonTest/.../model/catalog/CatalogItemUnitTest.kt` | 同上 |
| `commonTest/.../model/user/DisplayNameTest.kt`（移動: `user/profile/DisplayNameTest.kt`） | 同上 |
| `commonTest/.../model/user/auth/AuthSubjectTest.kt` | 同上 |

---

## StockHydration の JOIN 拡張（重要）

現状 `StockHydration.kt` は `actor` を `User("(unknown)")` スタブで埋めている（コメント「Plan 6 までに JOIN 拡張する想定」）。この Plan で**完全に解消する**。

新シグネチャ:

```kotlin
internal fun toStockMovement(
    product: Product,
    actor: Profile,           // ← UUID ではなく Profile を直接受け取る
    type: StockMovementType,
    quantity: Int,
    occurredAt: Instant,
    note: String,
): StockMovement
```

呼び出し側 `StockDataSource` は `StockMovementsTable` の SELECT 時に:

```kotlin
.join(UsersTable, JoinType.INNER, onColumn = StockMovementsTable.acted_by, otherColumn = UsersTable.id)
.join(latestNames, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latestNameUserId)
.join(UserDisplayNamesTable, JoinType.INNER) {
    (UserDisplayNamesTable.user_id eq latestNameUserId) and
        (UserDisplayNamesTable.id eq latestNameMaxId)
}
```

を追加し、各 row から `row.toProfile()` を呼んで `actor` に渡す。`UserHydration.toProfile()` を共用する形にする。

---

## 新規型の正準シグネチャ

すべての Phase で参照される seam。

```kotlin
// domain/.../model/user/profile/Profile.kt
package net.brightroom.mindstock.domain.model.user.profile

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.user.UserId

@Serializable
data class Profile(
    val userId: UserId,
    val displayName: DisplayName,
)
```

```kotlin
// domain/.../model/household/HouseholdMember.kt（変更後）
@Serializable
data class HouseholdMember(
    val profile: Profile,
    val role: HouseholdMemberRole,
)
```

```kotlin
// domain/.../model/household/HouseholdMembers.kt（変更後）
@Serializable
data class HouseholdMembers(
    val list: List<HouseholdMember>,
) {
    fun owner(): Profile? = list.firstOrNull { it.role == HouseholdMemberRole.OWNER }?.profile
    fun activeMembers(): List<Profile> = list.map { it.profile }
    fun contains(userId: UserId): Boolean = list.any { it.profile.userId == userId }
}
// asList() / size は所見 4.1 で別 Plan(C)。本 Plan では現状の `val list` をそのまま残す
```

```kotlin
// domain/.../model/stock/movement/StockMovement.kt（変更後）
@Serializable
sealed interface StockMovement {
    val product: Product
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Profile          // ← User → Profile
    val note: Note
    val type: StockMovementType
}
```

```kotlin
// backend/.../application/repository/user/UserRepository.kt（変更後）
interface UserRepository {
    fun findProfileByAuthIdentity(identity: AuthIdentity): Profile?
    fun findProfileById(id: UserId): Profile?
}
```

```kotlin
// backend/.../application/repository/user/UserRegisterRepository.kt（変更後）
interface UserRegisterRepository {
    /** users + 初回 user_display_names を 1 トランザクションで INSERT。 */
    fun register(identity: AuthIdentity, defaultDisplayName: DisplayName): Profile

    /** user_display_names に新規行を INSERT。 */
    fun rename(userId: UserId, newName: DisplayName)
}
```

```kotlin
// rpc/.../UserPublicRpcService.kt（変更後）
@Rpc
interface UserPublicRpcService {
    suspend fun register(displayName: DisplayName): Profile
}
```

---

## Phase 構成

Plan A はフェーズ単位で組む。**Phase 2 は内部で 1 PR 想定の巨大変更**（domain ↔ backend ↔ rpc ↔ frontend が全層同時に切り替わるため、フェーズ途中ではコンパイル不能）。

| Phase | 内容 | コミット |
|---|---|---|
| Phase 1 | 準備: `Profile` 追加、`DisplayName` を `profile/` に移動。`User` クラスはまだ残す（共存期） | 1 |
| Phase 2 | 切り替え: 全層を `User` → `Profile`（または `UserId`）に置換。`User` クラス削除。`StockHydration` の JOIN 拡張も同時実施 | 1（または論理ブロック単位で複数） |
| Phase 3 | `DomainException` 廃止: 各 VO の `throw` を `require` に置換、テスト更新、`DomainException.kt` 削除 | 7（VO ごと） |
| Phase 4 | 最終確認: 全モジュールビルド、全テスト pass | 0（追加コミットなし。Phase 3 の最終で確認） |

---

## Phase 1: Profile 新設と DisplayName 移動

**目的:** 新しい型を導入し、後続フェーズで段階的に切り替えるための準備をする。この Phase の終了時点で **ビルドは通る**（User クラスは残っているので既存コードは無修正）。

### Files

- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/profile/Profile.kt`
- Move: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/DisplayName.kt` → `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/profile/DisplayName.kt`
- Move: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/DisplayNameTest.kt` → `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/profile/DisplayNameTest.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/profile/ProfileTest.kt`

### Steps

- [ ] **Step 1.1: `DisplayName.kt` を `profile/` に移動し、package 宣言を更新**

新 package: `net.brightroom.mindstock.domain.model.user.profile`

```kotlin
// domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/profile/DisplayName.kt
package net.brightroom.mindstock.domain.model.user.profile

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.jvm.JvmInline

/**
 * ユーザーの表示名。空文字禁止、最大 100 文字。
 */
@Serializable
@JvmInline
value class DisplayName(
    private val value: String,
) {
    init {
        if (value.isBlank()) throw DomainException.DisplayNameBlank()
        if (value.length > 100) throw DomainException.DisplayNameTooLong(value.length)
    }

    override fun toString(): String = value

    operator fun invoke(): String = value
}
```

（`require` 化は Phase 3 で実施。この Phase では package 移動のみ）

- [ ] **Step 1.2: `DisplayNameTest.kt` を `profile/` に移動し、package 宣言と import を更新**

新 package: `net.brightroom.mindstock.domain.model.user.profile`
import: `import net.brightroom.mindstock.domain.model.user.profile.DisplayName`

中身の test ロジックは変更しない。

- [ ] **Step 1.3: 既存 `DisplayName` 利用箇所の import を一括更新**

旧: `import net.brightroom.mindstock.domain.model.user.DisplayName`
新: `import net.brightroom.mindstock.domain.model.user.profile.DisplayName`

対象ファイル（grep 結果に基づく）:
- `domain/.../model/user/User.kt`
- `domain/src/commonTest/.../model/SerializationRoundTripTest.kt`
- `backend/core/.../application/repository/user/UserRegisterRepository.kt`
- `backend/core/.../application/service/user/UserRegisterService.kt`
- `backend/core/.../infrastructure/datasource/user/UserHydration.kt`
- `backend/core/.../infrastructure/datasource/user/UserRegisterDataSource.kt`
- `backend/core/.../infrastructure/datasource/stock/StockHydration.kt`
- `backend/api/.../presentation/rpc/user/UserController.kt`
- `backend/api/.../presentation/rpc/user/UserPublicController.kt`
- `rpc/src/commonMain/.../UserPublicRpcService.kt`
- `frontend/.../App.kt` 等
- backend/api 統合テスト・E2E テスト 多数

確認コマンド: `grep -rl "net.brightroom.mindstock.domain.model.user.DisplayName" --include="*.kt"`

- [ ] **Step 1.4: `Profile.kt` を新規作成**

```kotlin
// domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/profile/Profile.kt
package net.brightroom.mindstock.domain.model.user.profile

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.user.UserId

/**
 * ユーザーの表示文脈エンティティ。`UserId` と紐付く。
 *
 * 認証文脈は別エンティティ（[net.brightroom.mindstock.domain.model.user.auth.AuthIdentity]）として
 * 分離されており、本クラスからは到達できない。
 */
@Serializable
data class Profile(
    val userId: UserId,
    val displayName: DisplayName,
)
```

- [ ] **Step 1.5: `ProfileTest.kt` を新規作成**

```kotlin
// domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/profile/ProfileTest.kt
package net.brightroom.mindstock.domain.model.user.profile

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.user.UserId
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ProfileTest {
    @Test
    fun `equals works on userId and displayName`() {
        val id = UserId.create()
        val a = Profile(id, DisplayName("Alice"))
        val b = Profile(id, DisplayName("Alice"))
        (a == b) shouldBe true
    }

    @Test
    fun `different displayName yields different Profile`() {
        val id = UserId.create()
        val a = Profile(id, DisplayName("Alice"))
        val b = Profile(id, DisplayName("Bob"))
        (a == b) shouldBe false
    }
}
```

- [ ] **Step 1.6: domain モジュールビルド**

```bash
./gradlew :domain:build
```

期待: BUILD SUCCESSFUL

- [ ] **Step 1.7: 全モジュールビルド**

```bash
./gradlew build
```

期待: BUILD SUCCESSFUL。User クラスは温存されているので既存コードは動く。

- [ ] **Step 1.8: コミット**

```bash
git add domain backend rpc frontend
git commit -m "$(cat <<'EOF'
refactor(domain): add Profile type and move DisplayName to user/profile

User クラスを Profile/UserId/AuthIdentity の 3 概念に分解するための準備
コミット。後続コミットで composition の置換と User クラス削除を行う。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2: 全層 User → Profile / UserId 切り替え

**目的:** domain / backend / rpc / frontend の全層を一気に切り替え、`User` クラスを削除する。Phase 内で部分コミットを挟むとビルドが壊れるため、**1 つの巨大コミット**として扱う（または論理サブセット単位で 3〜4 コミットに分けるのは可）。

**実施順序のガイド**: コンパイル単位ごとに「上から下」に進めると依存先が先に揃って楽:

1. domain layer
2. backend/core layer（repository → service → infrastructure）
3. backend/api layer（actor resolver → controllers → tests）
4. rpc layer
5. frontend layer
6. domain test
7. integration / e2e test

### Files

「File Plan」セクションの該当全ファイル。

### Steps

- [ ] **Step 2.1: domain `HouseholdMember.kt` を Profile composition に変更**

```kotlin
// domain/.../model/household/HouseholdMember.kt
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
data class HouseholdMember(
    val profile: Profile,
    val role: HouseholdMemberRole,
)
```

- [ ] **Step 2.2: domain `HouseholdMembers.kt` の API を Profile / UserId に変更**

```kotlin
// domain/.../model/household/HouseholdMembers.kt
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
data class HouseholdMembers(
    val list: List<HouseholdMember>,
) {
    fun owner(): Profile? = list.firstOrNull { it.role == HouseholdMemberRole.OWNER }?.profile
    fun activeMembers(): List<Profile> = list.map { it.profile }
    fun contains(userId: UserId): Boolean = list.any { it.profile.userId == userId }

    fun asList(): List<HouseholdMember> = list.toList()
}
```

注: `asList()` / `val list` 公開統一は Plan C で扱う。この Plan では既存形を維持。

- [ ] **Step 2.3: domain `StockMovement` 階層を Profile actor に変更**

```kotlin
// domain/.../model/stock/movement/StockMovement.kt
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
sealed interface StockMovement {
    val product: Product
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Profile
    val note: Note
    val type: StockMovementType
}
```

`Replenishment.kt` と `Consumption.kt` も `actor: User` → `actor: Profile` に書き換え、import を `Profile` に変更。

- [ ] **Step 2.4: domain `User.kt` を削除**

```bash
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/User.kt
```

- [ ] **Step 2.5: backend/core repository interface を更新**

例: `UserRepository.kt`

```kotlin
package net.brightroom.mindstock.application.repository.user

import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.Profile

interface UserRepository {
    fun findProfileByAuthIdentity(identity: AuthIdentity): Profile?
    fun findProfileById(id: UserId): Profile?
}
```

例: `UserRegisterRepository.kt`

```kotlin
package net.brightroom.mindstock.application.repository.user

import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

interface UserRegisterRepository {
    fun register(identity: AuthIdentity, defaultDisplayName: DisplayName): Profile
    fun rename(userId: UserId, newName: DisplayName)
}
```

その他の `*RegisterRepository` は、現状 `actor: User` を引数に取っている箇所を全て `actor: UserId` に置換。例（パターン）:

```kotlin
// 旧
fun create(catalogItemId: CatalogItemId, actor: User): Product
// 新
fun create(catalogItemId: CatalogItemId, actor: UserId): Product
```

「File Plan」の repository 一覧 7 ファイルすべて同様に対応する。

- [ ] **Step 2.6: backend/core service を repository に合わせて更新**

例: `UserRegisterService.kt`

```kotlin
package net.brightroom.mindstock.application.service.user

import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

class UserRegisterService(
    private val userRegisterRepository: UserRegisterRepository,
) {
    fun register(identity: AuthIdentity, defaultDisplayName: DisplayName): Profile =
        userRegisterRepository.register(identity, defaultDisplayName)

    fun rename(userId: UserId, newName: DisplayName) {
        userRegisterRepository.rename(userId, newName)
    }
}
```

他の service も同じパターンで `actor: User` → `actor: UserId` に置換。

- [ ] **Step 2.7: `UserHydration.kt` を `toProfile()` に書き換え**

```kotlin
// backend/core/.../infrastructure/datasource/user/UserHydration.kt
package net.brightroom.mindstock.infrastructure.datasource.user

import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
internal fun ResultRow.toProfile(): Profile =
    Profile(
        userId = UserId(this[UsersTable.id]),
        displayName = DisplayName(this[UserDisplayNamesTable.display_name]),
    )
```

注: `AuthIdentity` を構築するロジックは削除。`UserDataSource.findProfileByAuthIdentity` で `WHERE zitadel_sub = ?` で絞り込むので、結果行から `AuthIdentity` を取り出す必要がなくなる。

- [ ] **Step 2.8: `UserDataSource.kt` を Profile 返却に書き換え**

```kotlin
// backend/core/.../infrastructure/datasource/user/UserDataSource.kt
@OptIn(ExperimentalUuidApi::class)
class UserDataSource : UserRepository {
    override fun findProfileByAuthIdentity(identity: AuthIdentity): Profile? =
        queryLatest { UsersTable.zitadel_sub eq identity.subject() }

    override fun findProfileById(id: UserId): Profile? =
        queryLatest { UsersTable.id eq id() }

    private fun queryLatest(where: () -> Op<Boolean>): Profile? {
        // 既存の latestNames JOIN ロジックそのまま
        // ...
        return UsersTable
            .join(latestNames, JoinType.INNER, ...)
            .join(UserDisplayNamesTable, JoinType.INNER) { ... }
            .selectAll()
            .where { where() }
            .singleOrNull()
            ?.toProfile()
    }
}
```

（JOIN 構造は現状維持。`toUser` → `toProfile` の差し替えと戻り値型のみ変更）

- [ ] **Step 2.9: `UserRegisterDataSource.kt` を Profile 返却・UserId 引数に書き換え**

```kotlin
@OptIn(ExperimentalUuidApi::class)
class UserRegisterDataSource : UserRegisterRepository {
    override fun register(identity: AuthIdentity, defaultDisplayName: DisplayName): Profile {
        val insertedUserId =
            UsersTable.insert { it[zitadel_sub] = identity.subject() } get UsersTable.id

        UserDisplayNamesTable.insert {
            it[user_id] = insertedUserId
            it[display_name] = defaultDisplayName()
        }

        return (UsersTable innerJoin UserDisplayNamesTable)
            .selectAll()
            .where { UsersTable.id eq insertedUserId }
            .single()
            .toProfile()
    }

    override fun rename(userId: UserId, newName: DisplayName) {
        UserDisplayNamesTable.insert {
            it[user_id] = userId()
            it[display_name] = newName()
        }
    }
}
```

- [ ] **Step 2.10: `HouseholdDataSource.kt` を `findOf(userId: UserId)` と `toProfile()` に書き換え**

シグネチャ変更:
- `findOf(user: User)` → `findOf(userId: UserId)`
- `where { ... eq user.id() }` → `where { ... eq userId() }`
- `row.toUser()` → `row.toProfile()`
- `HouseholdMember(user = ..., role = ...)` → `HouseholdMember(profile = ..., role = ...)`

JOIN 構造は維持。

- [ ] **Step 2.11: `StockHydration.kt` の actor を Profile に拡張**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.time.Instant

internal fun toStockMovement(
    product: Product,
    actor: Profile,
    type: StockMovementType,
    quantity: Int,
    occurredAt: Instant,
    note: String,
): StockMovement {
    val q = Quantity(quantity)
    val occurred = OccurredAt(occurredAt)
    val n = Note(note)
    return when (type) {
        StockMovementType.REPLENISHMENT -> Replenishment(product, q, occurred, actor, n)
        StockMovementType.CONSUMPTION -> Consumption(product, q, occurred, actor, n)
    }
}
```

`(unknown)` スタブを根絶。`UserId(actorId)` の構築コードも不要（呼び出し側で Profile を組み立てる）。

- [ ] **Step 2.12: `StockDataSource.kt` の SELECT に UserDisplayNamesTable JOIN を追加**

`StockMovementsTable` 選択時に、actor の display_name を解決するための JOIN を追加:

```kotlin
val maxNameIdAlias = UserDisplayNamesTable.id.max().alias("max_name_id")
val latestNames =
    UserDisplayNamesTable
        .select(UserDisplayNamesTable.user_id, maxNameIdAlias)
        .groupBy(UserDisplayNamesTable.user_id)
        .alias("latest_names")
val latestNameUserId = latestNames[UserDisplayNamesTable.user_id]
val latestNameMaxId = latestNames[maxNameIdAlias]

val rows =
    StockMovementsTable
        // ... 既存の product 系 JOIN ...
        .join(UsersTable, JoinType.INNER, onColumn = StockMovementsTable.acted_by, otherColumn = UsersTable.id)
        .join(latestNames, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latestNameUserId)
        .join(UserDisplayNamesTable, JoinType.INNER) {
            (UserDisplayNamesTable.user_id eq latestNameUserId) and
                (UserDisplayNamesTable.id eq latestNameMaxId)
        }
        .selectAll()
        // ... ここで row.toProfile() を呼んで toStockMovement に渡す
```

各 row で:

```kotlin
val actor = row.toProfile()
val movement = toStockMovement(product, actor, ...)
```

- [ ] **Step 2.13: 他の `*RegisterDataSource` を `actor: UserId` に**

`CatalogItemRegisterDataSource`, `ProductRegisterDataSource`, `HouseholdRegisterDataSource`, `StockRegisterDataSource` 各々:

- 引数 `actor: User` → `actor: UserId`
- 利用箇所 `actor.id()` → `actor()`

- [ ] **Step 2.14: `ActorResolver.kt` を Profile 返却に変更**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.configuration.error.UnauthorizedException
import net.brightroom.mindstock.domain.model.user.profile.Profile

fun ApplicationCall.actor(userRepository: UserRepository): Profile {
    val principal = principal<MindstockPrincipal>() ?: throw UnauthorizedException("missing principal")
    return userRepository.findProfileByAuthIdentity(principal.authIdentity)
        ?: throw UnauthorizedException("unknown user")
}
```

- [ ] **Step 2.15: 各 Controller の actor 型を Profile に変更し、サービス呼び出しに `.userId` を渡す**

各 Controller の典型変更:

```kotlin
// 旧
private val actor: User by lazy { call.actor(userRepository) }
override suspend fun something(...) = tx(database) { someService.do(actor, ...) }

// 新
private val actor: Profile by lazy { call.actor(userRepository) }
override suspend fun something(...) = tx(database) { someService.do(actor.userId, ...) }
```

例外: `UserController.rename` のように `actor` を直接 service に渡しているケースは、service が `UserId` を受けるようになるので `actor.userId` を渡す:

```kotlin
override suspend fun rename(displayName: DisplayName) =
    tx(database) { userRegisterService.rename(actor.userId, displayName) }
```

`UserPublicController.register` は actor 解決前に呼ばれる（新規登録）ので変更なし、ただし戻り値が `Profile`:

```kotlin
override suspend fun register(displayName: DisplayName): Profile {
    val principal = call.principal<MindstockPrincipal>()
        ?: throw UnauthorizedException("missing principal")
    return tx(database) { userRegisterService.register(principal.authIdentity, displayName) }
}
```

- [ ] **Step 2.16: `UserPublicRpcService.kt` の register 戻り値を Profile に変更**

```kotlin
// rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/UserPublicRpcService.kt
package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Rpc
interface UserPublicRpcService {
    suspend fun register(displayName: DisplayName): Profile
}
```

- [ ] **Step 2.17: frontend で `User` を参照している箇所を `Profile` に置換**

確認コマンド:
```bash
grep -rn "domain.model.user.User\b" frontend/ --include="*.kt"
```

各箇所で `User` を `Profile` に変更、`.displayName` アクセスはそのまま使える（Profile も `displayName` を持つ）。`.id` → `.userId` に置換が必要な箇所もあるので併せて確認。

- [ ] **Step 2.18: domain test を Profile に追従**

「File Plan」の domain test ファイル群:

- `SerializationRoundTripTest.kt`: `User(...)` 構築箇所を `Profile(...)` に
- `HouseholdMembersTest.kt`: helper を Profile 構築に
- `StockTest.kt`, `StockMovementsTest.kt`, `ShoppingListTest.kt`: 同様

典型パターン:

```kotlin
// 旧
val user = User(
    id = UserId.create(),
    authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
    displayName = DisplayName("Alice"),
)
HouseholdMember(user = user, role = HouseholdMemberRole.OWNER)

// 新
val profile = Profile(userId = UserId.create(), displayName = DisplayName("Alice"))
HouseholdMember(profile = profile, role = HouseholdMemberRole.OWNER)
```

- [ ] **Step 2.19: backend/api E2E・統合テストを Profile に追従**

`Fixtures.kt`, `E2eTestSupport.kt`, 各 `*E2eTest.kt`, `*IntegrationTest.kt` で `User(...)` 構築・型注釈を全て `Profile` または `UserId` に置換。

確認コマンド:
```bash
grep -rln "import net.brightroom.mindstock.domain.model.user.User\b" backend/api/src/test
```

- [ ] **Step 2.20: 全モジュールビルドとテスト実行**

```bash
./gradlew clean build
```

期待: BUILD SUCCESSFUL、全テスト pass。

「unresolved reference: User」が残る場合は grep で漏れを探す:

```bash
grep -rn "net.brightroom.mindstock.domain.model.user.User\b" --include="*.kt" .
```

- [ ] **Step 2.21: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor: replace User with Profile/UserId composition across all layers

domain の User クラスを廃止し、composition は user.profile.Profile を
経由するように全層を切り替えた。書き込みパスの actor は UserId を直接
受け取る形に統一。StockHydration の (unknown) スタブも実 JOIN で解消。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

注: コミット規模が大きすぎる場合、論理サブセット単位（domain → backend/core → backend/api → rpc/frontend → tests）で複数コミットに分けても良い。ただし各サブセット間はビルド不通になるため、最終 push 前に rebase で squash するか、PR 内で順次 push する。

---

## Phase 3: DomainException 廃止

**目的:** 全 7 VO の `init` で投げている `DomainException.XxxYyy` を `IllegalArgumentException` に置換し、`DomainException.kt` 自体を削除する。各 VO ごとに独立コミット可能（テストは VO と同コミット）。

**順序:** 依存関係なく独立。順不同で良いが、以下の順を推奨（変更量が少ない順）。

### Files

- Modify: `domain/.../model/stock/Quantity.kt` + `commonTest/.../model/stock/QuantityTest.kt`
- Modify: `domain/.../model/product/MinimumStock.kt` + `commonTest/.../model/product/MinimumStockTest.kt`
- Modify: `domain/.../model/stock/OccurredAt.kt` + `commonTest/.../model/stock/OccurredAtTest.kt`
- Modify: `domain/.../model/user/profile/DisplayName.kt` + `commonTest/.../model/user/profile/DisplayNameTest.kt`
- Modify: `domain/.../model/catalog/CatalogItemName.kt` + `commonTest/.../model/catalog/CatalogItemNameTest.kt`
- Modify: `domain/.../model/catalog/CatalogItemUnit.kt` + `commonTest/.../model/catalog/CatalogItemUnitTest.kt`
- Modify: `domain/.../model/user/auth/AuthSubject.kt` + `commonTest/.../model/user/auth/AuthSubjectTest.kt`
- Delete: `domain/.../exception/DomainException.kt`
- Delete: `domain/src/commonTest/.../exception/DomainExceptionTest.kt`

### Steps

各 VO で同じパターンを繰り返す。以下、`Quantity` を代表例として詳述。

- [ ] **Step 3.1: `QuantityTest.kt` の期待例外を IAE に変更（失敗確認）**

```kotlin
// domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/QuantityTest.kt
package net.brightroom.mindstock.domain.model.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class QuantityTest {
    @Test
    fun `zero throws IAE`() {
        shouldThrow<IllegalArgumentException> { Quantity(0) }
    }

    @Test
    fun `negative throws IAE`() {
        shouldThrow<IllegalArgumentException> { Quantity(-1) }
    }

    @Test
    fun `positive ok`() {
        Quantity(1)() shouldBe 1
    }
}
```

```bash
./gradlew :domain:commonTest --tests "net.brightroom.mindstock.domain.model.stock.QuantityTest"
```

期待: 旧 `DomainException.InvalidQuantity` を投げているので、`shouldThrow<IllegalArgumentException>` は `IllegalArgumentException` のサブタイプではない `RuntimeException` を受け取ることになる → FAIL

実際は `DomainException.InvalidQuantity` は `RuntimeException` 継承で IAE のサブタイプではないため、テストが失敗することを確認する。

- [ ] **Step 3.2: `Quantity.kt` の init を require に変更**

```kotlin
// domain/.../model/stock/Quantity.kt
package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class Quantity(
    private val value: Int,
) {
    init {
        require(value > 0) { "quantity must be > 0, got $value" }
    }

    override fun toString(): String = value.toString()

    operator fun invoke(): Int = value
}
```

`import net.brightroom.mindstock.domain.exception.DomainException` を削除。

- [ ] **Step 3.3: テストが pass することを確認**

```bash
./gradlew :domain:commonTest --tests "net.brightroom.mindstock.domain.model.stock.QuantityTest"
```

期待: PASS。`require` は `IllegalArgumentException` を投げるため `shouldThrow<IllegalArgumentException>` が成立。

- [ ] **Step 3.4: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Quantity.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/QuantityTest.kt
git commit -m "$(cat <<'EOF'
refactor(domain): replace DomainException.InvalidQuantity with IAE

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 3.5: `MinimumStock` を同じパターンで変更**

`MinimumStock.kt`:

```kotlin
init {
    require(value >= 0) { "minimum_stock must be >= 0, got $value" }
}
```

`MinimumStockTest.kt` を `shouldThrow<IllegalArgumentException>` に。テスト確認 → コミット。

- [ ] **Step 3.6: `OccurredAt` を同じパターンで変更**

注: 現状 `OccurredAt` は `data class` で secondary constructor を持つ。spec 3.2 では「init 単一化」を別アクションとして P2 に置いている。**この Plan A では `throw → require` への単純置換のみ実施**:

```kotlin
@Serializable
data class OccurredAt(
    private val value: Instant,
) {
    constructor(value: Instant, now: Instant) : this(value) {
        require(value <= now) { "occurredAt $value must be <= now $now" }
    }

    override fun toString(): String = value.toString()
    operator fun invoke(): Instant = value
}
```

注: ローカル変更で既に `Clock.System.now()` を使った init 化が試されているが、その判断は Plan C に委ねる。本 Plan ではローカル変更を rebase で吸収せず、secondary constructor 内の throw のみ require に置換する。

`OccurredAtTest.kt` を `shouldThrow<IllegalArgumentException>` に。テスト確認 → コミット。

- [ ] **Step 3.7: `DisplayName`, `CatalogItemName`, `CatalogItemUnit`, `AuthSubject` を同パターンで変更**

各々:

```kotlin
init {
    require(value.isNotBlank()) { "... must not be blank" }
    require(value.length <= MAX) { "... length ${value.length} > MAX" }
}
```

各テストの `shouldThrow<DomainException.XxxYyy>` を `shouldThrow<IllegalArgumentException>` に変更。各 VO 単位でコミット。

- [ ] **Step 3.8: `DomainException.kt` と `DomainExceptionTest.kt` を削除**

```bash
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt
git rm domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/exception/DomainExceptionTest.kt
```

`exception/` ディレクトリも空になるので削除。

確認:
```bash
grep -rn "DomainException" --include="*.kt" .
```

期待: 出力なし（残っていれば削除漏れ）。

- [ ] **Step 3.9: 全モジュールビルド**

```bash
./gradlew clean build
```

期待: BUILD SUCCESSFUL。

- [ ] **Step 3.10: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): remove DomainException hierarchy

全 11 ケースが VO の値域違反であり IllegalArgumentException で意味的に
十分なため、sealed DomainException 階層を廃止。各 VO の init は require()
を使う。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4: 最終確認

**目的:** 全体ビルド・全テスト・lint を pass させる。

### Steps

- [ ] **Step 4.1: clean build**

```bash
./gradlew clean build
```

- [ ] **Step 4.2: 全テスト実行**

```bash
./gradlew test
```

- [ ] **Step 4.3: integration test と e2e test も実行**

```bash
./gradlew :backend:api:integrationTest :backend:api:e2eTest
```

（実際のタスク名はプロジェクトに合わせる。`./gradlew tasks --all | grep -i test` で確認）

- [ ] **Step 4.4: `User` / `DomainException` の残存チェック**

```bash
grep -rn "import net.brightroom.mindstock.domain.model.user.User\b" --include="*.kt" .
grep -rn "import net.brightroom.mindstock.domain.exception.DomainException" --include="*.kt" .
grep -rn "DomainException\." --include="*.kt" .
```

すべて出力 0 件であること。

- [ ] **Step 4.5: spotless / ktlint**

```bash
./gradlew spotlessApply
```

差分が出たら追加コミット:

```bash
git add -A
git commit -m "style: spotlessApply after Plan A"
```

- [ ] **Step 4.6: 動作確認（任意）**

ローカルで backend を起動し、UI から:
- 新規ユーザー登録（`/register` 相当）→ Profile が返ること
- 世帯メンバー一覧表示 → 各メンバー名が見えること
- 在庫履歴表示 → actor の display_name が表示されること

これらは UI 仕様次第。frontend dev server を立てて目視確認。

---

## 検証チェックリスト

Plan A 完了の判定条件:

- [ ] `grep -rn "net.brightroom.mindstock.domain.model.user.User\b" --include="*.kt" .` が 0 件
- [ ] `grep -rn "net.brightroom.mindstock.domain.exception.DomainException" --include="*.kt" .` が 0 件
- [ ] `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/User.kt` が存在しない
- [ ] `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt` が存在しない
- [ ] `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/profile/Profile.kt` が存在し、`@Serializable data class` である
- [ ] `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/profile/DisplayName.kt` が存在（移動済）
- [ ] `./gradlew clean build` が成功
- [ ] `./gradlew test` が全テスト pass
- [ ] `backend/.../infrastructure/datasource/stock/StockHydration.kt` に `"(unknown)"` 文字列が存在しない
- [ ] `HouseholdMember` のフィールド名が `profile`、`StockMovement.actor` の型が `Profile`

---

## 想定リスク

| リスク | 対策 |
|---|---|
| Phase 2 コミットが巨大すぎてレビュー困難 | 論理サブセット単位（domain → backend/core → backend/api → rpc/frontend → tests）で複数コミットに分け、PR レビューでも順次確認 |
| `kotlinx-rpc` の wire 形式変更でクライアントとのバージョン不整合 | 内部リファクタなので backend と frontend を同時 deploy。前提クライアント無し |
| `StockHydration` の JOIN 拡張でクエリプランが悪化 | 既存の `UserDataSource.queryLatest` と同じ `latestNames` aliased subquery パターンを使うので問題なし。`UserDisplayNamesTable` の `(user_id, id)` index を活用 |
| `OccurredAt` のローカル変更（`Clock.System.now()` init 化）と競合 | Phase 3.6 で secondary constructor の throw → require のみ実施。ローカル変更の方針判断は Plan C に委ねる旨 commit message に書く |
| frontend の User 利用箇所が未把握（前調査が App.kt しか触れていない） | Step 1.3 と Step 2.17 で grep ベースで網羅。漏れたらビルドが落ちて分かる |
