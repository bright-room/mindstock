# P6-3b 設定タブ刷新 + 世帯管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 設定タブを実機能化し、世帯切替・メンバー権限変更/除外・招待発行/失効・世帯名変更・表示名 rename・退出を配線する。

**Architecture:** P6-3a 確立パターン踏襲。feature VM(`SettingsViewModel`)が RPC + エラー処理を持ち、`AuthFlow`(=`AuthViewModel`)が `AppSession` 変更 + `AuthState` 遷移を coordinator として握る。Repository は service opener を遅延注入。世帯切替は WS 再接続不要(active 世帯はクライアント状態)。招待は明示発行式(get RPC が無いため発行済み invite は VM メモリのみ)。

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform (Wasm) / kotlinx-rpc / androidx.lifecycle.ViewModel / kotlin.test + Kotest assertions。

**Spec:** `docs/superpowers/specs/2026-06-07-p6-3b-settings-household-management-design.md`

**実装方針:** インライン実装+最後にまとめてビルド([[subagent-vs-inline-frontend]])。各 Task のテストは commonTest(`@Test` + Kotest)。UI 描画は網羅しない。ビルド検証は全 Task 完了後に `./gradlew test` + `./gradlew build`(WasmJs を除く)で一括(`local-build-tips`)。

**規約リマインダ:**
- commonTest は Kotest FunSpec 不可。`kotlin.test.@Test` + `io.kotest.matchers.*`(`frontend-kmp-test-style`)。
- feature 層は `androidx.compose.material3.*` を直接 import しない。`designsystem/atom/` 経由(`frontend-designsystem`)。
- UI 文言は `commonMain/composeResources/values/strings.xml`(ja)に置き `stringResource` で参照(`frontend-i18n-and-font`)。
- 公開 API の nullable 戻り値は原則禁止(`error-handling`)。
- コミットメッセージに issue/PR 番号を書かない(`commit-message-no-issue-refs`)。コミット末尾に `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

---

## ファイル構成

**新規作成:**
- `frontend/src/commonMain/.../feature/household/SettingsUiState.kt` — 設定画面 state(data class 群)
- `frontend/src/commonMain/.../feature/household/SettingsViewModel.kt` — 設定操作 VM
- `frontend/src/commonMain/.../feature/household/ui/HouseholdSwitcher.kt` — 世帯切替シート
- `frontend/src/commonMain/.../feature/household/ui/MemberSheet.kt` — メンバー権限変更/除外シート
- `frontend/src/commonMain/.../feature/household/ui/InviteSheet.kt` — 招待発行/失効シート(明示発行式)
- `frontend/src/commonMain/.../designsystem/atom/Toggle.kt` — on/off スイッチ atom(disabled 対応)
- `frontend/src/commonMain/.../app/settings/SettingsScreen.kt` — 設定画面合成(現 ProfileScreen 刷新)
- `frontend/src/commonTest/.../feature/household/SettingsViewModelTest.kt`
- `frontend/src/commonTest/.../app/AuthViewModelSwitchTest.kt`(coordinator 追加分。既存 AuthViewModelTest と分離)

**変更:**
- `frontend/src/commonMain/.../feature/household/data/HouseholdRepository.kt` — 6 メソッド追加
- `frontend/src/commonMain/.../feature/resident/data/ResidentRepository.kt` — rename 追加
- `frontend/src/commonMain/.../core/session/AppSession.kt` — `setDisplayName` 追加
- `frontend/src/commonMain/.../app/AuthFlow.kt` — 4 メソッド追加
- `frontend/src/commonMain/.../app/AuthViewModel.kt`(`AuthDeps` interface + `AuthViewModel`)— 4 coordinator 実装 + 4 deps メソッド
- `frontend/src/webMain/.../WebAuthDeps.kt` — 4 deps メソッド実装
- `frontend/src/commonMain/.../designsystem/atom/AppIcon.kt` — `AppIconName` に Crown/Swap/Copy/Eye/Trash 追加
- `frontend/src/commonMain/.../feature/household/ui/RoleLabels.kt` — ロールアイコンマッピング追加
- `frontend/src/commonMain/composeResources/values/strings.xml` — 文言追加
- `frontend/src/webMain/.../App.kt` — `profileContent` 差し替え + SettingsViewModel 配線 + 切替シートの create/join 合流
- `frontend/src/commonMain/.../app/profile/ProfileScreen.kt` — 削除(SettingsScreen へ移設)

**削除前提:** 既存 `AuthViewModelTest` がある場合、追加メソッドで壊さないこと(Task 8 で確認)。

---

## Task 1: HouseholdRepository に 6 メソッド追加

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/data/HouseholdRepository.kt`

Repository は薄い委譲のため単体テストは置かない(P6-3a の HouseholdRepository も create/join はテスト無し、preview のみ既存)。実装のみ。

- [ ] **Step 1: 6 メソッドを追加**

`HouseholdRepository` クラス本体(`previewInvite` の下)に追加。先頭の import も追加:

```kotlin
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
```

```kotlin
    suspend fun rename(
        householdId: HouseholdId,
        name: HouseholdName,
    ): RpcOutcome<Unit> = householdRegisterService().rename(householdId, name).toOutcome()

    suspend fun leave(householdId: HouseholdId): RpcOutcome<Unit> = householdRegisterService().leave(householdId).toOutcome()

    suspend fun changeRole(
        householdId: HouseholdId,
        target: ResidentId,
        role: HouseholdMemberRole,
    ): RpcOutcome<Unit> = householdRegisterService().changeRole(householdId, target, role).toOutcome()

    suspend fun removeMember(
        householdId: HouseholdId,
        target: ResidentId,
    ): RpcOutcome<Unit> = householdRegisterService().removeMember(householdId, target).toOutcome()

    suspend fun createInvite(
        householdId: HouseholdId,
        role: HouseholdMemberRole,
    ): RpcOutcome<Invitation> = householdRegisterService().createInvite(householdId, role).toOutcome()

    suspend fun revokeInvite(code: InvitationCode): RpcOutcome<Unit> = householdRegisterService().revokeInvite(code).toOutcome()
```

クラス KDoc の「P6-3b で … 追加予定」の一文は削除。

- [ ] **Step 2: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/data/HouseholdRepository.kt
git commit -m "feat(frontend): HouseholdRepository に世帯管理 RPC を追加"
```

---

## Task 2: ResidentRepository に rename 追加

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/resident/data/ResidentRepository.kt`

- [ ] **Step 1: rename を追加**

`register` の下に追加:

```kotlin
    suspend fun rename(displayName: DisplayName): RpcOutcome<Unit> = residentRegisterService().rename(displayName).toOutcome()
```

- [ ] **Step 2: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/resident/data/ResidentRepository.kt
git commit -m "feat(frontend): ResidentRepository に表示名 rename を追加"
```

---

## Task 3: AppSession に setDisplayName 追加

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/session/AppSession.kt`

- [ ] **Step 1: setDisplayName を追加**

`setResident` の下に追加:

```kotlin
    fun setDisplayName(displayName: DisplayName) = _state.update { it.copy(displayName = displayName) }
```

- [ ] **Step 2: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/session/AppSession.kt
git commit -m "feat(frontend): AppSession に setDisplayName を追加"
```

---

## Task 4: AuthFlow / AuthDeps interface 拡張

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthFlow.kt`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt`(`AuthDeps` interface 部)

interface のみ拡張(実装は Task 5)。この時点ではコンパイルが通らない(実装未追加)ので、Task 5 とセットでビルド確認する。

- [ ] **Step 1: AuthFlow に 4 メソッド追加**

`AuthFlow.kt` の import に追加:

```kotlin
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
```

interface 本体(`needHousehold()` の下)に追加:

```kotlin
    /** アクティブ世帯を切り替える(WS 再接続なし)。session 反映 + 永続化。 */
    fun switchActiveHousehold(id: HouseholdId)

    /** 世帯一覧を再ロードし、現在のアクティブを維持して session 反映。失敗時 throw。 */
    suspend fun refreshHouseholds()

    /** 表示名を session に反映する(resident rename は Unit 戻りのため VM が DisplayName を渡す)。 */
    fun applyDisplayName(name: DisplayName)

    /** アクティブ世帯から退出した後の再ロード + アクティブ再選択。ゼロなら NeedHousehold。失敗時 throw。 */
    suspend fun leaveActiveHousehold()
```

- [ ] **Step 2: AuthDeps に 4 メソッド追加**

`AuthViewModel.kt` の `AuthDeps` interface(`savedActiveHousehold(): HouseholdId?` の下)に追加。import に `DisplayName` を追加:

```kotlin
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
```

```kotlin
    /** アクティブ世帯のみ session 反映(切替用・一覧は変えない)。 */
    fun setActiveHousehold(id: HouseholdId)

    /** 表示名のみ session 反映。 */
    fun setDisplayName(name: DisplayName)

    /** 現在のアクティブ世帯。無ければ null。 */
    fun currentActiveHousehold(): HouseholdId?

    /** 世帯ゼロを session 反映(active=null)。退出で全世帯を失った時に使う。 */
    fun onHouseholdsCleared(households: Households)
```

`currentActiveHousehold(): HouseholdId?` は nullable 戻りだが、「現在のアクティブが無い(=世帯ゼロ)」という不在を表す内部 deps メソッドであり、`savedActiveHousehold(): HouseholdId?` と同じ既存パターン(P6-3a で承認済の nullable)に倣う。新規の nullable 公開 API ではない。

- [ ] **Step 3: コミット**(Task 5 と同時ビルドのためここでは commit のみ)

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthFlow.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt
git commit -m "feat(frontend): AuthFlow/AuthDeps に世帯切替・refresh・退出を宣言"
```

---

## Task 5: AuthViewModel coordinator 実装 + テスト

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelSwitchTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

既存 `AuthViewModelTest`(あれば)で使われている fake `AuthDeps` の作り方を確認し、同じ流儀で fake を用意する。既存 fake が他テストファイルに `internal` で居る場合はそれを拡張、無ければ本ファイルにローカル fake を定義する。以下は自己完結の fake を本ファイルに置く例:

```kotlin
package net.brightroom.mindstock.frontend.app

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile
import net.brightroom.mindstock.frontend.auth.Tokens
import net.brightroom.mindstock.frontend.core.auth.AuthState
import net.brightroom.mindstock.rpc.session.SessionStatus
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AuthViewModelSwitchTest {
    private fun resident(name: String = "わたし") =
        Resident(ResidentId(Uuid.random()), ResidentProfile(DisplayName(name)))

    private fun household(
        id: HouseholdId,
        name: String,
        me: Resident,
        role: HouseholdMemberRole = HouseholdMemberRole.世帯主,
    ) = Household(id, Profile(HouseholdName(name)), Members(listOf(HouseholdMember(me, role))))

    /** テスト用 deps。loadHouseholds で返す一覧を差し替えられる。 */
    private class FakeDeps(
        var households: Households,
        var active: HouseholdId?,
    ) : AuthDeps {
        val persisted = mutableListOf<HouseholdId>()
        var displayName: DisplayName? = null
        var clearedTo: Households? = null

        override fun currentPath() = "/"
        override suspend fun handleCallback() = Unit
        override fun loadValidToken(): Tokens? = Tokens("a", "r", 3600)
        override suspend fun redirectToAuthorize() = Unit
        override suspend fun fetchSessionStatus(token: Tokens): SessionStatus = error("unused")
        override fun onAuthenticated(resident: Resident) = Unit
        override suspend fun loadHouseholds(): Households = households
        override fun onHouseholdsLoaded(households: Households, active: HouseholdId) {
            this.households = households
            this.active = active
        }
        override suspend fun reconnect(token: Tokens) = Unit
        override fun persistActiveHousehold(id: HouseholdId) { persisted += id; active = id }
        override fun savedActiveHousehold(): HouseholdId? = active
        override fun setActiveHousehold(id: HouseholdId) { active = id }
        override fun setDisplayName(name: DisplayName) { displayName = name }
        override fun currentActiveHousehold(): HouseholdId? = active
        override fun onHouseholdsCleared(households: Households) { clearedTo = households; active = null }
    }

    @Test
    fun switchActiveHousehold_sets_and_persists() {
        val me = resident()
        val h1 = household(HouseholdId(Uuid.random()), "家1", me)
        val h2 = household(HouseholdId(Uuid.random()), "家2", me)
        val deps = FakeDeps(Households(listOf(h1, h2)), active = h1.id)
        val vm = AuthViewModel(deps)

        vm.switchActiveHousehold(h2.id)

        deps.active shouldBe h2.id
        deps.persisted shouldBe listOf(h2.id)
    }

    @Test
    fun applyDisplayName_updates_session() {
        val me = resident()
        val h1 = household(HouseholdId(Uuid.random()), "家1", me)
        val deps = FakeDeps(Households(listOf(h1)), active = h1.id)
        val vm = AuthViewModel(deps)

        vm.applyDisplayName(DisplayName("あたらしい"))

        deps.displayName shouldBe DisplayName("あたらしい")
    }

    @Test
    fun leaveActiveHousehold_picks_remaining_when_active_gone() = runTest {
        val me = resident()
        val h1 = household(HouseholdId(Uuid.random()), "家1", me)
        val h2 = household(HouseholdId(Uuid.random()), "家2", me)
        // 退出後の一覧は h2 のみ(h1 が active だった)
        val deps = FakeDeps(Households(listOf(h2)), active = h1.id)
        val vm = AuthViewModel(deps)

        vm.leaveActiveHousehold()

        deps.active shouldBe h2.id
        deps.persisted shouldBe listOf(h2.id)
    }

    @Test
    fun leaveActiveHousehold_goes_need_household_when_empty() = runTest {
        val me = resident()
        val h1 = household(HouseholdId(Uuid.random()), "家1", me)
        val deps = FakeDeps(Households(emptyList()), active = h1.id)
        val vm = AuthViewModel(deps)

        vm.leaveActiveHousehold()

        deps.clearedTo shouldBe Households(emptyList())
        vm.state.value.shouldBeInstanceOf<AuthState.NeedHousehold>()
    }

    @Test
    fun refreshHouseholds_keeps_current_active() = runTest {
        val me = resident()
        val h1 = household(HouseholdId(Uuid.random()), "家1", me)
        val renamed = household(h1.id, "家1改", me)
        val deps = FakeDeps(Households(listOf(renamed)), active = h1.id)
        val vm = AuthViewModel(deps)

        vm.refreshHouseholds()

        deps.active shouldBe h1.id
        deps.households shouldBe Households(listOf(renamed))
    }
}
```

注: `AuthState.NeedHousehold` が object か class かでアサーション形が変わる。`object` なら `vm.state.value shouldBe AuthState.NeedHousehold`。実際の `AuthState` を確認して合わせる(P6-3a 実装に存在)。`Tokens` のコンストラクタ引数も実型に合わせる(`loadValidToken` が返せる形なら何でもよい)。

- [ ] **Step 2: テストを実行して失敗を確認**

Run: `./gradlew :frontend:compileTestKotlinJvm` または `./gradlew :frontend:jvmTest --tests "*AuthViewModelSwitchTest*"`
Expected: コンパイルエラー(未実装メソッド)。

- [ ] **Step 3: AuthViewModel に coordinator を実装**

`AuthViewModel` クラス(`AuthFlow` 実装)の `needHousehold()` の下に追加。import に `DisplayName` を追加:

```kotlin
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
```

```kotlin
    override fun switchActiveHousehold(id: HouseholdId) {
        deps.setActiveHousehold(id)
        deps.persistActiveHousehold(id)
    }

    override suspend fun refreshHouseholds() {
        val households = deps.loadHouseholds()
        val active = deps.currentActiveHousehold() ?: households.list.firstOrNull()?.id
        if (active == null) {
            deps.onHouseholdsCleared(households)
            _state.value = AuthState.NeedHousehold
        } else {
            deps.onHouseholdsLoaded(households, active)
        }
    }

    override fun applyDisplayName(name: DisplayName) {
        deps.setDisplayName(name)
    }

    override suspend fun leaveActiveHousehold() {
        val households = deps.loadHouseholds()
        val current = deps.currentActiveHousehold()
        val active =
            households.list.firstOrNull { it.id == current }?.id
                ?: households.list.firstOrNull()?.id
        if (active == null) {
            deps.onHouseholdsCleared(households)
            _state.value = AuthState.NeedHousehold
        } else {
            deps.onHouseholdsLoaded(households, active)
            deps.persistActiveHousehold(active)
        }
    }
```

- [ ] **Step 4: テストを実行して通過を確認**

Run: `./gradlew :frontend:jvmTest --tests "*AuthViewModelSwitchTest*"`
Expected: PASS（5 件）。

- [ ] **Step 5: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelSwitchTest.kt
git commit -m "feat(frontend): AuthViewModel に世帯切替・refresh・退出 coordinator を実装"
```

---

## Task 6: WebAuthDeps に 4 メソッド実装

**Files:**
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/WebAuthDeps.kt`

webMain は wasmJs/js 専用で commonTest から触れないため実装のみ(`./gradlew :frontend:compileKotlinWasmJs` でコンパイル確認は Task 12 の一括ビルドで行う)。

- [ ] **Step 1: 4 メソッドを実装**

import に追加:

```kotlin
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
```

`savedActiveHousehold()` の下に追加:

```kotlin
    override fun setActiveHousehold(id: HouseholdId) {
        session.setActiveHousehold(id)
    }

    override fun setDisplayName(name: DisplayName) {
        session.setDisplayName(name)
    }

    override fun currentActiveHousehold(): HouseholdId? = session.state.value.activeHouseholdId

    override fun onHouseholdsCleared(households: Households) {
        session.setHouseholds(households, null)
    }
```

- [ ] **Step 2: コミット**

```bash
git add frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/WebAuthDeps.kt
git commit -m "feat(frontend): WebAuthDeps に世帯切替・表示名・クリアを実装"
```

---

## Task 7: SettingsUiState 定義

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/SettingsUiState.kt`

- [ ] **Step 1: state 型を定義**

```kotlin
package net.brightroom.mindstock.frontend.feature.household

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

/** 設定画面の表示状態。AppSession 由来 + 発行済み invite(VM メモリ)+ submitting。 */
data class SettingsUiState(
    val displayName: String = "",
    val households: List<HouseholdSummary> = emptyList(),
    val activeId: HouseholdId? = null,
    val activeName: String = "",
    val members: List<MemberRow> = emptyList(),
    val isOwner: Boolean = false,
    val issuedInvite: Invitation? = null,
    val submitting: Boolean = false,
)

/** 切替シート 1 行ぶん。 */
data class HouseholdSummary(
    val id: HouseholdId,
    val name: String,
    val memberCount: Int,
    val myRole: HouseholdMemberRole,
    val active: Boolean,
)

/** メンバー行 1 件ぶん。 */
data class MemberRow(
    val residentId: ResidentId,
    val name: String,
    val role: HouseholdMemberRole,
    val isMe: Boolean,
)
```

`issuedInvite: Invitation?` / `activeId: HouseholdId?` は UI state の「不在」表現(発行前/世帯ゼロ)であり、nullable 禁止の対象は「公開 API の戻り値・引数」。data class のフィールドは UI state の表現として許容(既存 UiState も同様の null フィールドを持つ。例: `DetailTarget?`)。

- [ ] **Step 2: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/SettingsUiState.kt
git commit -m "feat(frontend): SettingsUiState を追加"
```

---

## Task 8: SettingsViewModel 実装 + テスト

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/SettingsViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/household/SettingsViewModelTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`ToastController` / `ReauthController` の実 API は P6-3a 実装に存在。`toast.show(UiText)` / `reauth.request()` 想定。実際のシグネチャを確認して合わせる(例: `toast.current` を後で読む形なら、テストは「show が呼ばれたか」を確認できる薄い検証にする)。ここでは fake suspend lambda と fake AuthFlow で「成功時に AuthFlow の正しいメソッドが呼ばれる / Conflict 時に呼ばれない」を検証する。

```kotlin
package net.brightroom.mindstock.frontend.feature.household

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.session.AppSession
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SettingsViewModelTest {
    private val me = Resident(ResidentId(Uuid.random()), ResidentProfile(DisplayName("わたし")))
    private val other = Resident(ResidentId(Uuid.random()), ResidentProfile(DisplayName("ほか")))
    private val hid = HouseholdId(Uuid.random())

    private fun ownerHousehold() =
        Household(
            hid,
            Profile(HouseholdName("我が家")),
            Members(
                listOf(
                    HouseholdMember(me, HouseholdMemberRole.世帯主),
                    HouseholdMember(other, HouseholdMemberRole.メンバー),
                ),
            ),
        )

    private fun session(): AppSession =
        AppSession().apply {
            setResident(me.id, me.profile.displayName)
            setHouseholds(Households(listOf(ownerHousehold())), hid)
        }

    private class RecordingFlow : AuthFlow {
        var refreshed = false
        var renamedDisplay: DisplayName? = null
        var switched: HouseholdId? = null
        var left = false
        override suspend fun onResidentRegistered(resident: Resident) = Unit
        override suspend fun enterApp(activeId: HouseholdId) = Unit
        override fun needHousehold() = Unit
        override fun switchActiveHousehold(id: HouseholdId) { switched = id }
        override suspend fun refreshHouseholds() { refreshed = true }
        override fun applyDisplayName(name: DisplayName) { renamedDisplay = name }
        override suspend fun leaveActiveHousehold() { left = true }
    }

    private fun vm(
        flow: AuthFlow,
        rename: suspend (DisplayName) -> RpcOutcome<Unit> = { RpcOutcome.Success(Unit) },
        renameHh: suspend (HouseholdId, HouseholdName) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
        changeRole: suspend (HouseholdId, ResidentId, HouseholdMemberRole) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
        remove: suspend (HouseholdId, ResidentId) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
        leave: suspend (HouseholdId) -> RpcOutcome<Unit> = { RpcOutcome.Success(Unit) },
        createInvite: suspend (HouseholdId, HouseholdMemberRole) -> RpcOutcome<Invitation> = { h, r -> RpcOutcome.Success(Invitation.issue(h, r)) },
        revokeInvite: suspend (net.brightroom.mindstock.domain.model.household.invitation.InvitationCode) -> RpcOutcome<Unit> = { RpcOutcome.Success(Unit) },
    ) = SettingsViewModel(
        session = session(),
        renameDisplayNameRpc = rename,
        renameHouseholdRpc = renameHh,
        changeRoleRpc = changeRole,
        removeMemberRpc = remove,
        leaveRpc = leave,
        createInviteRpc = createInvite,
        revokeInviteRpc = revokeInvite,
        flow = flow,
        toast = ToastController(),
        reauth = ReauthController(),
    )

    @Test
    fun state_derives_owner_and_members_from_session() {
        val state = vm(RecordingFlow()).state.value
        state.isOwner.shouldBeTrue()
        state.activeName shouldBe "我が家"
        state.members.size shouldBe 2
        state.displayName shouldBe "わたし"
    }

    @Test
    fun renameDisplayName_success_applies_to_flow() = runTest {
        val flow = RecordingFlow()
        vm(flow).renameDisplayName(DisplayName("あたらしい"))
        flow.renamedDisplay shouldBe DisplayName("あたらしい")
    }

    @Test
    fun renameHousehold_success_refreshes() = runTest {
        val flow = RecordingFlow()
        vm(flow).renameHousehold(HouseholdName("新居"))
        flow.refreshed.shouldBeTrue()
    }

    @Test
    fun changeRole_success_refreshes() = runTest {
        val flow = RecordingFlow()
        vm(flow).changeRole(other.id, HouseholdMemberRole.閲覧者)
        flow.refreshed.shouldBeTrue()
    }

    @Test
    fun removeMember_success_refreshes() = runTest {
        val flow = RecordingFlow()
        vm(flow).removeMember(other.id)
        flow.refreshed.shouldBeTrue()
    }

    @Test
    fun leave_success_calls_flow_leave() = runTest {
        val flow = RecordingFlow()
        vm(flow).leave()
        flow.left.shouldBeTrue()
    }

    @Test
    fun leave_conflict_does_not_call_flow_leave() = runTest {
        val flow = RecordingFlow()
        vm(flow, leave = { RpcOutcome.Failure(RpcError.Conflict("last owner cannot leave")) }).leave()
        flow.left shouldBe false
    }

    @Test
    fun createInvite_success_stores_in_state() = runTest {
        val sut = vm(RecordingFlow())
        sut.createInvite(HouseholdMemberRole.メンバー)
        (sut.state.value.issuedInvite != null).shouldBeTrue()
    }

    @Test
    fun revokeInvite_success_clears_state() = runTest {
        val sut = vm(RecordingFlow())
        sut.createInvite(HouseholdMemberRole.メンバー)
        sut.revokeInvite()
        (sut.state.value.issuedInvite == null).shouldBeTrue()
    }

    @Test
    fun switchHousehold_delegates_to_flow() {
        val flow = RecordingFlow()
        vm(flow).switchHousehold(hid)
        flow.switched shouldBe hid
    }
}
```

注: `RpcOutcome` の variant 名(`Success`/`Failure`)・`ToastController`/`ReauthController` のコンストラクタ・`Invitation.issue` の可用性を実コードで確認し合わせる(`Invitation.issue` は domain companion に存在)。`InvitationCode` の import 行が冗長なら整理。

- [ ] **Step 2: テストを実行して失敗を確認**

Run: `./gradlew :frontend:jvmTest --tests "*SettingsViewModelTest*"`
Expected: コンパイルエラー（SettingsViewModel 未定義）。

- [ ] **Step 3: SettingsViewModel を実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.app.isOwner
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcError
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.session.AppSession
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.settings_error_last_owner_change_role
import mindstock.frontend.generated.resources.settings_error_last_owner_leave
import mindstock.frontend.generated.resources.settings_error_last_owner_remove

class SettingsViewModel(
    session: AppSession,
    private val renameDisplayNameRpc: suspend (DisplayName) -> RpcOutcome<Unit>,
    private val renameHouseholdRpc: suspend (HouseholdId, HouseholdName) -> RpcOutcome<Unit>,
    private val changeRoleRpc: suspend (HouseholdId, ResidentId, HouseholdMemberRole) -> RpcOutcome<Unit>,
    private val removeMemberRpc: suspend (HouseholdId, ResidentId) -> RpcOutcome<Unit>,
    private val leaveRpc: suspend (HouseholdId) -> RpcOutcome<Unit>,
    private val createInviteRpc: suspend (HouseholdId, HouseholdMemberRole) -> RpcOutcome<Invitation>,
    private val revokeInviteRpc: suspend (InvitationCode) -> RpcOutcome<Unit>,
    private val flow: AuthFlow,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    // 発行済み invite と submitting は VM ローカル(session に属さない)。
    private val local = MutableStateFlow(LocalState())

    private data class LocalState(
        val issuedInvite: Invitation? = null,
        val submitting: Boolean = false,
    )

    val state: StateFlow<SettingsUiState> =
        combine(session.state, local) { s, l ->
            val activeId = s.activeHouseholdId
            val household = s.households?.list?.firstOrNull { it.id == activeId }
            val owner = isOwner(s.households, activeId, s.residentId)
            SettingsUiState(
                displayName = s.displayName?.invoke() ?: "",
                households =
                    s.households?.list.orEmpty().map { h ->
                        HouseholdSummary(
                            id = h.id,
                            name = h.profile.name.invoke(),
                            memberCount = h.members.size(),
                            myRole = s.residentId?.let { rid ->
                                if (h.members.contains(rid)) h.members.roleOf(rid) else HouseholdMemberRole.閲覧者
                            } ?: HouseholdMemberRole.閲覧者,
                            active = h.id == activeId,
                        )
                    },
                activeId = activeId,
                activeName = household?.profile?.name?.invoke() ?: "",
                members =
                    household?.members?.list.orEmpty().map { m ->
                        MemberRow(
                            residentId = m.resident.id,
                            name = m.resident.profile.displayName.invoke(),
                            role = m.role,
                            isMe = m.resident.id == s.residentId,
                        )
                    },
                isOwner = owner,
                issuedInvite = l.issuedInvite,
                submitting = l.submitting,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    private fun activeId(): HouseholdId? = state.value.activeId

    suspend fun renameDisplayName(name: DisplayName) =
        run(renameDisplayNameRpc(name)) { flow.applyDisplayName(name) }

    suspend fun renameHousehold(name: HouseholdName) {
        val id = activeId() ?: return
        runRefreshing(renameHouseholdRpc(id, name))
    }

    suspend fun changeRole(target: ResidentId, role: HouseholdMemberRole) {
        val id = activeId() ?: return
        runRefreshing(changeRoleRpc(id, target, role), lastOwner = Res.string.settings_error_last_owner_change_role)
    }

    suspend fun removeMember(target: ResidentId) {
        val id = activeId() ?: return
        runRefreshing(removeMemberRpc(id, target), lastOwner = Res.string.settings_error_last_owner_remove)
    }

    suspend fun leave() {
        val id = activeId() ?: return
        when (val r = leaveRpc(id)) {
            is RpcOutcome.Success -> safe { flow.leaveActiveHousehold() }
            is RpcOutcome.Failure -> failWith(r.error, Res.string.settings_error_last_owner_leave)
        }
    }

    suspend fun createInvite(role: HouseholdMemberRole) {
        val id = activeId() ?: return
        when (val r = createInviteRpc(id, role)) {
            is RpcOutcome.Success -> local.value = local.value.copy(issuedInvite = r.value)
            is RpcOutcome.Failure -> failWith(r.error, null)
        }
    }

    suspend fun revokeInvite() {
        val invite = local.value.issuedInvite ?: return
        when (val r = revokeInviteRpc(invite.code)) {
            is RpcOutcome.Success -> local.value = local.value.copy(issuedInvite = null)
            is RpcOutcome.Failure -> failWith(r.error, null)
        }
    }

    fun switchHousehold(id: HouseholdId) {
        flow.switchActiveHousehold(id)
    }

    // --- helpers ---

    private suspend fun run(outcome: RpcOutcome<Unit>, onSuccess: () -> Unit) {
        when (outcome) {
            is RpcOutcome.Success -> onSuccess()
            is RpcOutcome.Failure -> failWith(outcome.error, null)
        }
    }

    private suspend fun runRefreshing(
        outcome: RpcOutcome<Unit>,
        lastOwner: org.jetbrains.compose.resources.StringResource? = null,
    ) {
        when (outcome) {
            is RpcOutcome.Success -> safe { flow.refreshHouseholds() }
            is RpcOutcome.Failure -> failWith(outcome.error, lastOwner)
        }
    }

    /** Conflict かつ lastOwner 文言が指定されていれば専用文言、それ以外は errorText / reauth。 */
    private fun failWith(
        error: RpcError,
        lastOwner: org.jetbrains.compose.resources.StringResource?,
    ) {
        if (error.requiresReauth()) {
            reauth.request()
            return
        }
        if (error is RpcError.Conflict && lastOwner != null) {
            toast.show(UiText(lastOwner))
            return
        }
        toast.show(errorText(error))
    }

    /** AuthFlow 呼び出しの通信失敗を toast に倒す(Cancellation は再 throw)。 */
    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            toast.show(errorText(RpcError.Internal))
        }
    }
}
```

注: `safe` は `suspend` ブロックを呼ぶので `inline fun` ではなく通常の `private suspend fun safe(block: suspend () -> Unit)` にする必要がある。実装時 `inline` を外し `suspend` 化すること。`toast.show` / `UiText` / `RpcError.Internal` の実シグネチャに合わせる(`errorText(RpcError.Internal)` が引数を要するなら `RpcError.Internal` の実コンストラクタを確認)。`reauth.request()` の実メソッド名も確認(`ReauthController` の API)。

- [ ] **Step 4: テストを実行して通過を確認**

Run: `./gradlew :frontend:jvmTest --tests "*SettingsViewModelTest*"`
Expected: PASS（10 件）。失敗したら `RpcOutcome`/`RpcError`/`ToastController` の実 API にテストと実装を合わせる。

- [ ] **Step 5: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/SettingsViewModel.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/household/SettingsViewModelTest.kt
git commit -m "feat(frontend): SettingsViewModel を追加(世帯管理操作)"
```

---

## Task 9: strings.xml に文言追加

**Files:**
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: 文言を追加**

`<resources>` 内に追加(既存キーと重複しないこと。`settings_*` prefix で統一)。Task 8 で参照済の 3 キー(`settings_error_last_owner_*`)を必ず含める。

```xml
    <!-- 設定タブ -->
    <string name="settings_title">設定</string>
    <string name="settings_eyebrow">アカウントと世帯</string>
    <string name="settings_section_household">世帯</string>
    <string name="settings_section_preferences">環境設定</string>
    <string name="settings_section_other">その他</string>
    <string name="settings_account_provider">Zitadel でログイン中</string>
    <string name="settings_display_name_edit">表示名を変更</string>
    <string name="settings_household_rename">世帯名を変更</string>
    <string name="settings_household_member_count">%1$d人で共有</string>
    <string name="settings_switch">切り替え</string>
    <string name="settings_invite_owner_only">家族の招待はオーナー(%1$s)が行えます。</string>
    <string name="settings_master_entry">商品マスタを編集</string>
    <string name="settings_master_entry_sub">画像・単位・最低在庫の設定／商品のアーカイブ</string>
    <string name="settings_owner_badge">オーナー</string>
    <string name="settings_pref_push">在庫減少のお知らせ</string>
    <string name="settings_pref_push_sub">Web Push で端末に通知</string>
    <string name="settings_pref_offline">オフラインで在庫を見る</string>
    <string name="settings_pref_offline_sub">読み取りのみ・書き込みは要オンライン</string>
    <string name="settings_badge_future">将来対応予定</string>
    <string name="settings_badge_soon">近日</string>
    <string name="settings_other_trend">消費の傾向</string>
    <string name="settings_other_archived">アーカイブした商品</string>
    <string name="settings_leave">この世帯から退出</string>
    <string name="settings_logout">ログアウト</string>
    <string name="settings_footer">mindstock · MVP プレビュー</string>

    <!-- 世帯切替シート -->
    <string name="switcher_title">世帯を切り替え</string>
    <string name="switcher_desc">参加している世帯から選んで切り替えられます。いくつでも持てます。</string>
    <string name="switcher_create">新しい世帯をつくる</string>
    <string name="switcher_create_sub">空の在庫から始めます</string>
    <string name="switcher_join">招待コードで参加</string>
    <string name="switcher_join_sub">家族や同居人の世帯に加わる</string>

    <!-- メンバーシート -->
    <string name="member_title">メンバー</string>
    <string name="member_you">あなた</string>
    <string name="member_role_label">権限</string>
    <string name="member_owner_note">世帯のオーナーです。すべての在庫とメンバーを管理できます。</string>
    <string name="member_viewer_note">権限の変更やメンバーの削除はオーナーのみ行えます。</string>
    <string name="member_remove">世帯から外す</string>
    <string name="member_remove_confirm">%1$s さんを世帯から外しますか? 共有はすぐに解除されます。</string>
    <string name="member_remove_cancel">やめる</string>
    <string name="member_remove_do">外す</string>

    <!-- 招待シート(明示発行式) -->
    <string name="invite_title">家族を招待</string>
    <string name="invite_desc">「%1$s」に招待して、在庫を一緒に管理しましょう。コードを送るだけです。</string>
    <string name="invite_role_label">参加したときの権限</string>
    <string name="invite_role_member">編集できる</string>
    <string name="invite_role_viewer">閲覧のみ</string>
    <string name="invite_none">まだ招待コードがありません</string>
    <string name="invite_issue">招待コードを発行</string>
    <string name="invite_reissue">新しいコード</string>
    <string name="invite_revoke">失効する</string>
    <string name="invite_copy">コードをコピー</string>
    <string name="invite_copied">コピーしました</string>
    <string name="invite_reusable">何度でも使えます</string>

    <!-- 退出確認 -->
    <string name="leave_confirm">この世帯から退出しますか? 在庫の共有が解除されます。</string>
    <string name="leave_cancel">やめる</string>
    <string name="leave_do">退出する</string>

    <!-- 成功トースト -->
    <string name="settings_toast_renamed">変更しました</string>
    <string name="settings_toast_member_removed">メンバーを外しました</string>
    <string name="settings_toast_left">世帯から退出しました</string>
    <string name="settings_toast_invite_issued">招待コードを発行しました</string>
    <string name="settings_toast_invite_revoked">招待コードを失効しました</string>

    <!-- last-owner エラー(backend reason の技術英語を出さない) -->
    <string name="settings_error_last_owner_leave">最後のオーナーは退出できません。先に別のメンバーをオーナーにしてください。</string>
    <string name="settings_error_last_owner_remove">最後のオーナーは外せません。</string>
    <string name="settings_error_last_owner_change_role">最後のオーナーの権限は変更できません。</string>
```

注: `member_remove_confirm` / `settings_household_member_count` / `settings_invite_owner_only` / `invite_desc` はプレースホルダ(`%1$s` / `%1$d`)を持つ。`stringResource(Res.string.xxx, arg)` で渡す。

- [ ] **Step 2: 重複キー確認**

Run: `grep -c 'name="settings_master_entry"' frontend/src/commonMain/composeResources/values/strings.xml`
Expected: `1`(既存 `profile_master_entry` とは別キー。既存と重複していれば名前を調整)。既存 `profile_*` キーは Task 11 で SettingsScreen に統合後、未使用なら残置でよい(削除は任意)。

- [ ] **Step 3: コミット**

```bash
git add frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(frontend): 設定タブ・世帯管理の文言を追加"
```

---

## Task 10: designsystem 拡張(AppIcon / Toggle / RoleLabels)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppIcon.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/Toggle.kt`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/RoleLabels.kt`

- [ ] **Step 1: AppIconName に追加**

`AppIcon.kt` の enum に追加: `Crown`, `Swap`, `Copy`, `Eye`, `Trash`。import と `vector()` の when に対応を追加:

```kotlin
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WorkspacePremium
```

```kotlin
        AppIconName.Crown -> Icons.Outlined.WorkspacePremium
        AppIconName.Swap -> Icons.Outlined.SwapHoriz
        AppIconName.Copy -> Icons.Outlined.ContentCopy
        AppIconName.Eye -> Icons.Outlined.Visibility
        AppIconName.Trash -> Icons.Outlined.DeleteOutline
```

注: `WorkspacePremium` / `SwapHoriz` 等が `material-icons-extended` に存在することは概ね確実だが、コンパイル時に未解決なら代替(`Crown`→`Star`、`Swap`→`CompareArrows`、`Eye`→`RemoveRedEye`)に差し替える。

- [ ] **Step 2: Toggle atom を作成**

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** on/off スイッチ。material3 Switch を 1 枚噛ませて feature から material3 を隠す。 */
@Composable
fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
    )
}
```

- [ ] **Step 3: RoleLabels にアイコンマッピングを追加**

`RoleLabels.kt` に追加(import `AppIconName`):

```kotlin
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName

/** 区分を UI アイコンに対応づける(オーナー=crown / メンバー=pencil / 閲覧者=eye)。 */
fun roleIcon(role: HouseholdMemberRole): AppIconName =
    when (role) {
        HouseholdMemberRole.世帯主 -> AppIconName.Crown
        HouseholdMemberRole.メンバー -> AppIconName.Pencil
        HouseholdMemberRole.閲覧者 -> AppIconName.Eye
    }
```

- [ ] **Step 4: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppIcon.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/Toggle.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/RoleLabels.kt
git commit -m "feat(frontend): 設定タブ用アイコン/Toggle/ロールアイコンを追加"
```

---

## Task 11: シート UI 3 種(HouseholdSwitcher / MemberSheet / InviteSheet)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/HouseholdSwitcher.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/MemberSheet.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/InviteSheet.kt`

UI は描画テストしない(`frontend-kmp-test-style`)。実装のみ。**見た目はモックに忠実化**(`frontend-visual-fidelity-expectation`):
- `HouseholdSwitcher` → `/tmp` 等で展開したモック `app/screens-household.jsx:24-92`(`HouseholdSwitcher` / `SwitcherAction`)。
- `MemberSheet` → `app/screens-invite.jsx:194-257`(`MemberSheet`)。
- `InviteSheet` → `app/screens-invite.jsx:75-192` を **明示発行式に簡略化**(QR/リンク/validity 表示なし。発行ボタン → コード + コピー + 新しいコード + 失効)。

各シートは既存 atom(`Sheet` / `SegmentedControl` / `PrimaryButton` / `AppIcon` / `AppText` / `TextInput`)と foundation レイアウト + `LocalMindstockTokens` で構成。`Sheet` の引数(`open` / `onClose` / `title` 等)は `designsystem/atom/Sheet.kt:19` のシグネチャに合わせる。`SegmentedControl` は `SegmentedControl.kt:28` のシグネチャに合わせる(既存 JoinCodeSheet の使い方が参考)。

- [ ] **Step 1: HouseholdSwitcher を作成**

state-hoisting。一覧と選択/作成/参加のコールバックを引数で受ける。

```kotlin
@Composable
fun HouseholdSwitcher(
    open: Boolean,
    households: List<HouseholdSummary>,
    onClose: () -> Unit,
    onChoose: (HouseholdId) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
)
```

内容(モック準拠): `Sheet(open, onClose, title=switcher_title)` 内に説明文 → 世帯行リスト(各行: home アイコン枠・名前・「N人」+ 自分のロール(`roleIcon`/`roleLabelResource`)・active ならチェック)→ `SwitcherAction` 相当 2 行(`switcher_create` / `switcher_join`)。行タップで `onChoose(id)`。

- [ ] **Step 2: MemberSheet を作成**

```kotlin
@Composable
fun MemberSheet(
    open: Boolean,
    member: MemberRow?,
    isOwnerSelf: Boolean,
    onClose: () -> Unit,
    onChangeRole: (ResidentId, HouseholdMemberRole) -> Unit,
    onRemove: (ResidentId) -> Unit,
)
```

内容(モック準拠): アバター頭文字・名前・(自分なら `member_you` バッジ)・ロール。分岐:
- `member.role == 世帯主` → `member_owner_note`(crown)。
- `isOwnerSelf && !member.isMe` → ロール seg(`invite_role_member`=メンバー / `invite_role_viewer`=閲覧者)→ `onChangeRole(member.residentId, role)`。「世帯から外す」→ ローカル `remember { confirm }` で確認 UI(`member_remove_confirm` + やめる/外す)→ `onRemove(member.residentId)`。
- それ以外 → `member_viewer_note`。

`member` が null の間は何も描かない(`open=false` 相当)。

- [ ] **Step 3: InviteSheet を作成(明示発行式)**

```kotlin
@Composable
fun InviteSheet(
    open: Boolean,
    householdName: String,
    issuedInvite: Invitation?,
    onClose: () -> Unit,
    onIssue: (HouseholdMemberRole) -> Unit,
    onRevoke: () -> Unit,
)
```

内容: 説明(`invite_desc` with householdName)→ 参加ロール seg(ローカル `remember { role: HouseholdMemberRole }`、初期 `メンバー`)→
- `issuedInvite == null` → `invite_none` + `PrimaryButton(invite_issue)` → `onIssue(role)`。
- `issuedInvite != null` → コード表示(`issuedInvite.code.invoke()` をモノスペース中央・コピーは clipboard。コピーは `LocalClipboardManager` を app/atom 経由で。直接 material3 不可のため、コピーは `androidx.compose.ui.platform.LocalClipboardManager`(material3 ではないので feature で可)を使う)→ `invite_reusable` → `invite_reissue`(→ `onIssue(role)`)+ `invite_revoke`(→ `onRevoke()`)。

注: ロール seg を変えてから再発行すると role が変わる。`onIssue` は常に現在の seg 値を渡す。

- [ ] **Step 4: 一括コンパイル確認(JVM)**

Run: `./gradlew :frontend:compileKotlinJvm`
Expected: BUILD SUCCESSFUL（型エラーがあればシグネチャを実 atom に合わせて修正）。

- [ ] **Step 5: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/HouseholdSwitcher.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/MemberSheet.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/InviteSheet.kt
git commit -m "feat(frontend): 世帯切替/メンバー/招待シートを追加"
```

---

## Task 12: SettingsScreen 合成 + ProfileScreen 削除

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/settings/SettingsScreen.kt`
- Delete: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/profile/ProfileScreen.kt`

UI 合成。state + コールバックを受け、内部でシートの open 状態を `remember` で持つ。モック `app/screens-d.jsx:74-240`(`Profile`)に忠実化。

- [ ] **Step 1: SettingsScreen を作成**

```kotlin
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onRenameDisplayName: (DisplayName) -> Unit,
    onRenameHousehold: (HouseholdName) -> Unit,
    onChoose: (HouseholdId) -> Unit,
    onChangeRole: (ResidentId, HouseholdMemberRole) -> Unit,
    onRemoveMember: (ResidentId) -> Unit,
    onLeave: () -> Unit,
    onIssueInvite: (HouseholdMemberRole) -> Unit,
    onRevokeInvite: () -> Unit,
    onOpenMaster: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenSwitcher: () -> Unit,    // 切替シート(create/join を含む)は App.kt 側で開くため hoist
    onLogout: () -> Unit,
    switcherOpen: Boolean,
    onCloseSwitcher: () -> Unit,
    modifier: Modifier = Modifier,
)
```

構成(モック準拠・縦スクロール):
1. ヘッダ(`settings_eyebrow` / `settings_title`)。
2. アカウントカード: アバター頭文字(`state.displayName` の先頭1文字)・表示名 + 鉛筆(ローカル編集 `remember`、確定で `onRenameDisplayName(DisplayName(input))`)・`settings_account_provider`。
3. 世帯セクション(`settings_section_household`):
   - `state.activeId == null` → `NoHouseholdCard`(P6-3a 既存。create/join は App.kt で `onOpenSwitcher` 経由ではなく、ここでは簡易に `onOpenSwitcher()` を呼んで切替シートの create/join に誘導してよい)。通常はここに来ない(boot で NeedHousehold)。
   - else 世帯カード: 世帯名 + 鉛筆(`state.isOwner` のみ。確定 `onRenameHousehold(HouseholdName(input))`)・`settings_household_member_count`(`state.members.size`)・「切り替え」ピル(`onOpenSwitcher`)。メンバー行(`state.members` を回し、行タップでローカル `memberSel = it`)。招待: `state.isOwner` なら「家族を招待」→ ローカル `inviteOpen=true`、非 owner は `settings_invite_owner_only`(owner 名は `state.members.firstOrNull { it.role==世帯主 }?.name`)。退出: `settings_leave`(確認付き → `onLeave()`)。
4. `state.isOwner` なら「商品マスタを編集」(`onOpenMaster`、`settings_owner_badge`)。
5. 環境設定(`settings_section_preferences`): `Toggle(enabled=false)` の行を 2 つ(push/offline、`settings_badge_future`)。状態は固定 false。
6. その他(`settings_section_other`): 消費の傾向(`settings_badge_soon`・無効)、アーカイブ(`onOpenArchived`)。
7. `PrimaryButton`/ghost で `settings_logout`(`onLogout`)。フッタ `settings_footer`。

シート: 画面内に `MemberSheet`(`memberSel`/`state.isOwner`/`onChangeRole`/`onRemoveMember`)と `InviteSheet`(`inviteOpen`/`state.activeName`/`state.issuedInvite`/`onIssueInvite`/`onRevokeInvite`)を配置。`HouseholdSwitcher` は **App.kt 側**(create/join シートと同居させるため)で `switcherOpen`/`onCloseSwitcher`/`onChoose` を使って描画する。

- [ ] **Step 2: ProfileScreen を削除**

```bash
git rm frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/profile/ProfileScreen.kt
```

`app/profile/` ディレクトリが空になる。空ディレクトリは git 管理外なので放置でよい。

- [ ] **Step 3: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/settings/SettingsScreen.kt
git commit -m "feat(frontend): SettingsScreen を追加し ProfileScreen を置換"
```

---

## Task 13: App.kt 配線

**Files:**
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`

- [ ] **Step 1: SettingsViewModel を remember し profileContent を差し替え**

`Ready` 分岐の `homeVm`/`shopVm`/`activityVm` の近くに `settingsVm` を追加:

```kotlin
val settingsVm =
    remember(householdId, sessionState.residentId) {
        SettingsViewModel(
            session = session,
            renameDisplayNameRpc = residentRepository::rename,
            renameHouseholdRpc = householdRepository::rename,
            changeRoleRpc = householdRepository::changeRole,
            removeMemberRpc = householdRepository::removeMember,
            leaveRpc = householdRepository::leave,
            createInviteRpc = householdRepository::createInvite,
            revokeInviteRpc = householdRepository::revokeInvite,
            flow = vm,
            toast = toast,
            reauth = reauth,
        )
    }
```

切替シートの create/join は P6-3a の `NeedHouseholdViewModel` を再利用する。`Ready` 分岐内に設定用の sheet 状態と VM を `remember`:

```kotlin
var settingsSheet by remember { mutableStateOf<SettingsSheet?>(null) }   // Switcher/Create/Join
val settingsHhVm =
    remember(householdId) {
        NeedHouseholdViewModel(
            createHousehold = householdRepository::create,
            previewInvite = householdRepository::previewInvite,
            joinByCode = householdRepository::join,
            flow = vm,                 // 成功で flow.enterApp(h.id) → 新世帯が active
            toast = toast,
            reauth = reauth,
        )
    }
val settingsHhState by settingsHhVm.state.collectAsState()
```

`profileContent` を差し替え:

```kotlin
profileContent = {
    val sState by settingsVm.state.collectAsState()
    SettingsScreen(
        state = sState,
        onRenameDisplayName = { scope.launch { settingsVm.renameDisplayName(it) } },
        onRenameHousehold = { scope.launch { settingsVm.renameHousehold(it) } },
        onChoose = { id ->
            settingsVm.switchHousehold(id)
            settingsSheet = null
        },
        onChangeRole = { t, r -> scope.launch { settingsVm.changeRole(t, r) } },
        onRemoveMember = { scope.launch { settingsVm.removeMember(it) } },
        onLeave = { scope.launch { settingsVm.leave() } },
        onIssueInvite = { scope.launch { settingsVm.createInvite(it) } },
        onRevokeInvite = { scope.launch { settingsVm.revokeInvite() } },
        onOpenMaster = { catalogOverlay = CatalogOverlay.Master },
        onOpenArchived = { catalogOverlay = CatalogOverlay.Archived },
        onOpenSwitcher = { settingsSheet = SettingsSheet.Switcher },
        onLogout = { reauth.request() },
        switcherOpen = settingsSheet == SettingsSheet.Switcher,
        onCloseSwitcher = { settingsSheet = null },
    )
}
```

- [ ] **Step 2: 切替シート(Switcher/Create/Join)を Ready 分岐の overlay として描画**

`AppShell(...)` 呼び出しの直後(ProductDetailOverlay や catalogOverlay と同階層)に追加:

```kotlin
HouseholdSwitcher(
    open = settingsSheet == SettingsSheet.Switcher,
    households = settingsVm.state.value.households,
    onClose = { settingsSheet = null },
    onChoose = { id ->
        settingsVm.switchHousehold(id)
        settingsSheet = null
    },
    onCreate = { settingsSheet = SettingsSheet.Create },
    onJoin = {
        settingsHhVm.clearPreview()
        settingsSheet = SettingsSheet.Join
    },
)
CreateHouseholdSheet(
    open = settingsSheet == SettingsSheet.Create,
    busy = settingsHhState.busy,
    onClose = { settingsSheet = null },
    onCreate = { name -> scope.launch { settingsHhVm.create(name) } },
)
JoinCodeSheet(
    open = settingsSheet == SettingsSheet.Join,
    state = settingsHhState,
    onClose = {
        settingsSheet = null
        settingsHhVm.clearPreview()
    },
    onCodeChange = { code ->
        if (code.length == 6) scope.launch { settingsHhVm.preview(code) } else settingsHhVm.clearPreview()
    },
    onJoin = { code -> scope.launch { settingsHhVm.join(code) } },
)
```

注: `settingsHhVm.create`/`join` 成功は `flow.enterApp(h.id)` で AuthState を Ready 再設定 + 新世帯 active。成功後にシートを閉じる必要があるので、`NeedHouseholdViewModel` の state に done フラグがあればそれを `LaunchedEffect` で監視して `settingsSheet = null`。無ければ P6-3a の挙動(enterApp で Ready 再構成)で十分なら、`LaunchedEffect(state)` 不要。実装時に P6-3a の done 検知方法を踏襲。

- [ ] **Step 3: import 追加と enum 定義**

App.kt に追加 import:

```kotlin
import net.brightroom.mindstock.frontend.app.settings.SettingsScreen
import net.brightroom.mindstock.frontend.feature.household.SettingsViewModel
import net.brightroom.mindstock.frontend.feature.household.ui.HouseholdSwitcher
```

ファイル末尾の `private enum class NeedHouseholdSheet` の近くに追加:

```kotlin
private enum class SettingsSheet { Switcher, Create, Join }
```

旧 `import ...app.profile.ProfileScreen` と `import ...app.isOwner`(profileContent で使っていた `owner` は他でも使うため残す)を整理。`ProfileScreen` import は削除。

- [ ] **Step 4: コミット**

```bash
git add frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt
git commit -m "feat(frontend): 設定タブに SettingsScreen と世帯管理を配線"
```

---

## Task 14: 一括ビルド + テスト検証

**Files:** なし(検証のみ)

- [ ] **Step 1: 全テスト実行**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL。`SettingsViewModelTest`(10)・`AuthViewModelSwitchTest`(5)・既存テストが全通過。失敗したら該当 Task に戻る。

- [ ] **Step 2: フルビルド(WasmJs を除く)**

Run: `./gradlew build -x :frontend:wasmJsBrowserDistribution -x wasmJsTest`（`local-build-tips`: WasmJs はフルビルドで OOM るため除外。具体的な除外タスク名は `local-build-tips` の記載に合わせる)
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: WasmJs コンパイル確認(OOM 回避のため compile のみ)**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL（webMain の WebAuthDeps / App.kt が wasmJs でコンパイルできること）。

- [ ] **Step 4: 受け入れ条件の自己確認**

spec の受け入れ条件 1-10 を読み返し、コードがカバーしているか確認(UI 実機確認はユーザに委ねる旨を最終報告に記載)。

- [ ] **Step 5: 最終コミット(あれば微修正をまとめて)**

```bash
git add -A
git commit -m "chore(frontend): P6-3b ビルド通過の微修正" || echo "no changes"
```

---

## Self-Review(計画作成者による点検結果)

**Spec coverage:**
- 設定タブ刷新 → Task 12(SettingsScreen)+ Task 13(配線)。
- 世帯切替シート → Task 11(HouseholdSwitcher)+ Task 5/6(switchActiveHousehold)+ Task 13。
- メンバー権限変更/除外 → Task 1(repo)+ Task 8(VM)+ Task 11(MemberSheet)。
- 招待発行/失効(明示発行式)→ Task 1 + Task 8 + Task 11(InviteSheet)。
- 世帯名変更 → Task 1 + Task 8 + Task 12。
- 住人 rename → Task 2 + Task 8(applyDisplayName)+ Task 5。
- 退出 → Task 1 + Task 5(leaveActiveHousehold)+ Task 8 + Task 12。
- last-owner エラー文言 → Task 8 + Task 9。
- 世帯切替に再接続不要 → Task 5(switchActiveHousehold が reconnect を呼ばない)で担保。
- 招待 get RPC 無し制約 → Task 8(issuedInvite は VM ローカル)で担保。

**Type consistency:** `RpcOutcome.Success`/`Failure`・`RpcError.Conflict`/`Internal`・`AuthFlow` の 7 メソッド・`SettingsUiState`/`HouseholdSummary`/`MemberRow` のフィールド名は Task 7/8 と Task 11/12/13 で一致。`createInviteRpc` の戻り `Invitation`、`switchHousehold(HouseholdId)` は一貫。

**実装時に実 API へ要すり合わせ(コメントで明記済):**
- `RpcOutcome` の variant 名(`Success`/`Failure`)— 実コードで確認。
- `ToastController.show` / `ReauthController.request` / `UiText` / `RpcError.Internal` の実シグネチャ。
- `AuthState.NeedHousehold` が object/class か(テストアサーション形)。
- `Tokens` コンストラクタ・`SegmentedControl`/`Sheet` の引数。
- `material-icons-extended` のアイコン可用性(未解決なら代替名)。
- `SettingsViewModel.safe` は `suspend` ブロックを呼ぶため `inline` を外し `suspend fun` 化。

これらは「実装時に既存コードを見て合わせる」種類の差分で、設計上の不確定要素ではない。
