# フェーズ 5: テスト・ビルド補強 実装プラン

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development(推奨)または superpowers:executing-plans でタスク単位に実装する。ステップは checkbox(`- [ ]`)で進捗管理。

**Goal:** 残るカバレッジ空白(Service / Scenario テスト)と Controller テストのコピペ、ビルド構成の重複(integrationTest convention)・小粒の硬化項目を解消し、リファクタリング後の状態を固定する。

**Architecture:** 既存パターンを踏襲。Service/Scenario テストは `FunSpec + mockk`(Repository をスタブ)、統合テストは `@Tags("integration")`。Controller テストの session セットアップは backend:api の testFixtures に集約。integrationTest タスク定義は `kotlin-jvm` convention に統合(core は直接、api は `ktor-server` 経由で継承)。

**Tech Stack:** Kotlin / Kotest(FunSpec)/ mockk / Gradle precompiled script plugins(build-logic)

**ブランチ:** `refactor/p5-tests-and-build`(origin/main から作成済み)

---

## 方針メモ(着手前にユーザ確認したい論点)

- **5-1 の薄い Service について**: `InvitationService` / `ResidentService` / `InvitationRegisterService` / `ResidentRegisterService` は 1 行の純委譲(`repo.x()` をそのまま返すだけ)。これらの「ハッピーパス委譲テスト」は 5-4 で削除する「値保持のみの無意味テスト」と同じ低価値カテゴリになりうる。**本プランの既定方針**: ロジック(認可分岐)を持つ `HouseholdRegisterService` は手厚く、純委譲 4 サービスは「委譲が結線されている」最小 1〜2 ケースのみ(過剰に書かない)。マスタープランの「主要ハッピーパス」に沿うが、純委譲の網羅は避ける。→ **この温度感で良いか確認**。
- **5-4 の削除対象**: `ImageUrlTest.URL 文字列を保持` / `RawImageUploadTest.非空バイト列はそのまま保持` の 2 ケースを削除(値保持のみ=言語機能のテスト)。バリデーション(空拒否)ケースは残す。`ImageRef` は init が `isNotBlank()` のみで hex/長さ検証は**ない**ため、マスタープラン記載の「非 hex・長さ」境界は**該当なし**。代わりに `ImageRefTest` に空文字列 `""` ケースを追加(現状は空白 `"  "` のみ)。→ **値保持テスト削除の是非を確認**(coverage を僅かに下げる)。

---

## File Structure

**新規作成:**
- `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/household/HouseholdRegisterServiceTest.kt`
- `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/invitation/InvitationServiceTest.kt`
- `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/invitation/InvitationRegisterServiceTest.kt`
- `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/resident/ResidentServiceTest.kt`
- `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/resident/ResidentRegisterServiceTest.kt`
- `backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/invitation/RevokeInvitationScenarioTest.kt`
- `backend/api/src/testFixtures/kotlin/net/brightroom/mindstock/testfixtures/SessionFixtures.kt`

**修正:**
- 9 つの Controller テスト(session セットアップを fixture 呼び出しへ)
- `domain/.../image/ImageUrlTest.kt` / `RawImageUploadTest.kt` / `ImageRefTest.kt`
- `build-logic/src/main/kotlin/net.brightroom.mindstock.kotlin-jvm.gradle.kts`(integrationTest 集約)
- `backend/core/build.gradle.kts` / `backend/api/build.gradle.kts`(重複定義削除)
- `build-logic/src/main/kotlin/net.brightroom.mindstock.kmp-shared.gradle.kts` / `...compose-web.gradle.kts`(5-6)
- `shared/build.gradle.kts` / `frontend/build.gradle.kts`(@js-joda/timezone)
- `build-logic/settings.gradle.kts`(google フィルタ)
- 各種 5-8 軽微項目ファイル

---

## Task 1: Service テスト追加(5-1)

**Files:**
- Create: 上記 5 ファイル

`HouseholdRegisterService` は `Household` 集約の認可(`requireCapability(by, 世帯管理)`)を介するため、非世帯主メンバーでは `OwnerRequiredException`、その際に write リポジトリが呼ばれないことを検証する(委譲とガード順序の保証)。純委譲 4 サービスは結線確認の最小ケースのみ。

- [ ] **Step 1: HouseholdRegisterServiceTest を書く**

`backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/household/HouseholdRegisterServiceTest.kt`:

```kotlin
package net.brightroom.mindstock.application.service.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile

class HouseholdRegisterServiceTest :
    FunSpec({
        val residentRepository = mockk<ResidentRepository>()
        val householdRepository = mockk<HouseholdRepository>()
        val householdRegisterRepository = mockk<HouseholdRegisterRepository>(relaxed = true)
        val service =
            HouseholdRegisterService(residentRepository, householdRepository, householdRegisterRepository)

        val ownerId = ResidentId.create()
        val owner = Resident(ownerId, ResidentProfile(DisplayName("ぬし")))
        val memberId = ResidentId.create()
        val member = Resident(memberId, ResidentProfile(DisplayName("ひと")))
        val householdId = HouseholdId.create()

        fun household(vararg pairs: Pair<Resident, HouseholdMemberRole>) =
            Household(
                householdId,
                HouseholdProfile(HouseholdName("わが家")),
                Members(pairs.map { (r, role) -> HouseholdMember(r, role) }),
            )

        test("create は owner を解決して Household を登録し返す") {
            every { residentRepository.findById(ownerId) } returns owner
            val created = service.create(HouseholdName("新居"), ownerId)
            created.profile shouldBe HouseholdProfile(HouseholdName("新居"))
            verify { householdRegisterRepository.registerHousehold(created) }
        }

        test("rename は世帯主なら appendHouseholdName を呼ぶ") {
            every { householdRepository.findById(householdId) } returns household(owner to HouseholdMemberRole.世帯主)
            service.rename(householdId, HouseholdName("改名後"), ownerId)
            verify { householdRegisterRepository.appendHouseholdName(householdId, HouseholdName("改名後")) }
        }

        test("rename は非世帯主メンバーなら OwnerRequiredException で write しない") {
            every { householdRepository.findById(householdId) } returns
                household(owner to HouseholdMemberRole.世帯主, member to HouseholdMemberRole.メンバー)
            shouldThrow<OwnerRequiredException> { service.rename(householdId, HouseholdName("改名後"), memberId) }
            verify(exactly = 0) { householdRegisterRepository.appendHouseholdName(any(), any()) }
        }

        test("changeRole は非世帯主メンバーなら OwnerRequiredException で write しない") {
            every { householdRepository.findById(householdId) } returns
                household(owner to HouseholdMemberRole.世帯主, member to HouseholdMemberRole.メンバー)
            shouldThrow<OwnerRequiredException> {
                service.changeRole(householdId, ownerId, HouseholdMemberRole.メンバー, memberId)
            }
            verify(exactly = 0) { householdRegisterRepository.changeMemberRole(any(), any(), any()) }
        }

        test("removeMember は世帯主なら removeMember を呼ぶ") {
            every { householdRepository.findById(householdId) } returns
                household(owner to HouseholdMemberRole.世帯主, member to HouseholdMemberRole.メンバー)
            service.removeMember(householdId, memberId, ownerId)
            verify { householdRegisterRepository.removeMember(householdId, memberId) }
        }
    })
```

- [ ] **Step 2: 純委譲 4 サービスのテストを書く**

`InvitationServiceTest.kt`:

```kotlin
package net.brightroom.mindstock.application.service.invitation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.invitation.InvitationRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole

class InvitationServiceTest :
    FunSpec({
        val repository = mockk<InvitationRepository>()
        val service = InvitationService(repository)

        test("findByCode は repository の結果を返す") {
            val code = InvitationCode("ABCDEF")
            val invitation = Invitation.issue(HouseholdId.create(), HouseholdMemberRole.メンバー)
            every { repository.findByCode(code) } returns invitation
            service.findByCode(code) shouldBe invitation
        }
    })
```

`InvitationRegisterServiceTest.kt`:

```kotlin
package net.brightroom.mindstock.application.service.invitation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.invitation.InvitationRegisterRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole

class InvitationRegisterServiceTest :
    FunSpec({
        val repository = mockk<InvitationRegisterRepository>(relaxed = true)
        val service = InvitationRegisterService(repository)

        test("issue は repository.issue の結果を返す") {
            val invitation = Invitation.issue(HouseholdId.create(), HouseholdMemberRole.メンバー)
            every { repository.issue(invitation) } returns invitation
            service.issue(invitation) shouldBe invitation
        }

        test("revoke は repository.revoke に委譲する") {
            val code = InvitationCode("ABCDEF")
            service.revoke(code)
            verify { repository.revoke(code) }
        }
    })
```

`ResidentServiceTest.kt`:

```kotlin
package net.brightroom.mindstock.application.service.resident

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile

class ResidentServiceTest :
    FunSpec({
        val repository = mockk<ResidentRepository>()
        val service = ResidentService(repository)

        test("me は repository.findById の結果を返す") {
            val id = ResidentId.create()
            val resident = Resident(id, ResidentProfile(DisplayName("じぶん")))
            every { repository.findById(id) } returns resident
            service.me(id) shouldBe resident
        }
    })
```

`ResidentRegisterServiceTest.kt`:

```kotlin
package net.brightroom.mindstock.application.service.resident

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.resident.ResidentRegisterRepository
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile

class ResidentRegisterServiceTest :
    FunSpec({
        val repository = mockk<ResidentRegisterRepository>(relaxed = true)
        val service = ResidentRegisterService(repository)

        test("register は採番済み Resident を返す") {
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
            val displayName = DisplayName("しんき")
            val registered = Resident(ResidentId.create(), ResidentProfile(displayName))
            every { repository.registerResident(identity, displayName) } returns registered
            service.register(identity, displayName) shouldBe registered
        }

        test("rename は appendDisplayName へ委譲する") {
            val id = ResidentId.create()
            val displayName = DisplayName("あたらしい名")
            service.rename(id, displayName)
            verify { repository.appendDisplayName(id, displayName) }
        }
    })
```

- [ ] **Step 3: テスト実行**

Run: `./gradlew :backend:core:test --tests "*ServiceTest"`
Expected: PASS(既存 ProductServiceTest 等含め green)

- [ ] **Step 4: Commit**

```bash
git add backend/core/src/test/kotlin/net/brightroom/mindstock/application/service
git commit -m "test(core): application Service のユニットテストを追加(認可分岐と委譲の保証)"
```

---

## Task 2: RevokeInvitationScenario テスト(5-2)

**Files:**
- Create: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/invitation/RevokeInvitationScenarioTest.kt`

`RevokeInvitationScenario.run` は (1) code から invitation 取得 → (2) `household.requireCanManage(actor)` → (3) `revoke(code)`。非世帯主では `OwnerRequiredException`(`requireCanManage` 由来)で revoke が呼ばれないこと、世帯主では revoke されることを検証。

- [ ] **Step 1: テストを書く**

```kotlin
package net.brightroom.mindstock.application.scenario.invitation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationRegisterService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile

class RevokeInvitationScenarioTest :
    FunSpec({
        val invitationService = mockk<InvitationService>()
        val householdService = mockk<HouseholdService>()
        val invitationRegisterService = mockk<InvitationRegisterService>(relaxed = true)
        val scenario = RevokeInvitationScenario(invitationService, householdService, invitationRegisterService)

        val ownerId = ResidentId.create()
        val owner = Resident(ownerId, ResidentProfile(DisplayName("ぬし")))
        val memberId = ResidentId.create()
        val member = Resident(memberId, ResidentProfile(DisplayName("ひと")))
        val householdId = HouseholdId.create()
        val invitation = Invitation.issue(householdId, HouseholdMemberRole.メンバー)

        fun household(vararg pairs: Pair<Resident, HouseholdMemberRole>) =
            Household(
                householdId,
                HouseholdProfile(HouseholdName("わが家")),
                Members(pairs.map { (r, role) -> HouseholdMember(r, role) }),
            )

        test("世帯主は招待を失効できる") {
            every { invitationService.findByCode(invitation.code) } returns invitation
            every { householdService.findById(householdId) } returns household(owner to HouseholdMemberRole.世帯主)
            scenario.run(invitation.code, ownerId)
            verify { invitationRegisterService.revoke(invitation.code) }
        }

        test("非世帯主メンバーは失効できず OwnerRequiredException で revoke しない") {
            every { invitationService.findByCode(invitation.code) } returns invitation
            every { householdService.findById(householdId) } returns
                household(owner to HouseholdMemberRole.世帯主, member to HouseholdMemberRole.メンバー)
            shouldThrow<OwnerRequiredException> { scenario.run(invitation.code, memberId) }
            verify(exactly = 0) { invitationRegisterService.revoke(any()) }
        }
    })
```

- [ ] **Step 2: テスト実行**

Run: `./gradlew :backend:core:test --tests "*RevokeInvitationScenarioTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/invitation/RevokeInvitationScenarioTest.kt
git commit -m "test(core): RevokeInvitationScenario のテストを追加(世帯主可・メンバー不可)"
```

---

## Task 3: Controller テスト fixture 共通化(5-3)

**Files:**
- Create: `backend/api/src/testFixtures/kotlin/net/brightroom/mindstock/testfixtures/SessionFixtures.kt`
- Modify: 9 つの Controller テスト(`presentation/rpc/*/`)

backend:api は `java-test-fixtures` 有効だが testFixtures ソースは空。`MindstockSession.Registered(identity, residentId, expiry, connectionId)` のコピペ(9 箇所)を fixture に集約する。

- [ ] **Step 1: SessionFixtures を作る**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.testfixtures

import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Controller テスト用の登録済みセッション。residentId は呼び出し側で stub に使うため引数で渡せる。 */
fun buildRegisteredSession(
    residentId: ResidentId = ResidentId.create(),
    identity: AuthIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
    expiresAt: Instant = Clock.System.now().plus(1.hours),
    connectionId: Uuid = Uuid.random(),
): MindstockSession.Registered = MindstockSession.Registered(identity, residentId, expiresAt, connectionId)
```

注: `MindstockSession` は backend:api の main にあるため、fixture から参照可能(testFixtures は main をコンパイル経路に含む)。`testFixturesImplementation(projects.domain)` は宣言済み(api build.gradle.kts:36)。

- [ ] **Step 2: 9 Controller テストを fixture 呼び出しへ置換**

各テストで以下のパターンを置換する。**residentId を stub に使うテスト**(Household/Product/Stock 系):

置換前(例 HouseholdControllerTest):
```kotlin
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val residentId = ResidentId.create()
        val session =
            MindstockSession.Registered(
                identity,
                residentId,
                Clock.System.now().plus(1.hours),
                Uuid.random(),
            )
```
置換後:
```kotlin
        val residentId = ResidentId.create()
        val session = buildRegisteredSession(residentId)
```
併せて不要になった import(`AuthIdentity` / `AuthProvider` / `AuthSubject` / `MindstockSession` / `Clock` / `kotlin.time.Duration.Companion.hours` / `Uuid` / `@file:OptIn`)をテスト内の使用状況に応じて削除する。**identity を別途参照しているテスト**(例 ResidentRegisterControllerTest は Unregistered→register 経路で identity を使う場合がある)は identity を残す形 `buildRegisteredSession(residentId, identity)` にする。各ファイルを Read して実際の参照を確認してから削る。

対象 9 ファイル:
- `presentation/rpc/household/HouseholdControllerTest.kt`
- `presentation/rpc/household/HouseholdRegisterControllerTest.kt`
- `presentation/rpc/product/ProductControllerTest.kt`
- `presentation/rpc/product/ProductImageControllerTest.kt`
- `presentation/rpc/product/ProductRegisterControllerTest.kt`
- `presentation/rpc/catalog/CatalogControllerTest.kt`
- `presentation/rpc/resident/ResidentRegisterControllerTest.kt`
- `presentation/rpc/stock/StockControllerTest.kt`
- `presentation/rpc/stock/StockRegisterControllerTest.kt`

注: `configuration/guard/SessionGuardTest.kt` と `configuration/auth/MindstockAuthPluginTest.kt` は**対象外**(セッション/ガードそのものの検証で、複数の session 状態を直接組むため fixture 化すると逆に読みにくい)。

- [ ] **Step 3: テスト実行**

Run: `./gradlew :backend:api:test`
Expected: PASS(全 Controller テスト green)

- [ ] **Step 4: Commit**

```bash
git add backend/api/src/testFixtures backend/api/src/test/kotlin/net/brightroom/mindstock/presentation
git commit -m "test(api): Controller テストの登録済みセッション生成を testFixtures に集約"
```

---

## Task 4: Image VO テスト整理(5-4)

**Files:**
- Modify: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ImageUrlTest.kt`
- Modify: `.../RawImageUploadTest.kt`
- Modify: `.../ImageRefTest.kt`

- [ ] **Step 1: 値保持テストを削除し ImageRef に空文字ケース追加**

`ImageUrlTest.kt` → `URL 文字列を保持` テストを削除(`空文字は拒否` のみ残す)。`shouldBe` import が他で未使用になれば削除。

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class ImageUrlTest {
    @Test
    fun `空文字は拒否`() {
        shouldThrow<IllegalArgumentException> { ImageUrl("") }
    }
}
```

`RawImageUploadTest.kt` → `非空バイト列はそのまま保持` を削除:

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class RawImageUploadTest {
    @Test
    fun `空バイト列は拒否`() {
        shouldThrow<IllegalArgumentException> { RawImageUpload(ByteArray(0)) }
    }
}
```

`ImageRefTest.kt` → 空文字列ケースを追加(init は `isNotBlank()` のみ):

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class ImageRefTest {
    @Test
    fun 空文字列は拒否する() {
        shouldThrow<IllegalArgumentException> { ImageRef("") }
    }

    @Test
    fun 空白のみは拒否する() {
        shouldThrow<IllegalArgumentException> { ImageRef("  ") }
    }
}
```

- [ ] **Step 2: テスト実行**

Run: `./gradlew :domain:jvmTest --tests "*Image*"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image
git commit -m "test(domain): 値保持のみの Image VO テストを削除し ImageRef 境界を補強"
```

---

## Task 5: integrationTest convention 統合(5-5)

**Files:**
- Modify: `build-logic/src/main/kotlin/net.brightroom.mindstock.kotlin-jvm.gradle.kts`
- Modify: `backend/core/build.gradle.kts`(integrationTest 定義削除)
- Modify: `backend/api/build.gradle.kts`(同上)

core/api でほぼ完全コピペの `integrationTest` タスクと `tasks.test` の tags.exclude を convention へ。`System.getenv()`(configuration phase 実行=config cache 無効化)を `providers.environmentVariable()` の lazy 評価に置換。

- [ ] **Step 1: 現状の kotlin-jvm convention を確認**

Run: `cat build-logic/src/main/kotlin/net.brightroom.mindstock.kotlin-jvm.gradle.kts`
(13 行。JVM toolchain 25 のみの想定。実内容を Read してから追記)

- [ ] **Step 2: convention に test 設定と integrationTest を追加**

`net.brightroom.mindstock.kotlin-jvm.gradle.kts` に以下を追記(既存 plugins/toolchain ブロックは保持):

```kotlin
import org.gradle.api.tasks.testing.Test

// 通常 test は integration/manual タグを除外(全モジュール共通)。
tasks.named<Test>("test") {
    systemProperty("kotest.tags.exclude", "integration | manual")
}

// @Tags("integration") のみを実行する統合テストタスク(core/api 共通の受け皿)。
// 外部依存(DB / Garage)に当てるためキャッシュさせず毎回実行する。
// 環境変数は providers.environmentVariable で lazy 評価し configuration cache を壊さない。
val integrationTest = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs @Tags(\"integration\") specs against live external dependencies (TEST_DB_* / STORAGE_*)."
    doNotTrackState("integration tests run against live external dependencies")
    val testSourceSet = sourceSets["test"]
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    systemProperty("kotest.tags.include", "integration")
    systemProperty("kotest.tags.exclude", "manual")
    listOf(
        "TEST_DB_URL", "TEST_DB_USER", "TEST_DB_PASSWORD",
        "STORAGE_ENDPOINT", "STORAGE_BUCKET", "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY",
    ).forEach { key ->
        providers.environmentVariable(key).orNull?.let { environment(key, it) }
    }
}
```

注意点:
- `import org.gradle.api.tasks.testing.Test` と `sourceSets` / `tasks` は precompiled script plugin の DSL で利用可。`sourceSets` は `kotlin-jvm` が適用する `java`/`kotlin` プラグイン経由で存在する。実際にコンパイルが通るか Step 4 で確認。
- `providers.environmentVariable(key).orNull` も configuration phase で値を読むが、`getenv` と違い provider 経由のため config cache 入力として正しく追跡される(無効化ではなく invalidation キーになる)。`environment(key, ...)` の呼び出し自体は configuration time に解決して良い(Test タスクの environment は execution 前に確定が必要)。

- [ ] **Step 3: core/api の重複定義を削除**

`backend/core/build.gradle.kts` から `tasks.test { ... }`(34-38)と `val integrationTest by tasks.registering(Test::class) { ... }`(40-56)ブロックを削除。`exposed { ... }` ブロックは残す。

`backend/api/build.gradle.kts` から `tasks.test { ... }`(59-63)と `val integrationTest by tasks.registering(Test::class) { ... }`(65-82)ブロックを削除。さらに api 直下にコメントを 1 行追加:

```kotlin
// 注: api の integrationTest は現状 @Tags("integration") のテストが 0 件で空実行(将来の e2e 受け皿)。
// タスク定義は kotlin-jvm convention(ktor-server 経由で継承)に集約済み。
```

- [ ] **Step 4: ビルドと統合テストの起動確認**

Run: `./gradlew :backend:core:test :backend:api:test`
Expected: PASS(通常テストが convention の tags.exclude で従来通り integration を除外)

Run: `./gradlew :backend:core:integrationTest --dry-run` および `:backend:api:integrationTest --dry-run`
Expected: タスクが両モジュールで解決される(convention 由来)

Run(DB ありの環境のみ): `mise run up` 後 `./gradlew :backend:core:integrationTest`
Expected: PASS(DataSource 統合テスト green)。DB なし環境ではこのステップはスキップ可(CI / ローカル DB で確認)。

- [ ] **Step 5: Commit**

```bash
git add build-logic/src/main/kotlin/net.brightroom.mindstock.kotlin-jvm.gradle.kts backend/core/build.gradle.kts backend/api/build.gradle.kts
git commit -m "build: integrationTest タスクを kotlin-jvm convention に集約し env を provider 化"
```

---

## Task 6: Gradle 構成の硬化(5-6)

**Files:**
- Modify: `build-logic/src/main/kotlin/net.brightroom.mindstock.kmp-shared.gradle.kts` / `...compose-web.gradle.kts`(webMain 階層明示)
- Modify: `shared/build.gradle.kts` / `frontend/build.gradle.kts`(@js-joda/timezone 集約)
- Modify: `build-logic/settings.gradle.kts`(google フィルタ)
- Modify: `backend/core/src/.../ProductImageTransferTest.kt`(region を env 参照へ)

各サブ項目は独立。Read で現物を確認しながら進める。

- [ ] **Step 1: webMain ソースセット階層の明示**

`kmp-shared` / `compose-web` convention の KMP ブロックで `applyDefaultHierarchyTemplate()` を明示呼び出し(現在は暗黙)。現物を Read し、既に `applyDefaultHierarchyTemplate()` があれば no-op として skip。なければ `kotlin { ... }` 内に追記し、`webMain` が `jsMain`/`wasmJsMain` の親として階層化されることをコメントで明示。

- [ ] **Step 2: @js-joda/timezone を webMain に集約**

`shared/build.gradle.kts`(14-19)の `jsMain` / `wasmJsMain` 個別宣言を `webMain.dependencies { implementation(npm("@js-joda/timezone", "2.3.0")) }` に統合(webMain が両者の親の場合)。`frontend/build.gradle.kts:35` の commonMain 側 `@js-joda/timezone` 重複宣言は、shared に推移するなら削除。**要否を実コンパイルで確認**: frontend が timezone を直接使うか(`kotlinx.datetime` の TZ DB 解決)。削除して `compileKotlinWasmJs` が通れば不要、落ちれば残す。

- [ ] **Step 3: build-logic settings の google() フィルタ**

`build-logic/settings.gradle.kts:9` の `google()` を、ルート `settings.gradle.kts` と同じ `includeGroupAndSubgroups("androidx")` / `"com.google"` / `"com.android")` フィルタ付きに変更(ルートの記法を Read してコピー)。

- [ ] **Step 4: ProductImageTransferTest の region を env 参照へ**

`ProductImageTransferTest.kt:32` の `region = "garage"` を `System.getenv("STORAGE_REGION") ?: "garage"` に変更(他の STORAGE_* env と整合)。

- [ ] **Step 5: ビルド確認**

Run: `./gradlew build -x :frontend:wasmJsBrowserDistribution -x test`(コンパイル経路のみ。frontend は OOM 回避のため `:frontend:compileKotlinWasmJs` を別途)
Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add build-logic shared/build.gradle.kts frontend/build.gradle.kts backend/core
git commit -m "build: webMain 階層明示 / js-joda 重複解消 / google フィルタ / region env 化"
```

---

## Task 7: 軽微な残項目(5-8)

**Files(各独立・まとめて 1 コミット):**
- `backend/api/.../configuration/external/storage/StorageProperties.kt`
- `backend/api/.../StorageConfiguration.kt`
- `frontend/.../ProductImageLoader.kt`
- `frontend/.../atom/RoundBtn.kt`
- `frontend/.../SettingsViewModel.kt`
- `backend/api/.../DependenciesConfiguration.kt`(RoutingConfiguration 解決)

- [ ] **Step 1: StorageProperties.corsAllowedOrigins を List 化**

`corsAllowedOrigins: String`(CSV)を `List<String>` に。HOCON list として読めるよう `@SerialName("cors-allowed-origins") val corsAllowedOrigins: List<String>` に変更し、`application.yaml` の該当値も list 表記へ。利用側(`StorageConfiguration` の CORS PUT で origins を渡す箇所)の `.split(",")` を除去。**現物 Read 必須**(yaml の現在の値形式と利用箇所)。

- [ ] **Step 2: 起動時 CORS PUT に withTimeout**

`StorageConfiguration.kt:43` の `runBlocking { s3.putBucketCors(...) }` を `runBlocking { withTimeout(10.seconds) { s3.putBucketCors(...) } }` に(起動が外部ストレージ不達で無限ブロックしないように)。import 追加(`kotlinx.coroutines.withTimeout` / `kotlin.time.Duration.Companion.seconds`)。

- [ ] **Step 3: rememberProductImage を internal 化**

`ProductImageLoader.kt:100` の `fun rememberProductImage(...)` を `internal fun` に(集約後の利用は `rememberProductThumbnail` 経由が正。外部公開不要)。`rememberProductThumbnail` から呼べることを確認。**他モジュール参照がないこと**を grep 確認。

- [ ] **Step 4: RoundBtn の KDoc 実態化**

`RoundBtn.kt:17` の KDoc を実用途(現状 Stepper の ± 専用なら「数量ステッパーの増減ボタン」等)に修正。**実呼び出し元を grep して実態に合わせる**。

- [ ] **Step 5: SettingsViewModel の remember キー差に意図コメント**

`SettingsViewModel.kt` の `combine(...).stateIn(...)` 付近、もしくは residentId をキーにした remember 差分に、なぜそのキーかの 1 行コメントを追加。**現物 Read で該当箇所を特定**。

- [ ] **Step 6: RoutingConfiguration の Service 先取り解決整理**

`DependenciesConfiguration.kt`(InvitationRegisterService の解決パターン不整合)を Read し、他の Scenario 依存解決と記法を揃える(`resolve()` 経由 / コンストラクタ注入の一貫性)。**整理であり挙動変更なし**。

- [ ] **Step 7: ビルド確認**

Run: `./gradlew :backend:api:compileKotlin :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: 軽微な硬化(CORS list/timeout / 可視性 / KDoc / DI 解決の整理)"
```

---

## 最終検証

- [ ] **Step 1: 全テスト**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: フル build(DB なしで通ること)**

Run: `./gradlew build -x :frontend:wasmJsBrowserDistribution`
Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 統合テスト(ローカル DB / CI)**

Run: `mise run up` 後 `./gradlew integrationTest`
Expected: PASS(convention 集約後も両モジュールで起動)

- [ ] **Step 4: 削除シンボルの残参照ゼロ**

Run: `grep -rn "URL 文字列を保持\|非空バイト列はそのまま保持" domain/ ; grep -rn "by tasks.registering(Test" backend/`
Expected: 残参照なし(test 名は削除済み、integrationTest 定義は convention のみ)

- [ ] **Step 5: PR**

superpowers:finishing-a-development-branch で PR 作成。本文に「全フェーズ(R/0/D/1〜5)完了」を記載。

---

## Self-Review チェック

- **Spec coverage**: 5-1(Task1)/ 5-2(Task2)/ 5-3(Task3)/ 5-4(Task4)/ 5-5(Task5)/ 5-6(Task6)/ 5-8(Task7)。マスタープランに 5-7 は欠番。全項目に対応タスクあり。
- **型整合**: `buildRegisteredSession` の引数順(residentId, identity, expiresAt, connectionId)は Task3 で定義し同タスク内で使用。`MindstockSession.Registered(identity, residentId, expiry, connectionId)` の実順と対応。
- **placeholder**: Task6/7 の一部は「現物 Read で確認」を含むが、これは file:line ドリフトと yaml 形式差への対応で、各ステップに具体的な変更内容(何を何へ)を明記済み。
