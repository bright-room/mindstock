# P1: domain — resident / household コンテキスト Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:domain` モジュールに resident / household の集約・VO・区分・区分使用・判定・例外を実装し、`./gradlew :domain:build` が緑になる状態を作る。

**Architecture:** 純粋ドメイン(KMP commonMain、外部依存は kotlin stdlib / kotlinx-serialization / kotlinx-datetime のみ)。ビジネスルールは集約に埋めず **区分(behavior 付き enum)/区分使用(Map ルール表 object)/判定クラス** へ外出しし、集約は薄く委譲する([03 詳細ドメイン](../specs/2026-05-31-mindstock-full-replace-design/03-domain-detail.md))。`Resident` は `id + profile` の薄い集約。`Household` は `(id, profile, members)`、招待は別集約 `Invitation`。**区分は型名=英語・値=日本語(ユビキタス言語)**。

**Tech Stack:** Kotlin 2.3.21 Multiplatform / kotlinx-serialization / `kotlin.uuid.Uuid`(`generateV7()`)/ テストは `kotlin.test` + Kotest assertions。

---

## 前提・規約(全タスク共通)

- パッケージルート: `net.brightroom.mindstock.domain`。ソースは `domain/src/commonMain/kotlin/...`、テストは `domain/src/commonTest/kotlin/...`(同一パッケージ。`internal` メンバはテストから参照可)。
- VO 規約([domain-guideline](../../../.claude/rules/domain-guideline.md)): `@Serializable @JvmInline value class`、バッキングは `private val value`、`internal operator fun invoke(): T`、`override fun toString()`、バリデーションは `init { require(...) }`(IAE)。
  - **重要(KMP)**: `@JvmInline value class` の各ファイルに `import kotlin.jvm.JvmInline` を**明示的に書く**。JVM コンパイラは `kotlin.jvm.*` を自動解決するが、JS/Wasm/metadata コンパイラはしない。`:domain:jvmTest` だけでは気付けず、`:domain:build`(全ターゲット)で初めて落ちる。
- ID 採番: `companion object { fun create() = XxxId(Uuid.generateV7()) }`。`Uuid` は experimental なので、**Uuid を使うファイルにだけ `@file:OptIn(ExperimentalUuidApi::class)` を明示**する(gradle 全体 opt-in はしない)。
- 区分(enum): **型名は英語、entry は日本語**。`enum-entry-name-case` は **root `.editorconfig` で無効化**する(Task 1)。
- **テストは「意味のあるもの」だけ書く([.claude/rules/testing.md](../../../.claude/rules/testing.md))。** バリデーション・判定・計算・抽出・状態遷移・前提崩れの例外のみ。コンストラクタ/保持/単純なアクセサ/equals 等は書かない。ロジックの無い VO/集約はテスト無し(コンパイルが通れば足りる)。
- テスト実行: `./gradlew :domain:jvmTest --tests "<FQCN>" --console=plain`(KMP の commonTest は jvmTest で走る)。
- テスト不要タスクの検証: `./gradlew :domain:compileKotlinJvm --console=plain`(緑=実装 OK)。
- コミット前に必ず整形: `./gradlew :domain:spotlessApply`(各 Commit ステップに含む)。

## ファイル構成(このプランで作成・変更するもの)

```
.editorconfig                                                           変更(Task 1: enum-entry-name-case 無効）
domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/
  exception/ResourceNotFoundException.kt                               Task 2（テスト無し）
  exception/OwnerRequiredException.kt                                  Task 2（テスト無し）
  exception/LastOwnerException.kt                                      Task 2（テスト無し）
  exception/InvitationInvalidException.kt                              Task 2（テスト無し）
  model/resident/identity/ResidentId.kt                               Task 1（テスト無し）
  model/resident/identity/auth/AuthIdentity.kt                        Task 5（AuthSubject 値域のみテスト）
  model/resident/profile/DisplayName.kt                               Task 3（値域テスト）
  model/resident/profile/Profile.kt                                   Task 4（テスト無し）
  model/resident/Resident.kt                                          Task 4（テスト無し）
  model/household/HouseholdId.kt                                      Task 6（テスト無し）
  model/household/HouseholdName.kt                                    Task 6（値域テスト）
  model/household/Profile.kt                                          Task 7（テスト無し）
  model/household/Household.kt                                        Task 11, 12（認可・前提崩れテスト）
  model/household/member/RolePermissions.kt                          Task 8（区分使用テスト）
  model/household/member/Members.kt                                  Task 9（抽出テスト）
  model/household/member/OwnerChangeability.kt                       Task 10（判定テスト）
  model/household/invitation/InvitationCode.kt                       Task 13（値域 + 生成テスト）
  model/household/invitation/Invitation.kt                           Task 14（状態遷移テスト）
```

---

## Task 1: ビルド土台 + ResidentId

`.editorconfig`(日本語 enum 値)と Uuid opt-in 方針を整え、最初の VO を置く。ResidentId は値域・ロジックを持たないので**テストは書かない**(コンパイル緑で足りる)。

**Files:**
- Modify: `.editorconfig`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/ResidentId.kt`

- [ ] **Step 1: root `.editorconfig` に日本語 enum 値を許可する設定を追加**

`.editorconfig` の `[{*.kt,*.kts}]` セクション末尾(既存の `ktlint_standard_property-naming = disabled` の下)に 1 行追加:
```ini
ktlint_standard_enum-entry-name-case = disabled
```

- [ ] **Step 2: ResidentId を実装(Uuid opt-in はファイル単位アノテーション)**

`ResidentId.kt`:
```kotlin
@file:OptIn(ExperimentalUuidApi::class)

package net.brightroom.mindstock.domain.model.resident.identity

import kotlin.uuid.ExperimentalUuidApi
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

- [ ] **Step 3: コンパイルが通ることを確認**

Run: `./gradlew :domain:compileKotlinJvm --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add .editorconfig domain/src
git commit -m "feat(domain): ResidentId と日本語 enum 値の許可(.editorconfig)"
```

---

## Task 2: ドメイン例外

P1 で使う前提崩れ系の専用例外を定義する。例外は「メッセージを保持するだけ」なので**テストは書かない**(testing ルール)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/ResourceNotFoundException.kt`
- Create: `.../exception/OwnerRequiredException.kt`
- Create: `.../exception/LastOwnerException.kt`
- Create: `.../exception/InvitationInvalidException.kt`

- [ ] **Step 1: 4 つの例外を実装**

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

- [ ] **Step 2: コンパイルが通ることを確認**

Run: `./gradlew :domain:compileKotlinJvm --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): 前提崩れ系のドメイン例外 4 種を定義"
```

---

## Task 3: DisplayName VO(値域バリデーション)

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/profile/DisplayName.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/resident/profile/DisplayNameTest.kt`

- [ ] **Step 1: 失敗するテストを書く(値域 + 正規化のみ)**

`DisplayNameTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.profile

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DisplayNameTest {
    @Test
    fun trims_surrounding_whitespace() {
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

値を保持するだけの集約なので**テストは書かない**。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/profile/Profile.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/Resident.kt`

- [ ] **Step 1: 実装を書く**

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

- [ ] **Step 2: コンパイルが通ることを確認**

Run: `./gradlew :domain:compileKotlinJvm --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): resident 集約（Profile + Resident）"
```

---

## Task 5: AuthIdentity(境界 VO)

`AuthSubject` の値域だけ意味があるのでテストする。`AuthProvider` / `AuthIdentity` は保持のみ(テスト無し)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth/AuthIdentity.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth/AuthSubjectTest.kt`

- [ ] **Step 1: 失敗するテストを書く(AuthSubject の値域のみ)**

`AuthSubjectTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.identity.auth

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class AuthSubjectTest {
    @Test
    fun rejects_blank_subject() {
        shouldThrow<IllegalArgumentException> { AuthSubject(" ") }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubjectTest" --console=plain`
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

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubjectTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): AuthIdentity 境界 VO（AuthProvider/AuthSubject）"
```

---

## Task 6: HouseholdId + HouseholdName

`HouseholdName` の値域だけテストする。`HouseholdId` は生成のみ(テスト無し)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdId.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdName.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdNameTest.kt`

- [ ] **Step 1: 失敗するテストを書く(HouseholdName の値域のみ)**

`HouseholdNameTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HouseholdNameTest {
    @Test
    fun trims_and_accepts() {
        HouseholdName("  我が家  ").invoke() shouldBe "我が家"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { HouseholdName("  ") }
    }

    @Test
    fun rejects_over_30_chars() {
        shouldThrow<IllegalArgumentException> { HouseholdName("あ".repeat(31)) }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdNameTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`HouseholdId.kt`:
```kotlin
@file:OptIn(ExperimentalUuidApi::class)

package net.brightroom.mindstock.domain.model.household

import kotlin.uuid.ExperimentalUuidApi
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

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdNameTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): HouseholdId / HouseholdName VO"
```

---

## Task 7: household Profile

resident の `Profile` とは別パッケージ(`...household.Profile`)。保持のみなので**テスト無し**。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Profile.kt`

- [ ] **Step 1: 実装を書く**

`Profile.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

@Serializable
data class Profile(val name: HouseholdName)
```

- [ ] **Step 2: コンパイルが通ることを確認**

Run: `./gradlew :domain:compileKotlinJvm --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): household Profile（世帯名）"
```

---

## Task 8: 役割と権限(区分 + 区分使用)

`HouseholdMemberRole`(区分)・`HouseholdCapability`(区分)・`RolePermissions`(区分使用 Map 表)。型名は英語・値は日本語。`RolePermissions.allows` は区分使用の判定なのでテストする。

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
    fun owner_can_manage_household() {
        RolePermissions.allows(HouseholdMemberRole.世帯主, HouseholdCapability.世帯管理) shouldBe true
    }

    @Test
    fun member_can_edit_inventory_but_not_manage_household() {
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

`owner()`(抽出)と `roleOf()`(抽出 + 不在で例外)をテストする。`contains`/`size`/`activeMembers` は単純な転送なのでテスト無し。

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
    }

    @Test
    fun roleOf_returns_role_for_member() {
        val member = resident("こ")
        val members = Members(
            listOf(
                HouseholdMember(resident("おや"), HouseholdMemberRole.世帯主),
                HouseholdMember(member, HouseholdMemberRole.閲覧者),
            ),
        )
        members.roleOf(member.id) shouldBe HouseholdMemberRole.閲覧者
    }

    @Test
    fun roleOf_throws_for_non_member() {
        val members = Members(listOf(HouseholdMember(resident("おや"), HouseholdMemberRole.世帯主)))
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
git commit -m "feat(domain): HouseholdMember + Members（owner/roleOf）"
```

---

## Task 10: OwnerChangeability(最後の世帯主 判定・区分)

判定ロジックなのでテストする。

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

`create` は構築のみだが、`rename` の認可(区分使用 + roleOf 不在例外)がビジネスロジックなのでテストする。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdRenameTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`HouseholdRenameTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile
import kotlin.test.Test

class HouseholdRenameTest {
    private fun resident(name: String) = Resident(ResidentId.create(), ResidentProfile(DisplayName(name)))

    @Test
    fun owner_can_rename() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        household.rename(HouseholdName("新居"), owner.id).profile.name.invoke() shouldBe "新居"
    }

    @Test
    fun stranger_cannot_rename() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<ResourceNotFoundException> { household.rename(HouseholdName("新居"), ResidentId.create()) }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdRenameTest" --console=plain`
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

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdRenameTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Household.create / rename（区分使用で認可）"
```

---

## Task 12: Household 集約 — join / changeRole / removeMember / leave

メンバーシップの規則(役割変更・最後の世帯主保護・認可)がビジネスロジックなのでテストする。

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

    private fun householdWithMember(): Triple<Household, Resident, Resident> {
        val owner = resident("おや")
        val member = resident("こ")
        val household = Household.create(HouseholdName("我が家"), owner)
            .join(member, HouseholdMemberRole.メンバー)
        return Triple(household, owner, member)
    }

    @Test
    fun join_applies_granted_role() {
        val (household, _, member) = householdWithMember()
        household.members.roleOf(member.id) shouldBe HouseholdMemberRole.メンバー
    }

    @Test
    fun owner_can_change_member_role() {
        val (household, owner, member) = householdWithMember()
        val updated = household.changeRole(member.id, HouseholdMemberRole.閲覧者, owner.id)
        updated.members.roleOf(member.id) shouldBe HouseholdMemberRole.閲覧者
    }

    @Test
    fun non_owner_cannot_change_role() {
        val (household, owner, member) = householdWithMember()
        shouldThrow<OwnerRequiredException> {
            household.changeRole(owner.id, HouseholdMemberRole.閲覧者, member.id)
        }
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
    fun removing_last_owner_is_rejected() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<LastOwnerException> { household.removeMember(owner.id, owner.id) }
    }

    @Test
    fun member_can_leave() {
        val (household, _, member) = householdWithMember()
        household.leave(member.id).members.contains(member.id) shouldBe false
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
`rename(...)` の下に以下を追加:
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

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdMembershipTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Household.join/changeRole/removeMember/leave（最後の世帯主を保護）"
```

---

## Task 13: InvitationCode VO(値域 + 生成)

値域バリデーションと `generate()`(ランダム生成が値域を満たすか)をテストする。

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

`InvitationValidity`(区分)と `Invitation`(別集約)。意味があるのは **`revoke()` で `usable()` が false になる状態遷移**のみ。それだけテストする(issue/usable/householdId の単純アクセサはテスト無し)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/invitation/Invitation.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/invitation/InvitationTest.kt`

- [ ] **Step 1: 失敗するテストを書く(状態遷移のみ)**

`InvitationTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household.invitation

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import kotlin.test.Test

class InvitationTest {
    @Test
    fun revoked_invitation_is_not_usable() {
        val invitation = Invitation.issue(HouseholdId.create(), HouseholdMemberRole.メンバー)
        invitation.usable() shouldBe true
        invitation.revoke().usable() shouldBe false
    }
}
```

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

- resident(`Resident`/`Profile`/`DisplayName`/`ResidentId`/`AuthIdentity`/`AuthProvider`/`AuthSubject`)実装、値域違反は IAE。
- household(`Household`/`Profile`/`HouseholdName`/`HouseholdId`/`HouseholdMember`/`Members`/`HouseholdMemberRole`/`HouseholdCapability`/`RolePermissions`/`OwnerChangeability`)実装。認可は区分使用、最後の世帯主保護は区分判定で `LastOwnerException`。
- invitation(`Invitation`/`InvitationCode`/`InvitationValidity`)別集約。
- 例外 4 種定義済み。
- **テストは意味のあるもの(値域・判定・抽出・状態遷移・前提崩れ)のみ**。保持/アクセサ/equals 等のテストは無い。
- `Uuid` 使用ファイルに `@file:OptIn(ExperimentalUuidApi::class)`、`enum-entry-name-case` は root `.editorconfig` で無効化。
- `./gradlew :domain:build` が緑。
