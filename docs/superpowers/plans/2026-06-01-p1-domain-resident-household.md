# P1: domain — resident / household コンテキスト Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:domain` モジュールに resident / household の集約・VO・区分・区分使用・判定・例外を TDD で実装し、`./gradlew :domain:build` が緑になる状態を作る。

**Architecture:** 純粋ドメイン(KMP commonMain、外部依存は kotlin stdlib / kotlinx-serialization / kotlinx-datetime のみ)。ビジネスルールは集約に埋めず **区分(behavior 付き enum)/区分使用(Map ルール表 object)/判定クラス** へ外出しし、集約は薄く委譲する([03 詳細ドメイン](../specs/2026-05-31-mindstock-full-replace-design/03-domain-detail.md))。`Resident` は `id + profile` の薄い集約。`Household` は `(id, profile, members)`、招待は別集約 `Invitation`。**区分はクラス名=英語・値=日本語(ユビキタス言語)**。

**Tech Stack:** Kotlin 2.3.21 Multiplatform / kotlinx-serialization / `kotlin.uuid.Uuid`(`generateV7()`)/ テストは `kotlin.test` + Kotest assertions(`io.kotest.matchers.*` / `io.kotest.assertions.throwables.shouldThrow`)。

---

## 前提・規約(全タスク共通)

- パッケージルート: `net.brightroom.mindstock.domain`。ソースは `domain/src/commonMain/kotlin/...`、テストは `domain/src/commonTest/kotlin/...`(同一パッケージ。`internal` メンバはテストから参照可)。
- VO 規約([domain-guideline](../../../.claude/rules/domain-guideline.md)): `@Serializable @JvmInline value class`、バッキングは `private val value`、`internal operator fun invoke(): T`、`override fun toString()`、バリデーションは `init { require(...) }`(IAE)。
- ID 採番: `companion object { fun create() = XxxId(Uuid.generateV7()) }`。`Uuid` は experimental のため **Task 1 でモジュール単位 opt-in** を設定する(各ファイルに `@OptIn` は書かない)。
- 区分(enum): **型名は英語、entry は日本語**。`domain/.editorconfig` で `enum-entry-name-case` を無効化する(Task 1)。
- テスト実行: `./gradlew :domain:jvmTest --tests "<FQCN>" --console=plain`(KMP の commonTest は jvmTest で走る)。
- コミット前に必ず整形: `./gradlew :domain:spotlessApply`(各 Commit ステップに含む)。

## ファイル構成(このプランで作成するもの)

```
domain/.editorconfig                                                    新規(Task 1)
domain/build.gradle.kts                                                 変更(Task 1: Uuid opt-in)
domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/
  exception/ResourceNotFoundException.kt                               Task 2
  exception/OwnerRequiredException.kt                                  Task 2
  exception/LastOwnerException.kt                                      Task 2
  exception/InvitationInvalidException.kt                              Task 2
  model/resident/identity/ResidentId.kt                               Task 1
  model/resident/identity/auth/AuthIdentity.kt                        Task 5（AuthProvider/AuthSubject/AuthIdentity）
  model/resident/profile/DisplayName.kt                               Task 3
  model/resident/profile/Profile.kt                                   Task 4
  model/resident/Resident.kt                                          Task 4
  model/household/HouseholdId.kt                                      Task 6
  model/household/HouseholdName.kt                                    Task 6
  model/household/Profile.kt                                          Task 7
  model/household/Household.kt                                        Task 11, 12
  model/household/member/RolePermissions.kt                          Task 8（HouseholdMemberRole/HouseholdCapability/RolePermissions）
  model/household/member/Members.kt                                  Task 9（HouseholdMember/Members）
  model/household/member/OwnerChangeability.kt                       Task 10
  model/household/invitation/InvitationCode.kt                       Task 13
  model/household/invitation/Invitation.kt                           Task 14（InvitationValidity/Invitation）
（commonTest 側に各 *Test.kt を同パッケージで作成）
```

---

## Task 1: ビルド土台 + ResidentId

ツールチェーン(Uuid opt-in / 日本語 enum entry / kotest 配線)を最初の VO で検証する。

**Files:**
- Create: `domain/.editorconfig`
- Modify: `domain/build.gradle.kts`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/ResidentId.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/resident/identity/ResidentIdTest.kt`

- [ ] **Step 1: `.editorconfig` を作成(日本語 enum entry を許可)**

`domain/.editorconfig`:
```ini
[*.kt]
ktlint_standard_enum-entry-name-case = disabled
```

- [ ] **Step 2: `build.gradle.kts` に Uuid opt-in を追加**

`domain/build.gradle.kts` の `kotlin { ... }` ブロック先頭(`sourceSets` の前)に追記:
```kotlin
kotlin {
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        // ...既存のまま...
    }
}
```

- [ ] **Step 3: 失敗するテストを書く**

`ResidentIdTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.identity

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.uuid.Uuid

class ResidentIdTest {
    @Test
    fun create_generates_distinct_ids() {
        (ResidentId.create() == ResidentId.create()) shouldBe false
    }

    @Test
    fun wraps_uuid_value() {
        val uuid = Uuid.parse("0192f0c1-2345-7654-89ab-cdef01234567")
        ResidentId(uuid).toString() shouldBe uuid.toString()
    }
}
```

- [ ] **Step 4: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.resident.identity.ResidentIdTest" --console=plain`
Expected: FAIL(`Unresolved reference: ResidentId` のコンパイルエラー)

- [ ] **Step 5: 実装を書く**

`ResidentId.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.identity

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ResidentId(private val value: Uuid) {
    internal operator fun invoke(): Uuid = value

    override fun toString(): String = value.toString()

    companion object {
        fun create(): ResidentId = ResidentId(Uuid.generateV7())
    }
}
```

- [ ] **Step 6: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.resident.identity.ResidentIdTest" --console=plain`
Expected: PASS

- [ ] **Step 7: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/.editorconfig domain/build.gradle.kts domain/src/commonMain domain/src/commonTest
git commit -m "feat(domain): ResidentId と Uuid opt-in / 日本語 enum 許可の土台"
```

---

## Task 2: ドメイン例外

P1 で使う前提崩れ系の専用例外を定義する(`ResourceNotFoundException` は既存方針どおり)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/ResourceNotFoundException.kt`
- Create: `.../exception/OwnerRequiredException.kt`
- Create: `.../exception/LastOwnerException.kt`
- Create: `.../exception/InvitationInvalidException.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/exception/DomainExceptionTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`DomainExceptionTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.exception

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DomainExceptionTest {
    @Test
    fun exceptions_carry_message() {
        ResourceNotFoundException("not found: x").message shouldBe "not found: x"
        OwnerRequiredException("owner required").message shouldBe "owner required"
        LastOwnerException("last owner").message shouldBe "last owner"
        InvitationInvalidException("invalid").message shouldBe "invalid"
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.exception.DomainExceptionTest" --console=plain`
Expected: FAIL(`Unresolved reference`)

- [ ] **Step 3: 4 つの例外を実装**

`ResourceNotFoundException.kt`:
```kotlin
package net.brightroom.mindstock.domain.exception

class ResourceNotFoundException(reason: String) : RuntimeException(reason)
```
`OwnerRequiredException.kt`:
```kotlin
package net.brightroom.mindstock.domain.exception

class OwnerRequiredException(reason: String) : RuntimeException(reason)
```
`LastOwnerException.kt`:
```kotlin
package net.brightroom.mindstock.domain.exception

class LastOwnerException(reason: String) : RuntimeException(reason)
```
`InvitationInvalidException.kt`:
```kotlin
package net.brightroom.mindstock.domain.exception

class InvitationInvalidException(reason: String) : RuntimeException(reason)
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.exception.DomainExceptionTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): 前提崩れ系のドメイン例外 4 種を定義"
```

---

## Task 3: DisplayName VO

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/profile/DisplayName.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/resident/profile/DisplayNameTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`DisplayNameTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.profile

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DisplayNameTest {
    @Test
    fun accepts_and_trims() {
        DisplayName("  たろう  ").invoke() shouldBe "たろう"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { DisplayName("   ") }
    }

    @Test
    fun rejects_over_100_chars() {
        shouldThrow<IllegalArgumentException> { DisplayName("あ".repeat(101)) }
    }

    @Test
    fun accepts_exactly_100_chars() {
        DisplayName("あ".repeat(100)).invoke().length shouldBe 100
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.resident.profile.DisplayNameTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`DisplayName.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.profile

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class DisplayName(private val value: String) {
    init {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
            "DisplayName must be 1..$MAX_LENGTH chars after trim: '$value'"
        }
    }

    internal operator fun invoke(): String = value.trim()

    override fun toString(): String = value.trim()

    companion object {
        const val MAX_LENGTH = 100
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.resident.profile.DisplayNameTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): DisplayName VO（trim 後 1..100）"
```

---

## Task 4: Profile + Resident(resident 集約)

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/profile/Profile.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/Resident.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/resident/ResidentTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`ResidentTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class ResidentTest {
    @Test
    fun holds_id_and_profile() {
        val id = ResidentId.create()
        val resident = Resident(id, Profile(DisplayName("たろう")))
        resident.id shouldBe id
        resident.profile.displayName.invoke() shouldBe "たろう"
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.resident.ResidentTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`Profile.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.profile

import kotlinx.serialization.Serializable

@Serializable
data class Profile(val displayName: DisplayName)
```
`Resident.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident

import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlinx.serialization.Serializable

@Serializable
data class Resident(val id: ResidentId, val profile: Profile)
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.resident.ResidentTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): resident 集約（Profile + Resident）"
```

---

## Task 5: AuthIdentity(境界 VO)

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth/AuthIdentity.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth/AuthIdentityTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`AuthIdentityTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.identity.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AuthIdentityTest {
    @Test
    fun builds_from_provider_and_subject() {
        val auth = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-123"))
        auth.provider shouldBe AuthProvider.ZITADEL
        auth.subject.invoke() shouldBe "sub-123"
    }

    @Test
    fun rejects_blank_subject() {
        shouldThrow<IllegalArgumentException> { AuthSubject(" ") }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentityTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`AuthIdentity.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.identity.auth

import kotlinx.serialization.Serializable

@Serializable
enum class AuthProvider { ZITADEL }

@Serializable
@JvmInline
value class AuthSubject(private val value: String) {
    init {
        require(value.isNotBlank()) { "AuthSubject must not be blank" }
    }

    internal operator fun invoke(): String = value

    override fun toString(): String = value
}

@Serializable
data class AuthIdentity(val provider: AuthProvider, val subject: AuthSubject)
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentityTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): AuthIdentity 境界 VO（AuthProvider/AuthSubject）"
```

---

## Task 6: HouseholdId + HouseholdName

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdId.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdName.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdIdNameTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`HouseholdIdNameTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HouseholdIdNameTest {
    @Test
    fun id_create_is_distinct() {
        (HouseholdId.create() == HouseholdId.create()) shouldBe false
    }

    @Test
    fun name_accepts_and_trims() {
        HouseholdName("  我が家  ").invoke() shouldBe "我が家"
    }

    @Test
    fun name_rejects_blank() {
        shouldThrow<IllegalArgumentException> { HouseholdName("  ") }
    }

    @Test
    fun name_rejects_over_30_chars() {
        shouldThrow<IllegalArgumentException> { HouseholdName("あ".repeat(31)) }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdIdNameTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`HouseholdId.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class HouseholdId(private val value: Uuid) {
    internal operator fun invoke(): Uuid = value

    override fun toString(): String = value.toString()

    companion object {
        fun create(): HouseholdId = HouseholdId(Uuid.generateV7())
    }
}
```
`HouseholdName.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class HouseholdName(private val value: String) {
    init {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
            "HouseholdName must be 1..$MAX_LENGTH chars after trim: '$value'"
        }
    }

    internal operator fun invoke(): String = value.trim()

    override fun toString(): String = value.trim()

    companion object {
        const val MAX_LENGTH = 30
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdIdNameTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): HouseholdId / HouseholdName VO"
```

---

## Task 7: household Profile

resident の `Profile` とは別パッケージ(`...household.Profile`)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Profile.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdProfileTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`HouseholdProfileTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HouseholdProfileTest {
    @Test
    fun holds_name() {
        Profile(HouseholdName("我が家")).name.invoke() shouldBe "我が家"
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdProfileTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`Profile.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

@Serializable
data class Profile(val name: HouseholdName)
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdProfileTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): household Profile（世帯名）"
```

---

## Task 8: 役割と権限(区分 + 区分使用)

`HouseholdMemberRole`(区分)と `HouseholdCapability`(区分)、`RolePermissions`(区分使用 Map 表)。enum 型名は英語・値は日本語。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/member/RolePermissions.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/member/RolePermissionsTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`RolePermissionsTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.member

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RolePermissionsTest {
    @Test
    fun owner_can_do_everything() {
        RolePermissions.allows(HouseholdMemberRole.世帯主, HouseholdCapability.世帯管理) shouldBe true
        RolePermissions.allows(HouseholdMemberRole.世帯主, HouseholdCapability.マスタ管理) shouldBe true
        RolePermissions.allows(HouseholdMemberRole.世帯主, HouseholdCapability.在庫編集) shouldBe true
    }

    @Test
    fun member_can_only_edit_inventory() {
        RolePermissions.allows(HouseholdMemberRole.メンバー, HouseholdCapability.在庫編集) shouldBe true
        RolePermissions.allows(HouseholdMemberRole.メンバー, HouseholdCapability.世帯管理) shouldBe false
    }

    @Test
    fun viewer_can_do_nothing() {
        RolePermissions.allows(HouseholdMemberRole.閲覧者, HouseholdCapability.在庫編集) shouldBe false
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.member.RolePermissionsTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`RolePermissions.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.member

import kotlinx.serialization.Serializable

@Serializable
enum class HouseholdMemberRole { 世帯主, メンバー, 閲覧者 }

enum class HouseholdCapability { 在庫編集, マスタ管理, 世帯管理 }

object RolePermissions {
    private val table: Map<HouseholdMemberRole, Set<HouseholdCapability>> =
        mapOf(
            HouseholdMemberRole.世帯主 to HouseholdCapability.entries.toSet(),
            HouseholdMemberRole.メンバー to setOf(HouseholdCapability.在庫編集),
            HouseholdMemberRole.閲覧者 to emptySet(),
        )

    fun allows(
        role: HouseholdMemberRole,
        capability: HouseholdCapability,
    ): Boolean = table.getValue(role).contains(capability)
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.member.RolePermissionsTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): 役割と権限（区分 HouseholdMemberRole/Capability + 区分使用 RolePermissions）"
```

---

## Task 9: HouseholdMember + Members(ファーストクラスコレクション)

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/member/Members.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/member/MembersTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`MembersTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.member

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class MembersTest {
    private fun resident(name: String) = Resident(ResidentId.create(), Profile(DisplayName(name)))

    @Test
    fun owner_returns_the_owner_resident() {
        val owner = resident("おや")
        val members = Members(
            listOf(
                HouseholdMember(owner, HouseholdMemberRole.世帯主),
                HouseholdMember(resident("こ"), HouseholdMemberRole.メンバー),
            ),
        )
        members.owner() shouldBe owner
        members.size() shouldBe 2
    }

    @Test
    fun roleOf_returns_role_or_throws() {
        val member = resident("こ")
        val members = Members(
            listOf(
                HouseholdMember(resident("おや"), HouseholdMemberRole.世帯主),
                HouseholdMember(member, HouseholdMemberRole.閲覧者),
            ),
        )
        members.roleOf(member.id) shouldBe HouseholdMemberRole.閲覧者
        members.contains(member.id) shouldBe true
        shouldThrow<ResourceNotFoundException> { members.roleOf(ResidentId.create()) }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.member.MembersTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`Members.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.member

import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import kotlinx.serialization.Serializable

@Serializable
data class HouseholdMember(val resident: Resident, val role: HouseholdMemberRole)

@Serializable
data class Members(val list: List<HouseholdMember>) {
    fun size(): Int = list.size

    fun owner(): Resident = list.first { it.role == HouseholdMemberRole.世帯主 }.resident

    fun activeMembers(): List<Resident> = list.map { it.resident }

    fun contains(residentId: ResidentId): Boolean = list.any { it.resident.id == residentId }

    fun roleOf(residentId: ResidentId): HouseholdMemberRole =
        list.firstOrNull { it.resident.id == residentId }?.role
            ?: throw ResourceNotFoundException("member not found: $residentId")
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.member.MembersTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): HouseholdMember + Members（owner/roleOf/contains/size）"
```

---

## Task 10: OwnerChangeability(最後の世帯主 判定・区分)

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/member/OwnerChangeability.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/member/OwnerChangeabilityTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`OwnerChangeabilityTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.member

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class OwnerChangeabilityTest {
    private fun resident(name: String) = Resident(ResidentId.create(), Profile(DisplayName(name)))

    @Test
    fun sole_owner_is_not_changeable() {
        val owner = resident("おや")
        val members = Members(
            listOf(
                HouseholdMember(owner, HouseholdMemberRole.世帯主),
                HouseholdMember(resident("こ"), HouseholdMemberRole.メンバー),
            ),
        )
        OwnerChangeability.on(members, owner.id).allowed shouldBe false
    }

    @Test
    fun one_of_two_owners_is_changeable() {
        val owner1 = resident("おや1")
        val owner2 = resident("おや2")
        val members = Members(
            listOf(
                HouseholdMember(owner1, HouseholdMemberRole.世帯主),
                HouseholdMember(owner2, HouseholdMemberRole.世帯主),
            ),
        )
        OwnerChangeability.on(members, owner1.id).allowed shouldBe true
    }

    @Test
    fun non_owner_is_changeable() {
        val owner = resident("おや")
        val member = resident("こ")
        val members = Members(
            listOf(
                HouseholdMember(owner, HouseholdMemberRole.世帯主),
                HouseholdMember(member, HouseholdMemberRole.メンバー),
            ),
        )
        OwnerChangeability.on(members, member.id).allowed shouldBe true
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.member.OwnerChangeabilityTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`OwnerChangeability.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.member

import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

enum class OwnerChangeability(val allowed: Boolean) {
    可能(true),
    最後の世帯主(false),
    ;

    companion object {
        fun on(
            members: Members,
            target: ResidentId,
        ): OwnerChangeability {
            val owners = members.list.filter { it.role == HouseholdMemberRole.世帯主 }
            val targetIsSoleOwner = owners.size == 1 && owners.first().resident.id == target
            return if (targetIsSoleOwner) 最後の世帯主 else 可能
        }
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.member.OwnerChangeabilityTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): OwnerChangeability（最後の世帯主 判定・区分）"
```

---

## Task 11: Household 集約 — create / rename

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`HouseholdTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile
import kotlin.test.Test

class HouseholdTest {
    private fun resident(name: String) = Resident(ResidentId.create(), ResidentProfile(DisplayName(name)))

    @Test
    fun create_starts_with_single_owner() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        household.members.owner() shouldBe owner
        household.profile.name.invoke() shouldBe "我が家"
    }

    @Test
    fun owner_can_rename() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        household.rename(HouseholdName("新居"), owner.id).profile.name.invoke() shouldBe "新居"
    }

    @Test
    fun non_owner_cannot_rename() {
        val owner = resident("おや")
        val member = resident("こ")
        val household = Household.create(HouseholdName("我が家"), owner)
            .join(member, HouseholdMemberRole.メンバー)
        shouldThrow<OwnerRequiredException> { household.rename(HouseholdName("新居"), member.id) }
    }

    @Test
    fun stranger_cannot_rename() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<ResourceNotFoundException> { household.rename(HouseholdName("新居"), ResidentId.create()) }
    }
}
```

> 注: `join` は Task 12 で実装する。このテストは `non_owner_cannot_rename` で `join` を使うため、Task 12 完了までその 1 ケースは赤のまま許容するか、Task 11 では `join` を使わない形(`Household` を直接 `copy` してメンバー追加)に書き換えてよい。サブエージェント実行では **Task 11 では `create`/`rename` の 3 ケース**(`create_starts_with_single_owner` / `owner_can_rename` / `stranger_cannot_rename`)を緑にし、`non_owner_cannot_rename` は Task 12 で緑化する。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く(create / rename / 認可ヘルパー)**

`Household.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.member.HouseholdCapability
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.household.member.RolePermissions
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import kotlinx.serialization.Serializable

@Serializable
data class Household(
    val id: HouseholdId,
    val profile: Profile,
    val members: Members,
) {
    fun rename(name: HouseholdName, by: ResidentId): Household {
        requireCapability(by, HouseholdCapability.世帯管理)
        return copy(profile = Profile(name))
    }

    private fun requireCapability(by: ResidentId, capability: HouseholdCapability) {
        if (!RolePermissions.allows(members.roleOf(by), capability)) {
            throw OwnerRequiredException("$capability requires owner: $by")
        }
    }

    companion object {
        fun create(name: HouseholdName, owner: Resident): Household =
            Household(
                id = HouseholdId.create(),
                profile = Profile(name),
                members = Members(listOf(HouseholdMember(owner, HouseholdMemberRole.世帯主))),
            )
    }
}
```

- [ ] **Step 4: create / rename のテストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdTest" --console=plain`
Expected: `non_owner_cannot_rename` 以外 PASS(`join` 未実装による 1 件 FAIL は Task 12 で解消)

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Household.create / rename（区分使用で認可）"
```

---

## Task 12: Household 集約 — join / changeRole / removeMember / leave

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembershipTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`HouseholdMembershipTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.LastOwnerException
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile
import kotlin.test.Test

class HouseholdMembershipTest {
    private fun resident(name: String) = Resident(ResidentId.create(), ResidentProfile(DisplayName(name)))

    @Test
    fun join_adds_member_with_role() {
        val owner = resident("おや")
        val newcomer = resident("こ")
        val household = Household.create(HouseholdName("我が家"), owner)
            .join(newcomer, HouseholdMemberRole.メンバー)
        household.members.contains(newcomer.id) shouldBe true
        household.members.roleOf(newcomer.id) shouldBe HouseholdMemberRole.メンバー
    }

    @Test
    fun owner_can_change_member_role() {
        val owner = resident("おや")
        val member = resident("こ")
        val household = Household.create(HouseholdName("我が家"), owner)
            .join(member, HouseholdMemberRole.閲覧者)
            .changeRole(member.id, HouseholdMemberRole.メンバー, owner.id)
        household.members.roleOf(member.id) shouldBe HouseholdMemberRole.メンバー
    }

    @Test
    fun demoting_last_owner_is_rejected() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<LastOwnerException> {
            household.changeRole(owner.id, HouseholdMemberRole.メンバー, owner.id)
        }
    }

    @Test
    fun non_owner_cannot_change_role() {
        val owner = resident("おや")
        val member = resident("こ")
        val household = Household.create(HouseholdName("我が家"), owner)
            .join(member, HouseholdMemberRole.メンバー)
        shouldThrow<OwnerRequiredException> {
            household.changeRole(owner.id, HouseholdMemberRole.閲覧者, member.id)
        }
    }

    @Test
    fun owner_can_remove_member() {
        val owner = resident("おや")
        val member = resident("こ")
        val household = Household.create(HouseholdName("我が家"), owner)
            .join(member, HouseholdMemberRole.メンバー)
            .removeMember(member.id, owner.id)
        household.members.contains(member.id) shouldBe false
    }

    @Test
    fun removing_last_owner_is_rejected() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<LastOwnerException> { household.removeMember(owner.id, owner.id) }
    }

    @Test
    fun member_can_leave() {
        val owner = resident("おや")
        val member = resident("こ")
        val household = Household.create(HouseholdName("我が家"), owner)
            .join(member, HouseholdMemberRole.メンバー)
            .leave(member.id)
        household.members.contains(member.id) shouldBe false
    }

    @Test
    fun last_owner_cannot_leave() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<LastOwnerException> { household.leave(owner.id) }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdMembershipTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: `Household` にメソッドを追加**

`Household.kt` の import に追加:
```kotlin
import net.brightroom.mindstock.domain.exception.LastOwnerException
import net.brightroom.mindstock.domain.model.household.member.OwnerChangeability
```
`rename(...)` の下に以下のメソッドを追加:
```kotlin
    fun join(resident: Resident, grantedRole: HouseholdMemberRole): Household =
        copy(members = Members(members.list + HouseholdMember(resident, grantedRole)))

    fun changeRole(target: ResidentId, role: HouseholdMemberRole, by: ResidentId): Household {
        requireCapability(by, HouseholdCapability.世帯管理)
        if (role != HouseholdMemberRole.世帯主 && !OwnerChangeability.on(members, target).allowed) {
            throw LastOwnerException("cannot demote last owner: $target")
        }
        return copy(
            members = Members(
                members.list.map { if (it.resident.id == target) it.copy(role = role) else it },
            ),
        )
    }

    fun removeMember(target: ResidentId, by: ResidentId): Household {
        requireCapability(by, HouseholdCapability.世帯管理)
        if (!OwnerChangeability.on(members, target).allowed) {
            throw LastOwnerException("cannot remove last owner: $target")
        }
        return copy(members = Members(members.list.filterNot { it.resident.id == target }))
    }

    fun leave(by: ResidentId): Household {
        if (!OwnerChangeability.on(members, by).allowed) {
            throw LastOwnerException("last owner cannot leave: $by")
        }
        return copy(members = Members(members.list.filterNot { it.resident.id == by }))
    }
```

- [ ] **Step 4: 両テストクラスが通ることを確認(Task 11 の保留分も緑化)**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.*" --console=plain`
Expected: PASS（`HouseholdTest` の `non_owner_cannot_rename` 含め全緑）

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Household.join/changeRole/removeMember/leave（最後の世帯主を保護）"
```

---

## Task 13: InvitationCode VO(生成つき)

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/invitation/InvitationCode.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/invitation/InvitationCodeTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`InvitationCodeTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.invitation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class InvitationCodeTest {
    @Test
    fun accepts_valid_code() {
        InvitationCode("ABC234").invoke() shouldBe "ABC234"
    }

    @Test
    fun rejects_wrong_length() {
        shouldThrow<IllegalArgumentException> { InvitationCode("ABC23") }
    }

    @Test
    fun rejects_ambiguous_chars() {
        shouldThrow<IllegalArgumentException> { InvitationCode("ABC230") } // 0 は除外
        shouldThrow<IllegalArgumentException> { InvitationCode("ABCO23") } // O は除外
    }

    @Test
    fun generate_produces_valid_code() {
        val code = InvitationCode.generate()
        code.invoke().length shouldBe 6
        code.invoke().all { it in InvitationCode.ALPHABET } shouldBe true
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.invitation.InvitationCodeTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`InvitationCode.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.invitation

import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class InvitationCode(private val value: String) {
    init {
        require(value.length == LENGTH && value.all { it in ALPHABET }) {
            "InvitationCode must be $LENGTH chars from the unambiguous alphabet: '$value'"
        }
    }

    internal operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val LENGTH = 6

        // 曖昧字 0 / O / 1 / I を除外した英数字
        const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

        fun generate(): InvitationCode =
            InvitationCode(
                buildString {
                    repeat(LENGTH) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
                },
            )
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.invitation.InvitationCodeTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): InvitationCode VO（6 桁・曖昧字除外・generate）"
```

---

## Task 14: Invitation 集約(別集約)

`InvitationValidity`(区分)と `Invitation`(別集約。`householdId` 保持、`code` で解決、有効/無効のみ)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/invitation/Invitation.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/invitation/InvitationTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`InvitationTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.invitation

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import kotlin.test.Test

class InvitationTest {
    @Test
    fun issued_invitation_is_usable() {
        val invitation = Invitation.issue(HouseholdId.create(), HouseholdMemberRole.メンバー)
        invitation.usable() shouldBe true
        invitation.validity shouldBe InvitationValidity.有効
        invitation.grantedRole shouldBe HouseholdMemberRole.メンバー
    }

    @Test
    fun revoked_invitation_is_not_usable() {
        val invitation = Invitation.issue(HouseholdId.create(), HouseholdMemberRole.メンバー)
        invitation.revoke().usable() shouldBe false
    }

    @Test
    fun keeps_its_household() {
        val householdId = HouseholdId.create()
        val invitation = Invitation.issue(householdId, HouseholdMemberRole.閲覧者)
        invitation.householdId() shouldBe householdId
    }
}
```

> `householdId()` は `internal`。テストは同一モジュール(commonTest)なので参照できる。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.invitation.InvitationTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`Invitation.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.invitation

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import kotlinx.serialization.Serializable

@Serializable
enum class InvitationValidity { 有効, 無効 }

@Serializable
data class Invitation(
    private val householdId: HouseholdId,
    val code: InvitationCode,
    val grantedRole: HouseholdMemberRole,
    val validity: InvitationValidity,
) {
    fun usable(): Boolean = validity == InvitationValidity.有効

    fun revoke(): Invitation = copy(validity = InvitationValidity.無効)

    internal fun householdId(): HouseholdId = householdId

    companion object {
        fun issue(householdId: HouseholdId, grantedRole: HouseholdMemberRole): Invitation =
            Invitation(householdId, InvitationCode.generate(), grantedRole, InvitationValidity.有効)
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.invitation.InvitationTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Invitation 別集約（issue/revoke/usable・有効/無効）"
```

---

## Task 15: モジュール全体の緑を確認

- [ ] **Step 1: 全テスト + lint を含むフルビルド**

Run: `./gradlew :domain:build --console=plain`
Expected: `BUILD SUCCESSFUL`(全 commonTest が JVM/JS/Wasm で緑、spotless 緑)

- [ ] **Step 2: 差分が無ければスキップ、あればコミット**

```bash
git status --short
# 差分があれば:
git add -A
git commit -m "chore(domain): P1（resident/household）ドメインのビルド緑化を確認"
```

---

## 完了条件(Definition of Done)

- resident(`Resident`/`Profile`/`DisplayName`/`ResidentId`/`AuthIdentity`/`AuthProvider`/`AuthSubject`)が実装され、値域違反は IAE。
- household(`Household`/`Profile`/`HouseholdName`/`HouseholdId`/`HouseholdMember`/`Members`/`HouseholdMemberRole`/`HouseholdCapability`/`RolePermissions`/`OwnerChangeability`)が実装され、認可は区分使用、最後の世帯主保護は区分判定で `LastOwnerException`。
- invitation(`Invitation`/`InvitationCode`/`InvitationValidity`)が別集約として実装(issue/revoke/usable、有効/無効)。
- 例外(`ResourceNotFoundException`/`OwnerRequiredException`/`LastOwnerException`/`InvitationInvalidException`)が定義済み。
- `./gradlew :domain:build` が緑。
- 区分はクラス名英語・値日本語、`domain/.editorconfig` で `enum-entry-name-case` を無効化済み。
