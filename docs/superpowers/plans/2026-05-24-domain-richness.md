# Domain Richness Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Plan 3(PR #50)で実装した `:domain` 層を「リッチドメインモデル」に書き直す。composition、collection、ファーストクラス計算概念を導入し、`id` / `createdAt` のインフラ漏れを解消する。

**Architecture:** 全 aggregate を `data class` 化し、ID 経由参照を composition に置き換える。fact クラス(`UserDisplayName` 等)は集約ルートに統合。Stock 系は ID を削除しサブパッケージ(`replenishment/`, `consumption/`)に再配置。新規 `Stock`(現在在庫計算)、`ShoppingList`、`EffectiveQuantity`、各 Collection クラスを追加。Repository は多引数。

**Tech Stack:** Kotlin 2.3.21(KMP commonMain + jvm + wasmJs)、kotlinx-serialization、Kotest、kotlin.uuid.Uuid、kotlin.time.Instant。

**Reference:** [docs/superpowers/specs/2026-05-24-domain-richness-design.md](../specs/2026-05-24-domain-richness-design.md) と親仕様 [2026-05-23-mindstock-design.md](../specs/2026-05-23-mindstock-design.md)。

---

## File Structure(変更全体図)

```
domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/
├── exception/DomainException.kt                  # MODIFY: サブクラス整理(Aggregate ガード系削除、AuthSubjectBlank 追加)
├── model/
│   ├── user/
│   │   ├── User.kt                                  # REWRITE: data class、authIdentity composition
│   │   ├── UserId.kt                                # (変更なし)
│   │   ├── DisplayName.kt                           # (変更なし)
│   │   ├── UserDisplayName.kt                       # DELETE
│   │   ├── UserDisplayNameId.kt                     # DELETE
│   │   ├── ZitadelSub.kt                            # DELETE
│   │   └── auth/                                    # NEW サブパッケージ
│   │       ├── AuthIdentity.kt                      # NEW: data class
│   │       ├── AuthProvider.kt                      # NEW: enum
│   │       └── AuthSubject.kt                       # NEW: VO
│   ├── household/
│   │   ├── Household.kt                             # REWRITE: data class、members: HouseholdMembers
│   │   ├── HouseholdId.kt                           # (変更なし)
│   │   ├── HouseholdMember.kt                       # NEW: data class(user, role)
│   │   ├── HouseholdMembers.kt                      # NEW: collection(owner, contains, ...)
│   │   ├── HouseholdMemberRole.kt                   # (変更なし)
│   │   ├── HouseholdMembership.kt                   # DELETE
│   │   ├── HouseholdMembershipId.kt                 # DELETE
│   │   ├── HouseholdMembershipRevocation.kt         # DELETE
│   │   └── HouseholdMembershipRevocationId.kt       # DELETE
│   ├── catalog/
│   │   ├── CatalogItem.kt                           # REWRITE: data class、name+unit を直接保持
│   │   ├── CatalogItemId.kt                         # (変更なし)
│   │   ├── CatalogItemName.kt                       # (変更なし)
│   │   ├── CatalogItemUnit.kt                       # (変更なし)
│   │   ├── CatalogItems.kt                          # NEW: collection
│   │   ├── CatalogItemRevision.kt                   # DELETE
│   │   └── CatalogItemRevisionId.kt                 # DELETE
│   ├── product/
│   │   ├── Product.kt                               # REWRITE: data class、catalogItem composition、archived: Boolean
│   │   ├── ProductId.kt                             # (変更なし)
│   │   ├── MinimumStock.kt                          # (変更なし)
│   │   ├── Products.kt                              # NEW: collection
│   │   ├── ProductMinimumStock.kt                   # DELETE
│   │   ├── ProductMinimumStockId.kt                 # DELETE
│   │   ├── ProductArchive.kt                        # DELETE
│   │   └── ProductArchiveId.kt                      # DELETE
│   ├── stock/
│   │   ├── Stock.kt                                 # NEW: 在庫計算(currentQuantity, needsReplenishment, shortage)
│   │   ├── EffectiveQuantity.kt                     # NEW: 訂正適用後の数量計算
│   │   ├── Quantity.kt                              # (変更なし)
│   │   ├── OccurredAt.kt                            # (変更なし)
│   │   ├── CorrectedAt.kt                           # NEW: 訂正日時 VO
│   │   ├── Note.kt                                  # (変更なし)
│   │   ├── Reason.kt                                # (変更なし)
│   │   ├── replenishment/                           # NEW サブパッケージ
│   │   │   ├── Replenishment.kt                     # MOVE+REWRITE: id 削除、Stock prefix 削除
│   │   │   ├── Replenishments.kt                    # NEW: collection
│   │   │   ├── ReplenishmentCorrection.kt           # MOVE+REWRITE: id 削除、correctedAt: CorrectedAt
│   │   │   └── ReplenishmentCorrections.kt          # NEW: collection
│   │   ├── consumption/                             # NEW サブパッケージ
│   │   │   ├── Consumption.kt                       # MOVE+REWRITE
│   │   │   ├── Consumptions.kt                      # NEW: collection
│   │   │   ├── ConsumptionCorrection.kt             # MOVE+REWRITE
│   │   │   └── ConsumptionCorrections.kt            # NEW: collection
│   │   ├── StockReplenishment.kt                    # DELETE(replenishment/ に移動)
│   │   ├── StockReplenishmentId.kt                  # DELETE
│   │   ├── StockConsumption.kt                      # DELETE(consumption/ に移動)
│   │   ├── StockConsumptionId.kt                    # DELETE
│   │   ├── StockReplenishmentCorrection.kt          # DELETE
│   │   ├── StockReplenishmentCorrectionId.kt        # DELETE
│   │   ├── StockConsumptionCorrection.kt            # DELETE
│   │   └── StockConsumptionCorrectionId.kt          # DELETE
│   └── shopping/                                    # NEW パッケージ
│       ├── ShoppingList.kt
│       └── ShoppingListItem.kt
└── repository/
    ├── user/{UserRepository, UserRegisterRepository}.kt           # REWRITE: 多引数、AuthIdentity
    ├── household/{HouseholdRepository, HouseholdRegisterRepository}.kt   # REWRITE
    ├── catalog/{CatalogItemRepository, CatalogItemRegisterRepository}.kt # REWRITE
    ├── product/{ProductRepository, ProductRegisterRepository}.kt         # REWRITE
    └── stock/{StockRepository, StockRegisterRepository}.kt               # REWRITE

domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/
├── exception/DomainExceptionTest.kt                # (変更なし、ただし削除されたサブクラスのテストを除去)
├── model/
│   ├── user/
│   │   ├── DisplayNameTest.kt                       # (変更なし)
│   │   ├── ZitadelSubTest.kt                        # DELETE
│   │   └── auth/AuthSubjectTest.kt                  # NEW
│   ├── household/
│   │   └── HouseholdMembersTest.kt                  # NEW
│   ├── catalog/
│   │   ├── CatalogItemIdTest.kt                     # (変更なし)
│   │   ├── CatalogItemNameTest.kt                   # (変更なし)
│   │   └── CatalogItemUnitTest.kt                   # (変更なし)
│   ├── product/
│   │   ├── MinimumStockTest.kt                      # (変更なし)
│   │   ├── ProductGuardTest.kt                      # DELETE(ガードメソッド削除のため)
│   │   └── ProductsTest.kt                          # NEW(collection の activeOnly)
│   ├── stock/
│   │   ├── QuantityTest.kt                          # (変更なし)
│   │   ├── OccurredAtTest.kt                        # (変更なし)
│   │   ├── CorrectedAtTest.kt                       # NEW
│   │   ├── NoteTest.kt                              # (変更なし)
│   │   ├── ReasonTest.kt                            # (変更なし)
│   │   ├── StockReplenishmentIdTest.kt              # DELETE
│   │   └── StockTest.kt                             # NEW(currentQuantity 等)
│   └── shopping/ShoppingListTest.kt                 # NEW
```

---

## Phase A: DomainException 整理 + 新 VO 追加

### Task 1: DomainException から不要なサブクラスを削除

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt`
- Modify: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/exception/DomainExceptionTest.kt`(壊れたら修正)

新設計では Aggregate ガードを削除するため、関連例外も削除。

- [ ] **Step 1: 該当サブクラスを削除**

`DomainException.kt` を以下に置き換え:

```kotlin
package net.brightroom.mindstock.domain.exception

import kotlinx.datetime.Instant

/**
 * Domain layer の不変条件違反を表す sealed 例外。
 *
 * Value Object のコンストラクタから throw される。
 * Application 層(UseCase)で catch して、必要に応じて RPC 層の
 * InventoryException に翻訳する。
 */
sealed class DomainException(message: String) : RuntimeException(message) {

    class InvalidQuantity(val value: Int) :
        DomainException("quantity must be > 0, got $value")

    class InvalidMinimumStock(val value: Int) :
        DomainException("minimum_stock must be >= 0, got $value")

    class OccurredAtInFuture(val value: Instant, val now: Instant) :
        DomainException("occurredAt $value must be <= now $now")

    class DisplayNameBlank : DomainException("display name must not be blank")
    class DisplayNameTooLong(val length: Int) :
        DomainException("display name length $length > 100")

    class CatalogItemNameBlank : DomainException("catalog item name must not be blank")
    class CatalogItemNameTooLong(val length: Int) :
        DomainException("catalog item name length $length > 200")

    class CatalogItemUnitBlank : DomainException("catalog item unit must not be blank")
    class CatalogItemUnitTooLong(val length: Int) :
        DomainException("catalog item unit length $length > 10")

    class AuthSubjectBlank : DomainException("auth subject must not be blank")
}
```

削除されたもの: `InvalidIdentity`(Long 系 ID が消えるため不要)、`ZitadelSubBlank`(→ `AuthSubjectBlank`)、`ProductArchived`、`ProductNotInHousehold`(Aggregate ガードがなくなるため)。

- [ ] **Step 2: `kotlin.time.Instant` の import 確認**

旧コードでは `kotlin.time.Instant`(stdlib)を使っているはず。確認:
```bash
grep "import.*Instant" domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt
```
`kotlin.time.Instant` であれば OK。`kotlinx.datetime.Instant` だったら `kotlin.time.Instant` に置換。

- [ ] **Step 3: DomainExceptionTest を更新**

`DomainExceptionTest.kt` は既存の `InvalidQuantity` テストのみ持つはず。確認:

```bash
cat domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/exception/DomainExceptionTest.kt
```

`InvalidQuantity` テストはそのまま。`Instant` import が `kotlin.time.Instant` ならそのまま、変更があれば反映。

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :domain:compileKotlinJvm --no-daemon`
Expected: **BUILD FAILED**(他のクラスがまだ古い例外を使っている可能性あり、特に Product、ZitadelSub)。コンパイルエラーの詳細を確認。

- [ ] **Step 5: コンパイルエラーを修正**

`ProductArchived` / `ProductNotInHousehold` / `InvalidIdentity` / `ZitadelSubBlank` への参照が残っている箇所を一時的に コメントアウト(後続タスクで該当箇所を削除/置換するため)。具体的には:
- `Product.kt` の `ensureNotArchived` / `ensureBelongsTo` メソッド(Task 14 で削除)
- `*Id.kt` の Long 系 IDs(後続タスクで削除)
- `ZitadelSub.kt`(Task 6 で削除)

このタスクではコンパイル成功は **目指さない**(Phase B–F で順次解消)。コミットだけ済ませる。

- [ ] **Step 6: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt
git commit -m "refactor(domain): clean up DomainException subclasses

旧設計の Aggregate ガード関連と Long 系 ID 例外を削除し、
新規 AuthSubjectBlank を追加。Plan 3 (domain richness redesign) の
最初の段階。後続タスクで関連クラスを削除/置換する間、
ビルドは一時的に失敗する。"
```

---

### Task 2: CorrectedAt VO を追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/CorrectedAt.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/CorrectedAtTest.kt`

訂正日時を表す VO。検証ロジックなし(`Instant` ラップのみ)。

- [ ] **Step 1: テストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class CorrectedAtTest {
    @Test
    fun `wraps an Instant`() {
        val instant = Instant.parse("2026-05-24T10:00:00Z")
        CorrectedAt(instant).toString() shouldBe "2026-05-24T10:00:00Z"
    }
}
```

- [ ] **Step 2: 実装を書く**

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * 訂正日時を表す Value Object。
 *
 * DB の `created_at` カラムを「訂正がいつ行われたか」という domain 概念として
 * 読み替えて使う。集約ルートの createdAt(インフラメタ)とは扱いが異なる。
 */
@Serializable
@JvmInline
value class CorrectedAt(private val value: Instant) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Instant = value
}
```

- [ ] **Step 3: テスト緑確認**

Run: `./gradlew :domain:jvmTest --tests "*CorrectedAtTest*" --no-daemon`

注意: Task 1 のコンパイル問題が解消されないと domain 全体は落ちる可能性。**個別テストだけが緑になることを目指す**(他のテストが落ちても OK)。もし `--tests` でも落ちる場合、Phase B–F まで進めてから検証する。

- [ ] **Step 4: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/CorrectedAt.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/CorrectedAtTest.kt
git commit -m "feat(domain): add CorrectedAt value object

訂正日時を表す VO。DB の created_at カラムを domain 概念として読み替える。"
```

---

## Phase B: User 集約 refactor + auth/ サブパッケージ

### Task 3: AuthSubject VO を追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/auth/AuthSubject.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/auth/AuthSubjectTest.kt`

- [ ] **Step 1: テストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.user.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class AuthSubjectTest {
    @Test
    fun `accepts non-blank`() {
        AuthSubject("abc-123").toString() shouldBe "abc-123"
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<DomainException.AuthSubjectBlank> { AuthSubject("") }
        shouldThrow<DomainException.AuthSubjectBlank> { AuthSubject("   ") }
    }
}
```

- [ ] **Step 2: 実装を書く**

```kotlin
package net.brightroom.mindstock.domain.model.user.auth

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException

/**
 * 認証プロバイダにおけるサブジェクト識別子(OIDC の sub クレーム相当)。
 * 空文字は禁止。
 */
@Serializable
@JvmInline
value class AuthSubject(private val value: String) {
    init {
        if (value.isBlank()) throw DomainException.AuthSubjectBlank()
    }

    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
```

- [ ] **Step 3: コミット**

ビルド確認は Phase 終了時にまとめて行う(中間状態のため)。

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/auth/AuthSubject.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/auth/AuthSubjectTest.kt
git commit -m "feat(domain): add AuthSubject value object

認証プロバイダのサブジェクト識別子。Zitadel に依存しない抽象。"
```

---

### Task 4: AuthProvider enum と AuthIdentity data class を追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/auth/AuthProvider.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/auth/AuthIdentity.kt`

- [ ] **Step 1: AuthProvider を実装**

```kotlin
package net.brightroom.mindstock.domain.model.user.auth

import kotlinx.serialization.Serializable

/**
 * 認証プロバイダ識別子。
 * 将来的に AUTH0 等を追加可能だが、MVP は ZITADEL のみ。
 */
@Serializable
enum class AuthProvider {
    ZITADEL,
}
```

- [ ] **Step 2: AuthIdentity を実装**

```kotlin
package net.brightroom.mindstock.domain.model.user.auth

import kotlinx.serialization.Serializable

/**
 * 認証プロバイダの識別情報。User が外部認証(Zitadel 等)と紐付くキー。
 * provider + subject の組で一意。
 */
@Serializable
data class AuthIdentity(val provider: AuthProvider, val subject: AuthSubject)
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/auth/AuthProvider.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/auth/AuthIdentity.kt
git commit -m "feat(domain): add AuthIdentity and AuthProvider

User と外部認証プロバイダの紐付けを表現。"
```

---

### Task 5: User を data class に書き換え + ZitadelSub への参照を AuthIdentity に置換

**Files:**
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/User.kt`

- [ ] **Step 1: User を新しい構造に書き換え**

```kotlin
package net.brightroom.mindstock.domain.model.user

import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity

/**
 * アプリ内ユーザー集約。
 * 認証プロバイダの AuthIdentity と表示名を保持する。
 */
data class User(
    val id: UserId,
    val authIdentity: AuthIdentity,
    val displayName: DisplayName,
)
```

ポイント:
- 旧 `User` の `zitadelSub: ZitadelSub` を `authIdentity: AuthIdentity` に置換
- 旧 `User` の `createdAt: Instant` を**削除**
- 旧 `User` の `internal` フィールドを全て public(data class のため)
- `class` を `data class` に変更

- [ ] **Step 2: コンパイル確認(domain 全体は失敗で OK、User.kt 自体のエラーがないことだけ確認)**

Run: `./gradlew :domain:compileKotlinJvm --no-daemon 2>&1 | grep "User.kt"`
Expected: User.kt に関するエラーが出ていないこと。他のファイルのエラーは OK。

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/User.kt
git commit -m "refactor(domain): rewrite User as data class with AuthIdentity composition

ZitadelSub を AuthIdentity に置き換え、createdAt を削除、data class 化。"
```

---

### Task 6: User 配下の旧 fact クラスと ZitadelSub を削除

**Files:**
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/UserDisplayName.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/UserDisplayNameId.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/ZitadelSub.kt`
- Delete: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/ZitadelSubTest.kt`

- [ ] **Step 1: ファイル削除**

```bash
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/UserDisplayName.kt
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/UserDisplayNameId.kt
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/ZitadelSub.kt
git rm domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/ZitadelSubTest.kt
```

- [ ] **Step 2: コミット**

```bash
git commit -m "refactor(domain): delete obsolete user fact classes

UserDisplayName / UserDisplayNameId / ZitadelSub を削除。
User.displayName と User.authIdentity に統合済み。"
```

---

## Phase C: Household 集約 refactor

### Task 7: HouseholdMember data class を追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMember.kt`

- [ ] **Step 1: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.household

import net.brightroom.mindstock.domain.model.user.User

/**
 * 世帯のメンバー(ユーザー + 役割)。
 *
 * Household 集約に含まれる Value Object。
 * 「revoked」状態は Repository が読み込み時にフィルタするため、
 * HouseholdMember を持っている = active なメンバー。
 */
data class HouseholdMember(val user: User, val role: HouseholdMemberRole)
```

- [ ] **Step 2: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMember.kt
git commit -m "feat(domain): add HouseholdMember data class

User と HouseholdMemberRole の組。Household.members の要素型。"
```

---

### Task 8: HouseholdMembers コレクションを追加 + テスト

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembers.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembersTest.kt`

- [ ] **Step 1: テストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HouseholdMembersTest {
    private fun user(name: String) = User(
        id = UserId(Uuid.generateV7()),
        authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-$name")),
        displayName = DisplayName(name),
    )

    @Test
    fun `owner returns the OWNER user`() {
        val ownerUser = user("alice")
        val memberUser = user("bob")
        val members = HouseholdMembers(listOf(
            HouseholdMember(ownerUser, HouseholdMemberRole.OWNER),
            HouseholdMember(memberUser, HouseholdMemberRole.MEMBER),
        ))
        members.owner() shouldBe ownerUser
    }

    @Test
    fun `owner returns null when no OWNER exists`() {
        val u = user("bob")
        val members = HouseholdMembers(listOf(HouseholdMember(u, HouseholdMemberRole.MEMBER)))
        members.owner().shouldBeNull()
    }

    @Test
    fun `contains returns true when user is a member`() {
        val u = user("alice")
        val members = HouseholdMembers(listOf(HouseholdMember(u, HouseholdMemberRole.OWNER)))
        members.contains(u).shouldBeTrue()
    }

    @Test
    fun `contains returns false when user is not a member`() {
        val u1 = user("alice")
        val u2 = user("bob")
        val members = HouseholdMembers(listOf(HouseholdMember(u1, HouseholdMemberRole.OWNER)))
        members.contains(u2).shouldBeFalse()
    }
}
```

- [ ] **Step 2: 実装を書く**

```kotlin
package net.brightroom.mindstock.domain.model.household

import net.brightroom.mindstock.domain.model.user.User

/**
 * 世帯のメンバー一覧。
 *
 * アクティブなメンバーのみを保持(Repository が revoked を除外して読み込む)。
 */
class HouseholdMembers(private val list: List<HouseholdMember>) {
    /** OWNER ロールのメンバーを返す。存在しなければ null。 */
    fun owner(): User? = list.firstOrNull { it.role == HouseholdMemberRole.OWNER }?.user

    /** すべてのアクティブメンバーの User オブジェクトを返す。 */
    fun activeMembers(): List<User> = list.map { it.user }

    /** 指定したユーザーがアクティブメンバーに含まれるか。 */
    fun contains(user: User): Boolean = list.any { it.user == user }

    fun asList(): List<HouseholdMember> = list.toList()
}
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembers.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembersTest.kt
git commit -m "feat(domain): add HouseholdMembers collection

owner() / contains(user) / activeMembers() を提供するコレクション。"
```

---

### Task 9: Household を data class に書き換え

**Files:**
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`

- [ ] **Step 1: Household を新しい構造に書き換え**

```kotlin
package net.brightroom.mindstock.domain.model.household

/**
 * 世帯集約。アクティブなメンバー一覧を持つ。
 */
data class Household(
    val id: HouseholdId,
    val members: HouseholdMembers,
)
```

ポイント:
- 旧 `Household` の `createdAt: Instant` を削除
- `members: HouseholdMembers` を追加
- `class` → `data class`

- [ ] **Step 2: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt
git commit -m "refactor(domain): rewrite Household as data class with members composition

createdAt を削除、members: HouseholdMembers を保持。"
```

---

### Task 10: Household 配下の旧 fact クラスを削除

**Files:**
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembership.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembershipId.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembershipRevocation.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembershipRevocationId.kt`

- [ ] **Step 1: ファイル削除**

```bash
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembership.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembershipId.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembershipRevocation.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembershipRevocationId.kt
```

- [ ] **Step 2: コミット**

```bash
git commit -m "refactor(domain): delete obsolete household membership fact classes

Household.members の HouseholdMember data class に統合済み。"
```

---

## Phase D: CatalogItem 集約 refactor

### Task 11: CatalogItems コレクションを追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItems.kt`

- [ ] **Step 1: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.catalog

/**
 * カタログ商品の集合。検索結果等で使う。
 */
class CatalogItems(private val list: List<CatalogItem>) {
    fun asList(): List<CatalogItem> = list.toList()
    val size: Int get() = list.size
}
```

- [ ] **Step 2: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItems.kt
git commit -m "feat(domain): add CatalogItems collection"
```

---

### Task 12: CatalogItem を data class に書き換え + 旧 fact を削除

**Files:**
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItem.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemRevision.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemRevisionId.kt`

- [ ] **Step 1: CatalogItem を書き換え**

```kotlin
package net.brightroom.mindstock.domain.model.catalog

/**
 * カタログ商品(全世帯で共有される商品概念)。
 *
 * 名前と単位は現在値。リビジョン履歴は Repository が hydrate するときに
 * 最新を取って組み立てる(DB の catalog_item_revisions テーブルは継続使用)。
 */
data class CatalogItem(
    val id: CatalogItemId,
    val name: CatalogItemName,
    val unit: CatalogItemUnit,
)
```

ポイント:
- 旧 `CatalogItem` の `createdBy: UserId`、`createdAt: Instant`、`latestName`、`latestUnit` を整理
- `name` と `unit` を直接保持(最新リビジョンの値)
- `createdBy` / `createdAt` は削除

- [ ] **Step 2: 旧ファイルを削除**

```bash
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemRevision.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemRevisionId.kt
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItem.kt
git commit -m "refactor(domain): rewrite CatalogItem as data class, drop revision fact

CatalogItem は name と unit を直接持つ。Revision 履歴は Repository 内部で扱う。"
```

---

## Phase E: Product 集約 refactor

### Task 13: Products コレクションを追加 + テスト

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/Products.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/product/ProductsTest.kt`

- [ ] **Step 1: テストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.product

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ProductsTest {
    private fun catalogItem(name: String) = CatalogItem(
        id = CatalogItemId(Uuid.generateV7()),
        name = CatalogItemName(name),
        unit = CatalogItemUnit("個"),
    )

    private fun product(name: String, archived: Boolean = false) = Product(
        id = ProductId(Uuid.generateV7()),
        catalogItem = catalogItem(name),
        minimumStock = null,
        archived = archived,
    )

    @Test
    fun `activeOnly excludes archived products`() {
        val active = product("a", archived = false)
        val archived = product("b", archived = true)
        val products = Products(listOf(active, archived))
        products.activeOnly().asList() shouldBe listOf(active)
    }

    @Test
    fun `activeOnly returns empty when all archived`() {
        val a = product("a", archived = true)
        val products = Products(listOf(a))
        products.activeOnly().size shouldBe 0
    }
}
```

- [ ] **Step 2: 実装を書く**

```kotlin
package net.brightroom.mindstock.domain.model.product

/**
 * 商品の集合。世帯の商品リスト等で使う。
 */
class Products(private val list: List<Product>) {
    /** archived = false の商品のみのコレクションを返す。 */
    fun activeOnly(): Products = Products(list.filter { !it.archived })

    fun asList(): List<Product> = list.toList()

    val size: Int get() = list.size
}
```

- [ ] **Step 3: コミット**

ビルド確認は Phase 終了時。

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/Products.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/product/ProductsTest.kt
git commit -m "feat(domain): add Products collection

activeOnly() で archive 済みを除外したコレクションを取得できる。"
```

---

### Task 14: Product を data class に書き換え + 旧 fact / テストを削除

**Files:**
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/Product.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/ProductMinimumStock.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/ProductMinimumStockId.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/ProductArchive.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/ProductArchiveId.kt`
- Delete: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/product/ProductGuardTest.kt`

- [ ] **Step 1: Product を書き換え**

```kotlin
package net.brightroom.mindstock.domain.model.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem

/**
 * 世帯固有の商品インスタンス。CatalogItem を世帯で「採用」したもの。
 *
 * 最低在庫値とアーカイブ状態を集約スナップショットとして持つ。
 * householdId は domain には出さない(Household 経由でアクセス前提)。
 */
data class Product(
    val id: ProductId,
    val catalogItem: CatalogItem,
    val minimumStock: MinimumStock?,
    val archived: Boolean,
)
```

ポイント:
- 旧 `Product` の `householdId: HouseholdId`、`catalogItemId: CatalogItemId`、`createdAt`、`latestMinimumStock`、`archivedAt` を整理
- `catalogItem: CatalogItem` で composition
- `archived: Boolean` で archive 状態を boolean に簡素化
- `ensureNotArchived`、`ensureBelongsTo` を**削除**(UseCase 側で `product.archived` を直接チェック)

- [ ] **Step 2: 旧ファイル削除**

```bash
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/ProductMinimumStock.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/ProductMinimumStockId.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/ProductArchive.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/ProductArchiveId.kt \
       domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/product/ProductGuardTest.kt
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/Product.kt
git commit -m "refactor(domain): rewrite Product as data class, drop guards and fact classes

Product は catalogItem を composition で保持。archived: Boolean に簡素化。
ProductGuardTest を削除(ガードメソッドは UseCase 側に移動)。"
```

---

## Phase F: Stock パッケージ再構成

### Task 15: 旧 Stock* ファイルを全削除(リプレース前のクリーンアップ)

**Files:**
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishment.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishmentId.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockConsumption.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockConsumptionId.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishmentCorrection.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishmentCorrectionId.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockConsumptionCorrection.kt`
- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockConsumptionCorrectionId.kt`
- Delete: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishmentIdTest.kt`

- [ ] **Step 1: ファイル削除**

```bash
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishment.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishmentId.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockConsumption.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockConsumptionId.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishmentCorrection.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishmentCorrectionId.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockConsumptionCorrection.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/StockConsumptionCorrectionId.kt \
       domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishmentIdTest.kt
```

- [ ] **Step 2: コミット**

```bash
git commit -m "refactor(domain): delete legacy Stock-prefixed classes

replenishment/ と consumption/ サブパッケージへ再構成するため一旦削除。"
```

---

### Task 16: Replenishment data class を追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/Replenishment.kt`

- [ ] **Step 1: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock.replenishment

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User

/**
 * 在庫補充イベント。
 *
 * id を持たない(順序は occurredAt、参照は composition で行う)。
 * Repository 実装での domain object と DB 行の対応付け方法は Plan 4-5 で設計。
 */
data class Replenishment(
    val product: Product,
    val quantity: Quantity,
    val occurredAt: OccurredAt,
    val actor: User,
    val note: Note,
)
```

- [ ] **Step 2: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/Replenishment.kt
git commit -m "feat(domain): add Replenishment data class

在庫補充イベント。id なし、composition で product/actor を保持。"
```

---

### Task 17: Replenishments コレクションを追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/Replenishments.kt`

- [ ] **Step 1: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock.replenishment

/**
 * 補充イベントの集合。1 つの Product に紐付く履歴等で使う。
 */
class Replenishments(private val list: List<Replenishment>) {
    fun asList(): List<Replenishment> = list.toList()

    val size: Int get() = list.size
}
```

- [ ] **Step 2: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/Replenishments.kt
git commit -m "feat(domain): add Replenishments collection"
```

---

### Task 18: ReplenishmentCorrection data class を追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/ReplenishmentCorrection.kt`

- [ ] **Step 1: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock.replenishment

import net.brightroom.mindstock.domain.model.stock.CorrectedAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Reason
import net.brightroom.mindstock.domain.model.user.User

/**
 * 補充イベントへの訂正。
 *
 * target に元イベントを composition で保持。
 * correctedAt は「いつ訂正されたか」(DB の created_at を読み替え)。
 */
data class ReplenishmentCorrection(
    val target: Replenishment,
    val correctedQuantity: Quantity,
    val reason: Reason,
    val corrector: User,
    val correctedAt: CorrectedAt,
)
```

- [ ] **Step 2: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/ReplenishmentCorrection.kt
git commit -m "feat(domain): add ReplenishmentCorrection data class

target に Replenishment を composition で保持。correctedAt VO を持つ。"
```

---

### Task 19: ReplenishmentCorrections コレクションを追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/ReplenishmentCorrections.kt`

- [ ] **Step 1: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock.replenishment

/**
 * 単一 Replenishment への訂正の集合。または複数 Replenishment への訂正をまとめた集合。
 */
class ReplenishmentCorrections(private val list: List<ReplenishmentCorrection>) {
    /** 訂正日時の最新を返す。なければ null。 */
    fun latest(): ReplenishmentCorrection? = list.maxByOrNull { it.correctedAt() }

    fun asList(): List<ReplenishmentCorrection> = list.toList()
}
```

注: `it.correctedAt()` は `CorrectedAt.invoke()` で `Instant` を取り出す。

- [ ] **Step 2: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/ReplenishmentCorrections.kt
git commit -m "feat(domain): add ReplenishmentCorrections collection

latest() で最新の訂正を取得できる。"
```

---

### Task 20: Consumption / Consumptions を追加(Replenishment と同形)

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/Consumption.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/Consumptions.kt`

- [ ] **Step 1: Consumption を実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock.consumption

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User

/**
 * 在庫消費イベント。
 */
data class Consumption(
    val product: Product,
    val quantity: Quantity,
    val occurredAt: OccurredAt,
    val actor: User,
    val note: Note,
)
```

- [ ] **Step 2: Consumptions を実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock.consumption

/**
 * 消費イベントの集合。
 */
class Consumptions(private val list: List<Consumption>) {
    fun asList(): List<Consumption> = list.toList()

    val size: Int get() = list.size
}
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/Consumption.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/Consumptions.kt
git commit -m "feat(domain): add Consumption data class and Consumptions collection"
```

---

### Task 21: ConsumptionCorrection / ConsumptionCorrections を追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/ConsumptionCorrection.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/ConsumptionCorrections.kt`

- [ ] **Step 1: ConsumptionCorrection を実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock.consumption

import net.brightroom.mindstock.domain.model.stock.CorrectedAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Reason
import net.brightroom.mindstock.domain.model.user.User

/**
 * 消費イベントへの訂正。
 */
data class ConsumptionCorrection(
    val target: Consumption,
    val correctedQuantity: Quantity,
    val reason: Reason,
    val corrector: User,
    val correctedAt: CorrectedAt,
)
```

- [ ] **Step 2: ConsumptionCorrections を実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock.consumption

class ConsumptionCorrections(private val list: List<ConsumptionCorrection>) {
    fun latest(): ConsumptionCorrection? = list.maxByOrNull { it.correctedAt() }

    fun asList(): List<ConsumptionCorrection> = list.toList()
}
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/ConsumptionCorrection.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/ConsumptionCorrections.kt
git commit -m "feat(domain): add ConsumptionCorrection and ConsumptionCorrections"
```

---

### Task 22: EffectiveQuantity を追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/EffectiveQuantity.kt`

- [ ] **Step 1: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock

/**
 * 補充/消費イベントに訂正を適用した「実効数量」。
 *
 * 訂正があれば最新の correctedQuantity、なければ元の quantity。
 */
class EffectiveQuantity(
    private val originalQuantity: Quantity,
    private val latestCorrectedQuantity: Quantity?,
) {
    fun value(): Int = (latestCorrectedQuantity ?: originalQuantity)()
}
```

- [ ] **Step 2: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/EffectiveQuantity.kt
git commit -m "feat(domain): add EffectiveQuantity calculation

訂正があれば最新の値、なければ元の数量を返す。"
```

---

### Task 23: Stock(計算)を追加 + テスト

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stock.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StockTest.kt`

- [ ] **Step 1: テストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.consumption.Consumption
import net.brightroom.mindstock.domain.model.stock.consumption.Consumptions
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishment
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishments
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StockTest {
    private val user = User(
        id = UserId(Uuid.generateV7()),
        authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
        displayName = DisplayName("alice"),
    )

    private fun productWithMin(min: Int?) = Product(
        id = ProductId(Uuid.generateV7()),
        catalogItem = CatalogItem(
            id = CatalogItemId(Uuid.generateV7()),
            name = CatalogItemName("ハンドソープ"),
            unit = CatalogItemUnit("本"),
        ),
        minimumStock = min?.let { MinimumStock(it) },
        archived = false,
    )

    private val now = Instant.parse("2026-05-24T10:00:00Z")
    private fun occurred(year: Int = 2026, day: Int = 1) =
        OccurredAt(Instant.parse("$year-05-0${day}T10:00:00Z"), now)

    private fun replenish(product: Product, qty: Int) = Replenishment(
        product = product, quantity = Quantity(qty),
        occurredAt = occurred(), actor = user, note = Note(""),
    )

    private fun consume(product: Product, qty: Int) = Consumption(
        product = product, quantity = Quantity(qty),
        occurredAt = occurred(), actor = user, note = Note(""),
    )

    @Test
    fun `currentQuantity is replenishments minus consumptions when no corrections`() {
        val p = productWithMin(null)
        val stock = Stock(
            product = p,
            replenishments = Replenishments(listOf(replenish(p, 5), replenish(p, 3))),
            consumptions = Consumptions(listOf(consume(p, 2))),
            replenishmentCorrections = emptyList(),
            consumptionCorrections = emptyList(),
        )
        stock.currentQuantity() shouldBe 6
    }

    @Test
    fun `needsReplenishment is true when current quantity is below minimum`() {
        val p = productWithMin(5)
        val stock = Stock(
            product = p,
            replenishments = Replenishments(listOf(replenish(p, 3))),
            consumptions = Consumptions(emptyList()),
            replenishmentCorrections = emptyList(),
            consumptionCorrections = emptyList(),
        )
        stock.needsReplenishment().shouldBeTrue()
        stock.shortage() shouldBe 2
    }

    @Test
    fun `needsReplenishment is false when minimumStock is null`() {
        val p = productWithMin(null)
        val stock = Stock(
            product = p,
            replenishments = Replenishments(emptyList()),
            consumptions = Consumptions(emptyList()),
            replenishmentCorrections = emptyList(),
            consumptionCorrections = emptyList(),
        )
        stock.needsReplenishment().shouldBeFalse()
    }
}
```

- [ ] **Step 2: 実装を書く**

```kotlin
package net.brightroom.mindstock.domain.model.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.consumption.Consumption
import net.brightroom.mindstock.domain.model.stock.consumption.ConsumptionCorrection
import net.brightroom.mindstock.domain.model.stock.consumption.Consumptions
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishment
import net.brightroom.mindstock.domain.model.stock.replenishment.ReplenishmentCorrection
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishments

/**
 * 在庫状態。
 *
 * 1 つの Product に対する補充・消費・訂正の集約から、現在数量・買い物リスト要否を計算する。
 */
class Stock(
    val product: Product,
    val replenishments: Replenishments,
    val consumptions: Consumptions,
    private val replenishmentCorrections: List<ReplenishmentCorrection>,
    private val consumptionCorrections: List<ConsumptionCorrection>,
) {
    fun currentQuantity(): Int {
        val replenished = replenishments.asList().sumOf { effective(it).value() }
        val consumed = consumptions.asList().sumOf { effective(it).value() }
        return replenished - consumed
    }

    fun needsReplenishment(): Boolean {
        val minimum = product.minimumStock?.let { it() } ?: return false
        return currentQuantity() < minimum
    }

    fun shortage(): Int {
        val minimum = product.minimumStock?.let { it() } ?: 0
        return (minimum - currentQuantity()).coerceAtLeast(0)
    }

    /** 対象 Replenishment の最新訂正があればその数量、なければ元の数量。 */
    private fun effective(event: Replenishment): EffectiveQuantity {
        val latest = replenishmentCorrections
            .filter { it.target == event }
            .maxByOrNull { it.correctedAt() }
        return EffectiveQuantity(event.quantity, latest?.correctedQuantity)
    }

    private fun effective(event: Consumption): EffectiveQuantity {
        val latest = consumptionCorrections
            .filter { it.target == event }
            .maxByOrNull { it.correctedAt() }
        return EffectiveQuantity(event.quantity, latest?.correctedQuantity)
    }
}
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stock.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StockTest.kt
git commit -m "feat(domain): add Stock calculation aggregate

Product + replenishments + consumptions + corrections から
currentQuantity / needsReplenishment / shortage を計算。"
```

---

## Phase G: ShoppingList

### Task 24: ShoppingList と ShoppingListItem を追加 + テスト

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/shopping/ShoppingList.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/shopping/ShoppingListItem.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/shopping/ShoppingListTest.kt`

- [ ] **Step 1: ShoppingListItem を実装**

```kotlin
package net.brightroom.mindstock.domain.model.shopping

import net.brightroom.mindstock.domain.model.stock.Stock

/**
 * 買い物リスト 1 行。
 */
data class ShoppingListItem(val stock: Stock, val shortage: Int)
```

- [ ] **Step 2: ShoppingList を実装**

```kotlin
package net.brightroom.mindstock.domain.model.shopping

import net.brightroom.mindstock.domain.model.stock.Stock

/**
 * 買い物リストというドメイン概念。
 *
 * Stock のリストから「閾値以下の商品」を抽出する。
 */
class ShoppingList(private val stocks: List<Stock>) {
    fun itemsToBuy(): List<ShoppingListItem> =
        stocks.filter { it.needsReplenishment() }
              .map { ShoppingListItem(it, shortage = it.shortage()) }
}
```

- [ ] **Step 3: テストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.shopping

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.consumption.Consumptions
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishment
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishments
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ShoppingListTest {
    private val user = User(
        UserId(Uuid.generateV7()),
        AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
        DisplayName("alice"),
    )
    private val now = Instant.parse("2026-05-24T10:00:00Z")

    private fun stockOf(name: String, min: Int, currentReplenished: Int): Stock {
        val product = Product(
            id = ProductId(Uuid.generateV7()),
            catalogItem = CatalogItem(
                id = CatalogItemId(Uuid.generateV7()),
                name = CatalogItemName(name),
                unit = CatalogItemUnit("個"),
            ),
            minimumStock = MinimumStock(min),
            archived = false,
        )
        val r = if (currentReplenished > 0) listOf(Replenishment(
            product = product, quantity = Quantity(currentReplenished),
            occurredAt = OccurredAt(Instant.parse("2026-05-23T10:00:00Z"), now),
            actor = user, note = Note(""),
        )) else emptyList()
        return Stock(
            product = product,
            replenishments = Replenishments(r),
            consumptions = Consumptions(emptyList()),
            replenishmentCorrections = emptyList(),
            consumptionCorrections = emptyList(),
        )
    }

    @Test
    fun `itemsToBuy returns only stocks below minimum`() {
        val low = stockOf("a", min = 5, currentReplenished = 2)        // shortage 3
        val ok = stockOf("b", min = 5, currentReplenished = 10)         // 在庫足りる
        val list = ShoppingList(listOf(low, ok))

        val result = list.itemsToBuy()
        result.size shouldBe 1
        result[0].stock shouldBe low
        result[0].shortage shouldBe 3
    }
}
```

- [ ] **Step 4: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/shopping/ \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/shopping/
git commit -m "feat(domain): add ShoppingList domain concept

Stock のリストから needsReplenishment な項目を抽出する。"
```

---

## Phase H: Repository ポート書き換え

### Task 25: UserRepository を書き換え

**Files:**
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/user/UserRepository.kt`
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/user/UserRegisterRepository.kt`

- [ ] **Step 1: UserRepository を書き換え**

```kotlin
package net.brightroom.mindstock.domain.repository.user

import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity

interface UserRepository {
    fun findByAuthIdentity(identity: AuthIdentity): User?
}
```

- [ ] **Step 2: UserRegisterRepository を書き換え**

```kotlin
package net.brightroom.mindstock.domain.repository.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity

interface UserRegisterRepository {
    /** users + 初回 user_display_names を 1 トランザクションで INSERT。 */
    fun register(identity: AuthIdentity, defaultDisplayName: DisplayName): User

    /** user_display_names に新規行を INSERT。 */
    fun rename(user: User, newName: DisplayName)
}
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/user/
git commit -m "refactor(domain): rewrite User repository ports for richness redesign

findByZitadelSub → findByAuthIdentity、register は AuthIdentity を受ける。"
```

---

### Task 26: HouseholdRepository を書き換え

**Files:**
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/household/HouseholdRepository.kt`
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/household/HouseholdRegisterRepository.kt`

- [ ] **Step 1: HouseholdRepository を書き換え**

```kotlin
package net.brightroom.mindstock.domain.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User

interface HouseholdRepository {
    /** ユーザーが所属する世帯(MVP は 1 ユーザー 1 世帯前提)。未所属なら null。 */
    fun findOf(user: User): Household?
}
```

- [ ] **Step 2: HouseholdRegisterRepository を書き換え**

```kotlin
package net.brightroom.mindstock.domain.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User

interface HouseholdRegisterRepository {
    /** households + 初回 household_memberships(OWNER)を 1 トランザクションで INSERT。 */
    fun create(owner: User): Household

    /** household_memberships に行を INSERT。 */
    fun invite(household: Household, user: User, role: HouseholdMemberRole)

    /** household_membership_revocations に行を INSERT。 */
    fun revoke(household: Household, user: User)
}
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/household/
git commit -m "refactor(domain): rewrite Household repository ports

findOf(user) で世帯取得、create/invite/revoke は domain object を受ける多引数。"
```

---

### Task 27: CatalogItemRepository を書き換え

**Files:**
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/catalog/CatalogItemRepository.kt`
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/catalog/CatalogItemRegisterRepository.kt`

- [ ] **Step 1: CatalogItemRepository を書き換え**

```kotlin
package net.brightroom.mindstock.domain.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItems

interface CatalogItemRepository {
    /** 名前部分一致検索。 */
    fun search(query: String, limit: Int = 50): CatalogItems

    /** id 引き(主に RPC 経由)。 */
    fun findById(id: CatalogItemId): CatalogItem?
}
```

- [ ] **Step 2: CatalogItemRegisterRepository を書き換え**

```kotlin
package net.brightroom.mindstock.domain.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User

interface CatalogItemRegisterRepository {
    /** catalog_items + 初回 catalog_item_revisions を 1 トランザクションで INSERT。 */
    fun register(name: CatalogItemName, unit: CatalogItemUnit, createdBy: User): CatalogItem

    /** catalog_item_revisions に行を INSERT。name と unit 両方を渡す責任は呼び出し側。 */
    fun revise(catalogItem: CatalogItem, newName: CatalogItemName, newUnit: CatalogItemUnit, editedBy: User)
}
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/catalog/
git commit -m "refactor(domain): rewrite CatalogItem repository ports"
```

---

### Task 28: ProductRepository を書き換え

**Files:**
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/product/ProductRepository.kt`
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/product/ProductRegisterRepository.kt`

- [ ] **Step 1: ProductRepository を書き換え**

```kotlin
package net.brightroom.mindstock.domain.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.Products

interface ProductRepository {
    /** 世帯の全商品(archived 含む)。 */
    fun listOf(household: Household): Products

    /** 同一世帯で同一カタログ商品を採用済みか引く(UNIQUE 検出用)。 */
    fun find(household: Household, catalogItem: CatalogItem): Product?
}
```

- [ ] **Step 2: ProductRegisterRepository を書き換え**

```kotlin
package net.brightroom.mindstock.domain.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User

interface ProductRegisterRepository {
    /** products に行を INSERT。 */
    fun adopt(household: Household, catalogItem: CatalogItem): Product

    /** product_minimum_stocks に行を INSERT。 */
    fun setMinimumStock(product: Product, value: MinimumStock, editedBy: User)

    /** product_archives に行を INSERT。 */
    fun archive(product: Product, by: User)
}
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/product/
git commit -m "refactor(domain): rewrite Product repository ports"
```

---

### Task 29: StockRepository を書き換え

**Files:**
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/stock/StockRepository.kt`
- Rewrite: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/stock/StockRegisterRepository.kt`

- [ ] **Step 1: StockRepository を書き換え**

```kotlin
package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.consumption.Consumptions
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishments

interface StockRepository {
    /** 1 商品の在庫状態。 */
    fun stockOf(product: Product): Stock

    /** 世帯全商品の在庫状態(ShoppingList 用)。 */
    fun stocksOf(household: Household): List<Stock>

    fun replenishmentHistory(product: Product, limit: Int = 50): Replenishments

    fun consumptionHistory(product: Product, limit: Int = 50): Consumptions
}
```

- [ ] **Step 2: StockRegisterRepository を書き換え**

```kotlin
package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Reason
import net.brightroom.mindstock.domain.model.stock.consumption.Consumption
import net.brightroom.mindstock.domain.model.stock.consumption.ConsumptionCorrection
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishment
import net.brightroom.mindstock.domain.model.stock.replenishment.ReplenishmentCorrection
import net.brightroom.mindstock.domain.model.user.User

interface StockRegisterRepository {
    fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Replenishment

    fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Consumption

    fun correct(
        replenishment: Replenishment,
        correctedQuantity: Quantity,
        reason: Reason,
        by: User,
    ): ReplenishmentCorrection

    fun correct(
        consumption: Consumption,
        correctedQuantity: Quantity,
        reason: Reason,
        by: User,
    ): ConsumptionCorrection
}
```

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/stock/
git commit -m "refactor(domain): rewrite Stock repository ports

多引数 + Stock prefix 削除 + Replenishment/Consumption(id なし)を返す。"
```

---

## Phase I: 最終検証

### Task 30: ビルドと全テストを緑にする

- [ ] **Step 1: 全ビルド確認**

Run: `./gradlew :domain:compileKotlinJvm :domain:compileKotlinWasmJs --no-daemon`
Expected: BUILD SUCCESSFUL

何かエラーが出たら、エラーメッセージに従って該当ファイルを修正する。多くの場合は import が古いままだったり、削除した型を参照していたりが原因。

- [ ] **Step 2: テスト実行**

Run: `./gradlew :domain:jvmTest --no-daemon`
Expected: BUILD SUCCESSFUL

テスト失敗があれば原因を確認(構造変更で意味が変わったテストがあれば修正、新しいテストの assertion が誤っていれば修正)。

- [ ] **Step 3: 影響モジュールのビルド確認**

Run: `./gradlew :shared:rpc:build :backend:infrastructure:schemas:build --no-daemon`
Expected: BUILD SUCCESSFUL

`:backend:infrastructure:schemas` は `HouseholdMemberRole` だけを :domain から使っているはず(これは変更していないので壊れない)。`:shared:rpc` は Placeholder のみ。両方とも問題なく通るはず。

- [ ] **Step 4: spotless**

Run: `./gradlew spotlessCheck --no-daemon`
失敗時: `./gradlew spotlessApply --no-daemon` で修正、その後 spotlessCheck で再確認。

- [ ] **Step 5: 修正があればコミット**

```bash
git status
# 何か変更があれば:
git add -u
git commit -m "style: spotless apply"
```

- [ ] **Step 6: トップレベル check**

Run: `./gradlew check --no-daemon`
Expected: BUILD SUCCESSFUL(全モジュール)

- [ ] **Step 7: ブランチ push + PR**

```bash
git push -u origin refactor/domain-richness-impl
gh pr create --title "refactor: domain richness redesign implementation" --body "Implements docs/superpowers/plans/2026-05-24-domain-richness.md"
```

---

## 注意事項

- **コンパイル中断状態**: Phase A の Task 1 後から Phase E の Task 14 までは、`:domain` モジュールがコンパイルエラー状態になる(中間状態)。各 Phase / Task の細かい確認は最低限に留め、Phase I (Task 30) でまとめて緑化する。
- **Stock event の id 削除**: domain object と DB 行の対応付け問題が Plan 4-5 で扱う(設計仕様セクション 5 末尾の「未解決」参照)。Plan 3 範囲では「id なし」と「Repository インターフェース」だけ揃え、実装は別途。
- **kotlinx.datetime.Instant vs kotlin.time.Instant**: Plan 3 で `kotlin.time.Instant` に移行済み。新規ファイルでも `kotlin.time.Instant` を使う。
- **DB スキーマは変更しない**: すべての履歴テーブル(`user_display_names`、`household_memberships` 等)はそのまま残る。Repository 実装(Plan 5)が DB 履歴から現在状態を hydrate する。
- **テストでの object 構築**: `User`, `Product` 等の data class はすべてのフィールドを引数に取るので、テストでは helper 関数(`fun user(name: String) = User(...)`)を作って取り回す。
