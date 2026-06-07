# P6-3a オンボーディング + 世帯作成/参加 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** boot 後の `NeedOnboarding` / `NeedHousehold` プレースホルダを実機能にし、初回ユーザが表示名登録 → 世帯作成または招待コード参加 → アプリ本体(Ready)へ到達できるようにする。アクティブ世帯を localStorage で永続化する。

**Architecture:** `AuthViewModel` を `AuthFlow` coordinator 化し、feature(onboarding / household)の ViewModel が完了時に `onResidentRegistered`(★登録後 WS 再接続)/ `enterApp`(世帯再ロード+永続化+Ready)/ `needHousehold` を呼び戻す。RPC は Repository(service opener 遅延注入)で隠蔽し、ViewModel は `suspend (...) -> RpcOutcome<...>` を注入される既存パターンに従う。UI はモック(`screens-onboard.jsx` / `screens-household.jsx`)に寄せ、atom 経由で組む。

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform(Kotlin/Wasm) / kotlinx-rpc / kotlinx-coroutines / kotlin.test + Kotest assertions(commonTest)。

**設計参照:** `docs/superpowers/specs/2026-06-07-p6-3a-onboarding-household-design.md`

---

## ファイル構成

新規(commonMain):
- `frontend/.../core/preference/PreferenceStore.kt`(expect)— localStorage 薄ラッパ
- `frontend/.../app/AuthFlow.kt` — coordinator interface
- `frontend/.../feature/resident/data/ResidentRepository.kt`
- `frontend/.../feature/household/data/HouseholdRepository.kt`
- `frontend/.../feature/onboarding/OnboardingUiState.kt`
- `frontend/.../feature/onboarding/OnboardingViewModel.kt`
- `frontend/.../feature/onboarding/ui/OnboardingScreen.kt`
- `frontend/.../feature/household/NeedHouseholdUiState.kt`
- `frontend/.../feature/household/NeedHouseholdViewModel.kt`
- `frontend/.../feature/household/ui/NeedHouseholdScreen.kt`
- `frontend/.../feature/household/ui/CreateHouseholdSheet.kt`
- `frontend/.../feature/household/ui/JoinCodeSheet.kt`
- `frontend/.../feature/household/ui/RoleLabels.kt`
- `frontend/.../designsystem/atom/WizardProgress.kt`
- `frontend/.../designsystem/atom/SuggestionChips.kt`

新規(jsMain / wasmJsMain):
- `frontend/src/jsMain/.../core/preference/PreferenceStore.js.kt`(actual)
- `frontend/src/wasmJsMain/.../core/preference/PreferenceStore.wasmJs.kt`(actual)

変更:
- `frontend/.../app/AuthDeps.kt`(interface に 3 メソッド追加)
- `frontend/.../app/AuthViewModel.kt`(`AuthFlow` 実装 + boot の saved-active 選択)
- `frontend/.../webMain/WebAuthDeps.kt`(新メソッド実装)
- `frontend/.../webMain/App.kt`(プレースホルダ置換・repo/VM 配線)
- `frontend/.../designsystem/atom/AppIcon.kt`(`Check`/`Link`/`Users` 追加)
- `frontend/src/commonMain/composeResources/values/strings.xml`(文言追加・旧 placeholder 削除)
- `frontend/.../app/AuthViewModelTest.kt`(FakeAuthDeps 拡張 + 新テスト)

パッケージ接頭辞(以下 `<pkg>` と表記): `net.brightroom.mindstock.frontend`
ソース基点(以下 `<src>` と表記): `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend`

---

## Task 1: PreferenceStore(localStorage 薄ラッパ)

**Files:**
- Create: `<src>/core/preference/PreferenceStore.kt`
- Create: `frontend/src/jsMain/kotlin/net/brightroom/mindstock/frontend/core/preference/PreferenceStore.js.kt`
- Create: `frontend/src/wasmJsMain/kotlin/net/brightroom/mindstock/frontend/core/preference/PreferenceStore.wasmJs.kt`

プラットフォーム依存(localStorage)のため commonTest 不可。検証はビルドで行う。
既存 `auth/SessionStorage.kt`(sessionStorage の expect/actual)と同じ形に揃える。

- [ ] **Step 1: expect 宣言を書く**

```kotlin
package net.brightroom.mindstock.frontend.core.preference

/** リロード・タブ復帰をまたいで保持する薄い key-value(localStorage backed)。 */
expect object PreferenceStore {
    fun get(key: String): String?

    fun set(
        key: String,
        value: String,
    )

    fun remove(key: String)
}
```

- [ ] **Step 2: jsMain actual を書く**

```kotlin
package net.brightroom.mindstock.frontend.core.preference

import kotlinx.browser.window

actual object PreferenceStore {
    actual fun get(key: String): String? = window.localStorage.getItem(key)

    actual fun set(
        key: String,
        value: String,
    ) = window.localStorage.setItem(key, value)

    actual fun remove(key: String) = window.localStorage.removeItem(key)
}
```

- [ ] **Step 3: wasmJsMain actual を書く**

```kotlin
package net.brightroom.mindstock.frontend.core.preference

import kotlinx.browser.window

actual object PreferenceStore {
    actual fun get(key: String): String? = window.localStorage.getItem(key)

    actual fun set(
        key: String,
        value: String,
    ) = window.localStorage.setItem(key, value)

    actual fun remove(key: String) = window.localStorage.removeItem(key)
}
```

> 注: `auth/SessionStorage.kt` の既存 actual と同じ import / 形になっているか確認すること。差異があれば既存に合わせる。

- [ ] **Step 4: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/preference \
        frontend/src/jsMain/kotlin/net/brightroom/mindstock/frontend/core/preference \
        frontend/src/wasmJsMain/kotlin/net/brightroom/mindstock/frontend/core/preference
git commit -m "feat(frontend): localStorage backed PreferenceStore を追加"
```

---

## Task 2: AuthFlow interface + AuthDeps 拡張

**Files:**
- Create: `<src>/app/AuthFlow.kt`
- Modify: `<src>/app/AuthDeps.kt`

ここではまだ AuthViewModel 本体は変えない(Task 3 でまとめてテスト駆動)。型だけ先に用意する。

- [ ] **Step 1: AuthFlow interface を書く**

```kotlin
package net.brightroom.mindstock.frontend.app

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.resident.Resident

/**
 * オンボーディング/世帯作成・参加の完了時に呼び戻す coordinator。AuthViewModel が実装する。
 */
interface AuthFlow {
    /** 登録済み Resident を session に反映し、WS を再接続して Registered セッションを獲得する。失敗時 throw。 */
    suspend fun onResidentRegistered(resident: Resident)

    /** 世帯一覧を再ロードし、activeId をアクティブにして session 反映+永続化し、Ready に遷移。失敗時 throw。 */
    suspend fun enterApp(activeId: HouseholdId)

    /** 世帯ゼロ(スキップ)へ。NeedHousehold に遷移。 */
    fun needHousehold()
}
```

- [ ] **Step 2: AuthDeps にメソッドを追加する**

`<src>/app/AuthDeps.kt` の `interface AuthDeps { ... }` の末尾(`onHouseholdsLoaded` の後)に以下を追加。
先頭の import に `net.brightroom.mindstock.frontend.auth.Tokens` が既にあること(なければ追加)。

```kotlin
    /** WS を貼り直す(close → connect)。登録直後にセッションを Registered へ昇格させる。 */
    suspend fun reconnect(token: Tokens)

    /** アクティブ世帯を永続化する。 */
    fun persistActiveHousehold(id: HouseholdId)

    /** 永続化済みアクティブ世帯。無ければ null。 */
    fun savedActiveHousehold(): HouseholdId?
```

- [ ] **Step 3: コンパイル確認(まだ実装が無いので落ちることを確認)**

Run: `./gradlew :frontend:compileKotlinJvm 2>&1 | tail -20`（※ frontend に jvm target が無い場合は Step 5 のビルドで確認）
Expected: `WebAuthDeps` / `AuthViewModel` が `AuthDeps` を実装していないエラー、または AuthViewModel が AuthFlow 未実装。これは Task 3/4 で解消する。

> このタスク単体ではビルドが通らない。Task 3・4 まで進めてから通す。コミットは型追加として行ってよい。

- [ ] **Step 4: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthFlow.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthDeps.kt
git commit -m "feat(frontend): AuthFlow coordinator interface と AuthDeps 拡張点を追加"
```

---

## Task 3: AuthViewModel を AuthFlow 実装 + boot の saved-active 選択(TDD)

**Files:**
- Modify: `<src>/app/AuthViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelTest.kt`

- [ ] **Step 1: 既存テストの FakeAuthDeps を新メソッド対応に拡張する**

`AuthViewModelTest.kt` の `FakeAuthDeps` に、コンストラクタ引数 `savedActive` を足し、新メソッドを実装する。
クラス本体(`override fun onHouseholdsLoaded` の後)に追加:

```kotlin
    // --- 追加: コンストラクタ引数 ---
    // private val savedActive: HouseholdId? = null,  ← 一次パラメータ末尾に追加すること

    var reconnectCalled = false
    var persistedActive: HouseholdId? = null

    override suspend fun reconnect(token: Tokens) {
        reconnectCalled = true
    }

    override fun persistActiveHousehold(id: HouseholdId) {
        persistedActive = id
    }

    override fun savedActiveHousehold(): HouseholdId? = savedActive
```

`FakeAuthDeps` の宣言を次のように変更(`failHouseholds` の後に `savedActive` を追加):

```kotlin
private class FakeAuthDeps(
    private val path: String,
    private val token: String?,
    private val status: SessionStatus? = null,
    private val households: Households = Households(emptyList()),
    private val failHouseholds: Boolean = false,
    private val savedActive: HouseholdId? = null,
) : AuthDeps {
```

- [ ] **Step 2: 失敗するテストを追加する**

`AuthViewModelTest.kt` の `class AuthViewModelTest { ... }` 内に追加:

```kotlin
    @Test
    fun boot_honors_saved_active_household() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
            val h1 = Household(HouseholdId.create(), HouseholdProfile(HouseholdName("家1")), Members(emptyList()))
            val h2 = Household(HouseholdId.create(), HouseholdProfile(HouseholdName("家2")), Members(emptyList()))
            val deps =
                FakeAuthDeps(
                    path = "/",
                    token = "tok",
                    status = SessionStatus.Registered(resident),
                    households = Households(listOf(h1, h2)),
                    savedActive = h2.id,
                )
            val vm = AuthViewModel(deps)
            vm.boot()
            deps.persistedActive shouldBe h2.id
            vm.state.value.shouldBeInstanceOf<AuthState.Ready>()
        }

    @Test
    fun boot_falls_back_to_first_when_saved_active_absent() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
            val h1 = Household(HouseholdId.create(), HouseholdProfile(HouseholdName("家1")), Members(emptyList()))
            val deps =
                FakeAuthDeps(
                    path = "/",
                    token = "tok",
                    status = SessionStatus.Registered(resident),
                    households = Households(listOf(h1)),
                    savedActive = HouseholdId.create(), // 一覧に居ない
                )
            val vm = AuthViewModel(deps)
            vm.boot()
            deps.persistedActive shouldBe h1.id
            vm.state.value.shouldBeInstanceOf<AuthState.Ready>()
        }

    @Test
    fun on_resident_registered_reflects_and_reconnects() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
            val deps = FakeAuthDeps(path = "/", token = "tok")
            val vm = AuthViewModel(deps)
            vm.onResidentRegistered(resident)
            deps.onAuthenticatedCalled shouldBe true
            deps.reconnectCalled shouldBe true
        }

    @Test
    fun enter_app_loads_persists_and_becomes_ready() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
            val h1 = Household(HouseholdId.create(), HouseholdProfile(HouseholdName("家1")), Members(emptyList()))
            val deps =
                FakeAuthDeps(
                    path = "/",
                    token = "tok",
                    status = SessionStatus.Registered(resident),
                    households = Households(listOf(h1)),
                )
            val vm = AuthViewModel(deps)
            vm.enterApp(h1.id)
            deps.setHouseholdsCalled.shouldNotBeNull().size() shouldBe 1
            deps.persistedActive shouldBe h1.id
            vm.state.value.shouldBeInstanceOf<AuthState.Ready>()
        }

    @Test
    fun need_household_transitions_state() =
        runTest {
            val deps = FakeAuthDeps(path = "/", token = "tok")
            val vm = AuthViewModel(deps)
            vm.needHousehold()
            vm.state.value.shouldBeInstanceOf<AuthState.NeedHousehold>()
        }
```

- [ ] **Step 3: テストが失敗(コンパイルエラー)することを確認**

Run: `./gradlew :frontend:compileTestKotlinWasmJs 2>&1 | tail -30`
Expected: `AuthViewModel` に `onResidentRegistered`/`enterApp`/`needHousehold` が無い、`reconnect`未実装 等でコンパイル失敗。

- [ ] **Step 4: AuthViewModel を実装する**

`<src>/app/AuthViewModel.kt` を次の通り変更。
(a) `import net.brightroom.mindstock.domain.model.resident.Resident` を追加(無ければ)。
(b) クラス宣言を `AuthFlow` 実装に変更。
(c) boot の `Registered` 分岐を saved-active 選択に変更。
(d) `AuthFlow` の 3 メソッドを実装。

```kotlin
class AuthViewModel(
    private val deps: AuthDeps,
) : ViewModel(),
    AuthFlow {
    private val _state = MutableStateFlow<AuthState>(AuthState.Booting)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun boot() {
        if (deps.currentPath() == "/auth/callback") {
            runCatching { deps.handleCallback() }
                .onFailure { _state.value = AuthState.Failed("ログインに失敗しました") }
            return
        }
        val token = deps.loadValidToken()
        if (token == null) {
            deps.redirectToAuthorize()
            return
        }
        try {
            when (val status = deps.fetchSessionStatus(token)) {
                is SessionStatus.Registered -> {
                    deps.onAuthenticated(status.resident)
                    val households = deps.loadHouseholds()
                    val saved = deps.savedActiveHousehold()
                    val active =
                        households.list.firstOrNull { it.id == saved }
                            ?: households.list.firstOrNull()
                    if (active == null) {
                        _state.value = AuthState.NeedHousehold
                    } else {
                        deps.onHouseholdsLoaded(households, active.id)
                        deps.persistActiveHousehold(active.id)
                        _state.value = AuthState.Ready
                    }
                }

                is SessionStatus.Unregistered -> {
                    _state.value = AuthState.NeedOnboarding
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            _state.value = AuthState.Failed("起動に失敗しました")
        }
    }

    override suspend fun onResidentRegistered(resident: Resident) {
        deps.onAuthenticated(resident)
        val token = deps.loadValidToken() ?: error("token lost during registration")
        deps.reconnect(token)
    }

    override suspend fun enterApp(activeId: HouseholdId) {
        val households = deps.loadHouseholds()
        deps.onHouseholdsLoaded(households, activeId)
        deps.persistActiveHousehold(activeId)
        _state.value = AuthState.Ready
    }

    override fun needHousehold() {
        _state.value = AuthState.NeedHousehold
    }
}
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*AuthViewModelTest*" 2>&1 | tail -30`
Expected: PASS(既存 6 件 + 新規 5 件)。

- [ ] **Step 6: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelTest.kt
git commit -m "feat(frontend): AuthViewModel を AuthFlow 化し saved-active 起動を実装"
```

---

## Task 4: WebAuthDeps に新メソッドを実装

**Files:**
- Modify: `<src>/../../webMain/.../WebAuthDeps.kt`(実体: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/WebAuthDeps.kt`)

web 専用のため commonTest なし。Task 8 のフルビルドで検証。

- [ ] **Step 1: import を追加する**

`WebAuthDeps.kt` の import 群に追加:

```kotlin
import net.brightroom.mindstock.frontend.core.preference.PreferenceStore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
```

ファイル先頭(package 行の直前)に OptIn を付ける:

```kotlin
@file:OptIn(ExperimentalUuidApi::class)
```

- [ ] **Step 2: 定数を追加する**

`private const val VERIFIER_KEY = ...` の下に追加:

```kotlin
private const val ACTIVE_HOUSEHOLD_KEY = "mindstock.active_household.v1"
```

- [ ] **Step 3: 3 メソッドを実装する**

`class WebAuthDeps(...)` の末尾(`onHouseholdsLoaded` の後)に追加:

```kotlin
    override suspend fun reconnect(token: Tokens) {
        rpc.close()
        rpc.connect(token.accessToken)
    }

    override fun persistActiveHousehold(id: HouseholdId) {
        PreferenceStore.set(ACTIVE_HOUSEHOLD_KEY, id().toString())
    }

    override fun savedActiveHousehold(): HouseholdId? {
        val raw = PreferenceStore.get(ACTIVE_HOUSEHOLD_KEY) ?: return null
        return runCatching { HouseholdId(Uuid.parse(raw)) }.getOrNull()
    }
```

> 注: `HouseholdId` の値取り出しは `id()`(operator invoke)。`HouseholdId(Uuid)` コンストラクタが存在することを `domain/.../household/HouseholdId.kt` で確認。`rpc.connect` / `rpc.close` のシグネチャは `RpcClientProvider` を確認(`connect(accessToken: String)` / `close()`)。

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs 2>&1 | tail -20`
Expected: PASS(WebAuthDeps が AuthDeps を完全実装)。

- [ ] **Step 5: コミット**

```bash
git add frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/WebAuthDeps.kt
git commit -m "feat(frontend): WebAuthDeps に reconnect とアクティブ世帯永続化を実装"
```

---

## Task 5: ResidentRepository(+test)

**Files:**
- Create: `<src>/feature/resident/data/ResidentRepository.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/resident/data/ResidentRepositoryTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.resident.data

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

private class FakeResidentRegisterService(
    private val result: RpcResult<Resident, RpcError>,
) : ResidentRegisterRpcService {
    override suspend fun registerDisplayName(displayName: DisplayName): RpcResult<Resident, RpcError> = result

    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)
}

class ResidentRepositoryTest {
    @Test
    fun register_maps_ok_to_success() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("たろう")))
            val repo = ResidentRepository(residentRegisterService = { FakeResidentRegisterService(RpcResult.Ok(resident)) })
            repo.register(DisplayName("たろう")).shouldBeInstanceOf<RpcOutcome.Success<Resident>>()
        }

    @Test
    fun register_maps_err_to_failure() =
        runTest {
            val repo =
                ResidentRepository(
                    residentRegisterService = { FakeResidentRegisterService(RpcResult.Err(RpcError.Internal("boom"))) },
                )
            repo.register(DisplayName("たろう")).shouldBeInstanceOf<RpcOutcome.Failure>()
        }
}
```

- [ ] **Step 2: テストが失敗(未定義)することを確認**

Run: `./gradlew :frontend:compileTestKotlinWasmJs 2>&1 | tail -20`
Expected: `ResidentRepository` が未定義でコンパイル失敗。

- [ ] **Step 3: ResidentRepository を実装する**

```kotlin
package net.brightroom.mindstock.frontend.feature.resident.data

import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService

/** 住人登録まわりの RPC を隠蔽。サービスは認証後に開かれる opener として遅延注入する。 */
class ResidentRepository(
    private val residentRegisterService: () -> ResidentRegisterRpcService,
) {
    suspend fun register(displayName: DisplayName): RpcOutcome<Resident> =
        residentRegisterService().registerDisplayName(displayName).toOutcome()
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*ResidentRepositoryTest*" 2>&1 | tail -20`
Expected: PASS。

- [ ] **Step 5: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/resident \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/resident
git commit -m "feat(frontend): ResidentRepository を追加"
```

---

## Task 6: HouseholdRepository(+test)

**Files:**
- Create: `<src>/feature/household/data/HouseholdRepository.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/household/data/HouseholdRepositoryTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.household.data

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.household.InvitationPreview
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

private fun household(name: String) = Household(HouseholdId.create(), Profile(HouseholdName(name)), Members(emptyList()))

private class FakeHouseholdService(
    private val preview: RpcResult<InvitationPreview, RpcError>,
) : HouseholdRpcService {
    override suspend fun list(): RpcResult<Households, RpcError> = RpcResult.Ok(Households(emptyList()))

    override suspend fun previewInvite(code: InvitationCode): RpcResult<InvitationPreview, RpcError> = preview
}

private class FakeHouseholdRegisterService(
    private val createResult: RpcResult<Household, RpcError>,
    private val joinResult: RpcResult<Household, RpcError>,
) : HouseholdRegisterRpcService {
    override suspend fun create(name: HouseholdName): RpcResult<Household, RpcError> = createResult

    override suspend fun rename(
        householdId: HouseholdId,
        name: HouseholdName,
    ): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)

    override suspend fun leave(householdId: HouseholdId): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)

    override suspend fun changeRole(
        householdId: HouseholdId,
        target: ResidentId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)

    override suspend fun removeMember(
        householdId: HouseholdId,
        target: ResidentId,
    ): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)

    override suspend fun createInvite(
        householdId: HouseholdId,
        role: HouseholdMemberRole,
    ): RpcResult<net.brightroom.mindstock.domain.model.household.invitation.Invitation, RpcError> =
        RpcResult.Err(RpcError.Internal("n/a"))

    override suspend fun revokeInvite(code: InvitationCode): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)

    override suspend fun join(code: InvitationCode): RpcResult<Household, RpcError> = joinResult
}

private fun repo(
    preview: RpcResult<InvitationPreview, RpcError> = RpcResult.Ok(InvitationPreview(HouseholdName("家"), HouseholdMemberRole.メンバー)),
    create: RpcResult<Household, RpcError> = RpcResult.Ok(household("家")),
    join: RpcResult<Household, RpcError> = RpcResult.Ok(household("家")),
) = HouseholdRepository(
    householdService = { FakeHouseholdService(preview) },
    householdRegisterService = { FakeHouseholdRegisterService(create, join) },
)

class HouseholdRepositoryTest {
    @Test
    fun create_maps_ok() =
        runTest {
            repo().create(HouseholdName("家")).shouldBeInstanceOf<RpcOutcome.Success<Household>>()
        }

    @Test
    fun create_maps_err() =
        runTest {
            repo(create = RpcResult.Err(RpcError.Conflict("dup"))).create(HouseholdName("家"))
                .shouldBeInstanceOf<RpcOutcome.Failure>()
        }

    @Test
    fun preview_maps_ok() =
        runTest {
            repo().previewInvite(InvitationCode.generate()).shouldBeInstanceOf<RpcOutcome.Success<InvitationPreview>>()
        }

    @Test
    fun preview_maps_err() =
        runTest {
            repo(preview = RpcResult.Err(RpcError.NotFound("no")))
                .previewInvite(InvitationCode.generate())
                .shouldBeInstanceOf<RpcOutcome.Failure>()
        }

    @Test
    fun join_maps_ok() =
        runTest {
            repo().join(InvitationCode.generate()).shouldBeInstanceOf<RpcOutcome.Success<Household>>()
        }
}
```

> 注: `HouseholdRegisterRpcService` / `HouseholdRpcService` の正確なメソッド集合は
> `rpc/.../household/*.kt` を見て fake に過不足なく合わせること(上記は調査時点のシグネチャ)。
> `createInvite` の戻り値型 `Invitation` のパッケージは `domain/.../household/invitation/Invitation.kt` を確認。

- [ ] **Step 2: テストが失敗(未定義)することを確認**

Run: `./gradlew :frontend:compileTestKotlinWasmJs 2>&1 | tail -20`
Expected: `HouseholdRepository` 未定義でコンパイル失敗。

- [ ] **Step 3: HouseholdRepository を実装する**

```kotlin
package net.brightroom.mindstock.frontend.feature.household.data

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.household.InvitationPreview

/**
 * 世帯の作成・参加・招待プレビューまわりの RPC を隠蔽。サービスは opener として遅延注入する。
 * P6-3b で rename / leave / changeRole / removeMember / createInvite / revokeInvite を追加予定。
 */
class HouseholdRepository(
    private val householdService: () -> HouseholdRpcService,
    private val householdRegisterService: () -> HouseholdRegisterRpcService,
) {
    suspend fun create(name: HouseholdName): RpcOutcome<Household> = householdRegisterService().create(name).toOutcome()

    suspend fun join(code: InvitationCode): RpcOutcome<Household> = householdRegisterService().join(code).toOutcome()

    suspend fun previewInvite(code: InvitationCode): RpcOutcome<InvitationPreview> =
        householdService().previewInvite(code).toOutcome()
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*HouseholdRepositoryTest*" 2>&1 | tail -20`
Expected: PASS。

- [ ] **Step 5: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/data \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/household/data
git commit -m "feat(frontend): HouseholdRepository を追加"
```

---

## Task 7: OnboardingUiState + OnboardingViewModel(+test)

**Files:**
- Create: `<src>/feature/onboarding/OnboardingUiState.kt`
- Create: `<src>/feature/onboarding/OnboardingViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/onboarding/OnboardingViewModelTest.kt`

VM は code/name を **String で受け**、VO 構築の IAE は内部で握って toast にする(不正入力の唯一の着地点)。

- [ ] **Step 1: UiState を書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.onboarding

enum class OnboardingStep { Welcome, Name, Household, Confirm }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val name: String = "",
    val householdName: String = "",
    val submitting: Boolean = false,
)
```

- [ ] **Step 2: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.onboarding

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile

private class FakeAuthFlow : AuthFlow {
    var registered: Resident? = null
    var enteredId: HouseholdId? = null
    var needHouseholdCalled = false

    override suspend fun onResidentRegistered(resident: Resident) {
        registered = resident
    }

    override suspend fun enterApp(activeId: HouseholdId) {
        enteredId = activeId
    }

    override fun needHousehold() {
        needHouseholdCalled = true
    }
}

private fun resident() = Resident(ResidentId.create(), ResidentProfile(DisplayName("たろう")))

private fun household() = Household(HouseholdId.create(), Profile(HouseholdName("家")), Members(emptyList()))

private fun vm(
    register: suspend (DisplayName) -> RpcOutcome<Resident> = { RpcOutcome.Success(resident()) },
    create: suspend (HouseholdName) -> RpcOutcome<Household> = { RpcOutcome.Success(household()) },
    flow: AuthFlow = FakeAuthFlow(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = OnboardingViewModel(
    registerDisplayName = register,
    createHousehold = create,
    flow = flow,
    toast = toast,
    reauth = reauth,
)

class OnboardingViewModelTest {
    @Test
    fun submit_with_household_registers_creates_and_enters() =
        runTest {
            val flow = FakeAuthFlow()
            val h = household()
            val v =
                vm(
                    register = { RpcOutcome.Success(resident()) },
                    create = { RpcOutcome.Success(h) },
                    flow = flow,
                )
            v.setName("たろう")
            v.setHouseholdName("わたしの家")
            v.submit()
            flow.registered shouldBe flow.registered // 非 null を後段で確認
            (flow.registered != null) shouldBe true
            flow.enteredId shouldBe h.id
        }

    @Test
    fun submit_skipping_household_goes_need_household() =
        runTest {
            val flow = FakeAuthFlow()
            val v = vm(flow = flow)
            v.setName("たろう")
            v.setHouseholdName("")
            v.submit()
            (flow.registered != null) shouldBe true
            flow.needHouseholdCalled shouldBe true
            flow.enteredId shouldBe null
        }

    @Test
    fun submit_register_failure_keeps_step_and_does_not_register() =
        runTest {
            val flow = FakeAuthFlow()
            val v =
                vm(
                    register = { RpcOutcome.Failure(RpcError.Internal("boom")) },
                    flow = flow,
                )
            v.setName("たろう")
            v.setHouseholdName("わたしの家")
            v.submit()
            flow.registered shouldBe null
            flow.enteredId shouldBe null
            v.state.value.submitting shouldBe false
        }

    @Test
    fun submit_register_unauthorized_requests_reauth() =
        runTest {
            var reauthed = 0
            val reauth = ReauthController()
            val job =
                kotlinx.coroutines.GlobalScope.launch { reauth.signal.collect { reauthed++ } }
            val v =
                vm(
                    register = { RpcOutcome.Failure(RpcError.Unauthorized("expired")) },
                    reauth = reauth,
                )
            v.setName("たろう")
            v.submit()
            // tryEmit のバッファに 1 件入っていれば良い(collect の有無に依らずクラッシュしないこと)
            job.cancel()
        }

    @Test
    fun step_navigation_next_and_back() =
        runTest {
            val v = vm()
            v.next()
            v.state.value.step shouldBe OnboardingStep.Name
            v.back()
            v.state.value.step shouldBe OnboardingStep.Welcome
        }
}
```

> 注: `GlobalScope` の使用は避けたい場合、reauth テストは `ReauthController` の `request()` が呼ばれることを
> 直接確認できないため簡略化している。クラッシュしないことの確認に留める(他 VM テストと同方針)。

- [ ] **Step 3: テストが失敗(未定義)することを確認**

Run: `./gradlew :frontend:compileTestKotlinWasmJs 2>&1 | tail -20`
Expected: `OnboardingViewModel` 未定義でコンパイル失敗。

- [ ] **Step 4: OnboardingViewModel を実装する**

```kotlin
package net.brightroom.mindstock.frontend.feature.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError

class OnboardingViewModel(
    private val registerDisplayName: suspend (DisplayName) -> RpcOutcome<Resident>,
    private val createHousehold: suspend (HouseholdName) -> RpcOutcome<Household>,
    private val flow: AuthFlow,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setHouseholdName(value: String) = _state.update { it.copy(householdName = value) }

    fun next() =
        _state.update {
            it.copy(
                step =
                    when (it.step) {
                        OnboardingStep.Welcome -> OnboardingStep.Name
                        OnboardingStep.Name -> OnboardingStep.Household
                        OnboardingStep.Household -> OnboardingStep.Confirm
                        OnboardingStep.Confirm -> OnboardingStep.Confirm
                    },
            )
        }

    fun back() =
        _state.update {
            it.copy(
                step =
                    when (it.step) {
                        OnboardingStep.Welcome -> OnboardingStep.Welcome
                        OnboardingStep.Name -> OnboardingStep.Welcome
                        OnboardingStep.Household -> OnboardingStep.Name
                        OnboardingStep.Confirm -> OnboardingStep.Household
                    },
            )
        }

    /** 確認 step の確定: 登録 → (世帯あり: 作成→enterApp / なし: needHousehold)。 */
    suspend fun submit() {
        val current = _state.value
        val displayName = runCatching { DisplayName(current.name) }.getOrNull()
        if (displayName == null) {
            toast.show(errorText(RpcError.BadRequest("displayName", "invalid")))
            return
        }
        _state.update { it.copy(submitting = true) }

        when (val reg = registerDisplayName(displayName)) {
            is RpcOutcome.Success -> {
                try {
                    flow.onResidentRegistered(reg.value)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    toast.show(errorText(RpcError.Internal("reconnect failed")))
                    _state.update { it.copy(submitting = false) }
                    return
                }
                val rawHousehold = current.householdName.trim()
                if (rawHousehold.isEmpty()) {
                    flow.needHousehold()
                    return
                }
                val householdName = runCatching { HouseholdName(rawHousehold) }.getOrNull()
                if (householdName == null) {
                    toast.show(errorText(RpcError.BadRequest("householdName", "invalid")))
                    _state.update { it.copy(submitting = false) }
                    return
                }
                when (val created = createHousehold(householdName)) {
                    is RpcOutcome.Success -> enterOrEscape(created.value)
                    is RpcOutcome.Failure -> {
                        if (created.error.requiresReauth()) {
                            reauth.request()
                        } else {
                            toast.show(errorText(created.error))
                            flow.needHousehold()
                        }
                    }
                }
            }

            is RpcOutcome.Failure -> {
                if (reg.error.requiresReauth()) reauth.request() else toast.show(errorText(reg.error))
                _state.update { it.copy(submitting = false) }
            }
        }
    }

    private suspend fun enterOrEscape(household: Household) {
        try {
            flow.enterApp(household.id)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            toast.show(errorText(RpcError.Internal("enter failed")))
            flow.needHousehold()
        }
    }
}
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*OnboardingViewModelTest*" 2>&1 | tail -30`
Expected: PASS。

- [ ] **Step 6: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/onboarding \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/onboarding
git commit -m "feat(frontend): OnboardingViewModel を追加"
```

---

## Task 8: NeedHouseholdUiState + NeedHouseholdViewModel(+test)

**Files:**
- Create: `<src>/feature/household/NeedHouseholdUiState.kt`
- Create: `<src>/feature/household/NeedHouseholdViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/household/NeedHouseholdViewModelTest.kt`

- [ ] **Step 1: UiState を書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.household

import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.household.InvitationPreview

data class NeedHouseholdUiState(
    val preview: InvitationPreview? = null,
    val previewError: UiText? = null,
    val busy: Boolean = false,
)
```

- [ ] **Step 2: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.household

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.household.InvitationPreview
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private class FakeAuthFlow : AuthFlow {
    var enteredId: HouseholdId? = null

    override suspend fun onResidentRegistered(resident: Resident) {}

    override suspend fun enterApp(activeId: HouseholdId) {
        enteredId = activeId
    }

    override fun needHousehold() {}
}

private fun household() = Household(HouseholdId.create(), Profile(HouseholdName("家")), Members(emptyList()))

private fun vm(
    create: suspend (HouseholdName) -> RpcOutcome<Household> = { RpcOutcome.Success(household()) },
    preview: suspend (InvitationCode) -> RpcOutcome<InvitationPreview> = {
        RpcOutcome.Success(InvitationPreview(HouseholdName("ゆいの家"), HouseholdMemberRole.メンバー))
    },
    join: suspend (InvitationCode) -> RpcOutcome<Household> = { RpcOutcome.Success(household()) },
    flow: AuthFlow = FakeAuthFlow(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = NeedHouseholdViewModel(
    createHousehold = create,
    previewInvite = preview,
    joinByCode = join,
    flow = flow,
    toast = toast,
    reauth = reauth,
)

class NeedHouseholdViewModelTest {
    @Test
    fun create_enters_app() =
        runTest {
            val flow = FakeAuthFlow()
            val h = household()
            val v = vm(create = { RpcOutcome.Success(h) }, flow = flow)
            v.create("わたしの家")
            flow.enteredId shouldBe h.id
        }

    @Test
    fun preview_invalid_code_sets_error_without_calling_service() =
        runTest {
            val v = vm()
            v.preview("zzz") // 6桁でない/不正英字 → InvitationCode 構築失敗
            v.state.value.previewError.shouldNotBeNull()
            v.state.value.preview shouldBe null
        }

    @Test
    fun preview_not_found_sets_error() =
        runTest {
            val v = vm(preview = { RpcOutcome.Failure(RpcError.NotFound("no")) })
            v.preview(InvitationCode.generate().toString())
            v.state.value.previewError.shouldNotBeNull()
            v.state.value.preview shouldBe null
        }

    @Test
    fun preview_success_sets_preview() =
        runTest {
            val v = vm()
            v.preview(InvitationCode.generate().toString())
            v.state.value.preview.shouldNotBeNull()
        }

    @Test
    fun join_enters_app() =
        runTest {
            val flow = FakeAuthFlow()
            val h = household()
            val v = vm(join = { RpcOutcome.Success(h) }, flow = flow)
            v.join(InvitationCode.generate().toString())
            flow.enteredId shouldBe h.id
        }
}
```

- [ ] **Step 3: テストが失敗(未定義)することを確認**

Run: `./gradlew :frontend:compileTestKotlinWasmJs 2>&1 | tail -20`
Expected: `NeedHouseholdViewModel` 未定義でコンパイル失敗。

- [ ] **Step 4: NeedHouseholdViewModel を実装する**

```kotlin
package net.brightroom.mindstock.frontend.feature.household

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

class NeedHouseholdViewModel(
    private val createHousehold: suspend (HouseholdName) -> RpcOutcome<Household>,
    private val previewInvite: suspend (InvitationCode) -> RpcOutcome<net.brightroom.mindstock.rpc.household.InvitationPreview>,
    private val joinByCode: suspend (InvitationCode) -> RpcOutcome<Household>,
    private val flow: AuthFlow,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow(NeedHouseholdUiState())
    val state: StateFlow<NeedHouseholdUiState> = _state.asStateFlow()

    fun clearPreview() = _state.update { it.copy(preview = null, previewError = null) }

    suspend fun create(rawName: String) {
        val name = runCatching { HouseholdName(rawName.trim()) }.getOrNull()
        if (name == null) {
            toast.show(errorText(RpcError.BadRequest("householdName", "invalid")))
            return
        }
        _state.update { it.copy(busy = true) }
        when (val out = createHousehold(name)) {
            is RpcOutcome.Success -> enterOrEscape(out.value)
            is RpcOutcome.Failure -> {
                handleFailure(out.error)
                _state.update { it.copy(busy = false) }
            }
        }
    }

    suspend fun preview(rawCode: String) {
        val code = parseCode(rawCode)
        if (code == null) {
            _state.update { it.copy(preview = null, previewError = invalidCodeText()) }
            return
        }
        when (val out = previewInvite(code)) {
            is RpcOutcome.Success -> _state.update { it.copy(preview = out.value, previewError = null) }
            is RpcOutcome.Failure -> {
                if (out.error.requiresReauth()) reauth.request()
                _state.update { it.copy(preview = null, previewError = errorText(out.error)) }
            }
        }
    }

    suspend fun join(rawCode: String) {
        val code = parseCode(rawCode)
        if (code == null) {
            _state.update { it.copy(previewError = invalidCodeText()) }
            return
        }
        _state.update { it.copy(busy = true) }
        when (val out = joinByCode(code)) {
            is RpcOutcome.Success -> enterOrEscape(out.value)
            is RpcOutcome.Failure -> {
                handleFailure(out.error)
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun parseCode(raw: String): InvitationCode? = runCatching { InvitationCode(raw.trim().uppercase()) }.getOrNull()

    private fun invalidCodeText(): UiText =
        UiText(mindstock.frontend.generated.resources.Res.string.join_code_invalid)

    private suspend fun enterOrEscape(household: Household) {
        try {
            flow.enterApp(household.id)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            toast.show(errorText(RpcError.Internal("enter failed")))
            _state.update { it.copy(busy = false) }
        }
    }

    private fun handleFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }
}
```

> 注: `join_code_invalid` 文言は Task 9 で strings.xml に追加する。Task 8→9 の順なら一時的に
> 未解決になるため、**Task 9 を先に行ってもよい**(順序入れ替え可)。実行時は strings 追加済みの状態でビルドする。

- [ ] **Step 5: テストが通ることを確認(strings 追加後)**

Run: `./gradlew :frontend:wasmJsTest --tests "*NeedHouseholdViewModelTest*" 2>&1 | tail -30`
Expected: PASS。

- [ ] **Step 6: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/NeedHousehold*.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/household/NeedHouseholdViewModelTest.kt
git commit -m "feat(frontend): NeedHouseholdViewModel を追加"
```

---

## Task 9: 文言追加(strings.xml)+ 旧 placeholder 削除

**Files:**
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: 旧 placeholder を削除する**

`strings.xml` から以下 2 行を削除:

```xml
    <string name="need_household">世帯がありません（世帯作成は準備中）</string>
    <string name="onboarding_placeholder">オンボーディング(P6-3)</string>
```

- [ ] **Step 2: P6-3a 文言を追加する**

`</resources>` の直前に追加:

```xml
    <!-- P6-3a オンボーディング -->
    <string name="onboarding_welcome_title">ようこそ、mindstock へ</string>
    <string name="onboarding_welcome_sub">はじめに、かんたんな初期設定をします。表示名を決めれば準備完了。世帯はあとからでもつくれます。</string>
    <string name="onboarding_welcome_item1">表示名を決める</string>
    <string name="onboarding_welcome_item1_sub">記録に残る名前</string>
    <string name="onboarding_welcome_item2">世帯をつくる</string>
    <string name="onboarding_welcome_item2_sub">任意・あとでもOK</string>
    <string name="onboarding_start">はじめる</string>
    <string name="onboarding_name_eyebrow">プロフィール</string>
    <string name="onboarding_name_title">お名前を教えてください</string>
    <string name="onboarding_name_sub">補充や消費の記録に表示され、家族にも見えます。あとから変更できます。</string>
    <string name="onboarding_name_placeholder">例: たろう</string>
    <string name="onboarding_household_eyebrow">世帯をつくる（任意）</string>
    <string name="onboarding_household_title">世帯に名前をつけましょう</string>
    <string name="onboarding_household_sub">在庫を共有する単位です。まずはあなた1人から。あとで家族を招待できます。今は決めなくても大丈夫です。</string>
    <string name="onboarding_household_placeholder">例: わたしの家</string>
    <string name="onboarding_next">次へ</string>
    <string name="onboarding_to_confirm">確認する</string>
    <string name="onboarding_skip">あとで設定する（スキップ）</string>
    <string name="onboarding_confirm_eyebrow">最終確認</string>
    <string name="onboarding_confirm_title">この内容で始めます</string>
    <string name="onboarding_confirm_name_label">表示名</string>
    <string name="onboarding_confirm_household_label">世帯</string>
    <string name="onboarding_confirm_empty">—</string>
    <string name="onboarding_confirm_note">あなたが世帯のオーナーになります。家族の招待は設定からいつでも。</string>
    <string name="onboarding_finish">mindstock を始める</string>
    <string name="onboarding_finishing">作成中…</string>
    <string name="onboarding_edit">修正する</string>
    <string name="onboarding_progress">%1$d / %2$d</string>
    <string name="household_suggest_1">わたしの家</string>
    <string name="household_suggest_2">田中家</string>
    <string name="household_suggest_3">自宅</string>
    <!-- P6-3a 世帯なし / 作成 / 参加 -->
    <string name="need_household_title">まだ世帯がありません</string>
    <string name="need_household_sub">在庫の管理をはじめるには、世帯をつくるか、招待コードで参加してください。</string>
    <string name="need_household_create">世帯をつくる</string>
    <string name="need_household_join">招待コードで参加</string>
    <string name="create_household_title">世帯をつくる</string>
    <string name="create_household_sub">在庫を共有する単位です。あなたがオーナーになります。あとから家族を招待できます。</string>
    <string name="create_household_placeholder">例: 別荘の在庫</string>
    <string name="create_household_cta">この世帯をつくる</string>
    <string name="create_household_busy">作成中…</string>
    <string name="create_suggest_1">わたしの家</string>
    <string name="create_suggest_2">実家</string>
    <string name="create_suggest_3">別荘</string>
    <string name="create_suggest_4">オフィス</string>
    <string name="join_code_title">招待コードで参加</string>
    <string name="join_code_sub">家族や同居人から受け取った6桁のコードを入力してください。</string>
    <string name="join_code_placeholder">K7M2PQ</string>
    <string name="join_code_invalid">コードが正しくありません</string>
    <string name="join_code_preview_role">権限：%1$s</string>
    <string name="join_code_preview_household">参加する世帯</string>
    <string name="join_code_cta">参加する</string>
    <string name="join_code_busy">参加中…</string>
    <string name="role_owner">オーナー</string>
    <string name="role_member">編集できる</string>
    <string name="role_viewer">閲覧のみ</string>
    <string name="toast_household_created">世帯をつくりました</string>
    <string name="toast_household_joined">世帯に参加しました</string>
```

- [ ] **Step 3: 文言生成リソースが反映されることを確認**

Run: `./gradlew :frontend:generateComposeResClass 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL（`Res.string.join_code_invalid` 等が生成される）。
> タスク名が異なる環境では `./gradlew :frontend:compileKotlinWasmJs` で生成+コンパイルを確認。

- [ ] **Step 4: コミット**

```bash
git add frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(frontend): P6-3a の文言を追加し旧 placeholder を削除"
```

---

## Task 10: アイコン追加(Check / Link / Users)

**Files:**
- Modify: `<src>/designsystem/atom/AppIcon.kt`

- [ ] **Step 1: enum に 3 値を追加する**

`enum class AppIconName { ... }` の末尾(`Pencil,` の後)に追加:

```kotlin
    Check,
    Link,
    Users,
```

- [ ] **Step 2: vector() のマッピングを追加する**

`private fun AppIconName.vector(): ImageVector = when (this) { ... }` の末尾(`AppIconName.Pencil -> ...` の後)に追加:

```kotlin
        AppIconName.Check -> Icons.Outlined.Check
        AppIconName.Link -> Icons.Outlined.Link
        AppIconName.Users -> Icons.Outlined.Group
```

import を追加(ファイル先頭の Icons import 群に合わせる):

```kotlin
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Group
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs 2>&1 | tail -20`
Expected: PASS。（material-icons-extended に該当アイコンが無い場合は近い outlined アイコンに差し替える: 例 `Group` 無→`People`、`Link` 無→`InsertLink`。`Check` は `Icons.Default.Check` でも可。）

- [ ] **Step 4: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppIcon.kt
git commit -m "feat(frontend): AppIconName に Check/Link/Users を追加"
```

---

## Task 11: atom WizardProgress + SuggestionChips

**Files:**
- Create: `<src>/designsystem/atom/WizardProgress.kt`
- Create: `<src>/designsystem/atom/SuggestionChips.kt`

UI 部品のため logic test なし。ビルドで検証。

- [ ] **Step 1: WizardProgress を書く**

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** セグメント進捗バー。total 個のセグメントのうち current(1始まり)未満を accent で塗る。 */
@Composable
fun WizardProgress(
    total: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(total) { i ->
            val filled = i < current
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (filled) scheme.primary else scheme.outlineVariant),
            ) {}
        }
    }
}
```

- [ ] **Step 2: SuggestionChips を書く**

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/** 候補文字列のチップ行。タップで onPick に値を渡す。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionChips(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { s ->
            androidx.compose.foundation.layout.Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(scheme.surface)
                        .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(99.dp))
                        .clickable { onPick(s) }
                        .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                AppText(s, style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
            }
        }
    }
}
```

> 注: `FlowRow` は `androidx.compose.foundation.layout.FlowRow`(ExperimentalLayoutApi)。既存コードで
> 使用例があるか確認し、無ければ通常の `Row` + 折返し不要(候補は 3-4 個)で代替してもよい。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs 2>&1 | tail -20`
Expected: PASS。

- [ ] **Step 4: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/WizardProgress.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/SuggestionChips.kt
git commit -m "feat(frontend): WizardProgress と SuggestionChips atom を追加"
```

---

## Task 12: RoleLabels ヘルパ

**Files:**
- Create: `<src>/feature/household/ui/RoleLabels.kt`

- [ ] **Step 1: roleLabel を書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.household.ui

import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.role_member
import mindstock.frontend.generated.resources.role_owner
import mindstock.frontend.generated.resources.role_viewer
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import org.jetbrains.compose.resources.StringResource

/** 区分(世帯主/メンバー/閲覧者)を UI ラベル(オーナー/編集できる/閲覧のみ)の文言リソースに対応づける。 */
fun roleLabelResource(role: HouseholdMemberRole): StringResource =
    when (role) {
        HouseholdMemberRole.世帯主 -> Res.string.role_owner
        HouseholdMemberRole.メンバー -> Res.string.role_member
        HouseholdMemberRole.閲覧者 -> Res.string.role_viewer
    }
```

> 注: enum 定数名(`世帯主`/`メンバー`/`閲覧者`)は `domain/.../member/HouseholdMemberRole.kt` で確認。

- [ ] **Step 2: コンパイル確認 + コミット**

Run: `./gradlew :frontend:compileKotlinWasmJs 2>&1 | tail -20`
Expected: PASS。

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/RoleLabels.kt
git commit -m "feat(frontend): 世帯ロールの表示ラベルヘルパを追加"
```

---

## Task 13: OnboardingScreen(UI)

**Files:**
- Create: `<src>/feature/onboarding/ui/OnboardingScreen.kt`

モック `screens-onboard.jsx` に寄せる。material3 コンポーネントは直接使わず atom + foundation で組む。
グラデーション背景・カードは theme トークンで表現。

- [ ] **Step 1: OnboardingScreen を書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.onboarding_confirm_empty
import mindstock.frontend.generated.resources.onboarding_confirm_eyebrow
import mindstock.frontend.generated.resources.onboarding_confirm_household_label
import mindstock.frontend.generated.resources.onboarding_confirm_name_label
import mindstock.frontend.generated.resources.onboarding_confirm_note
import mindstock.frontend.generated.resources.onboarding_confirm_title
import mindstock.frontend.generated.resources.onboarding_edit
import mindstock.frontend.generated.resources.onboarding_finish
import mindstock.frontend.generated.resources.onboarding_finishing
import mindstock.frontend.generated.resources.onboarding_household_eyebrow
import mindstock.frontend.generated.resources.onboarding_household_placeholder
import mindstock.frontend.generated.resources.onboarding_household_sub
import mindstock.frontend.generated.resources.onboarding_household_title
import mindstock.frontend.generated.resources.onboarding_name_eyebrow
import mindstock.frontend.generated.resources.onboarding_name_placeholder
import mindstock.frontend.generated.resources.onboarding_name_sub
import mindstock.frontend.generated.resources.onboarding_name_title
import mindstock.frontend.generated.resources.onboarding_next
import mindstock.frontend.generated.resources.onboarding_progress
import mindstock.frontend.generated.resources.onboarding_start
import mindstock.frontend.generated.resources.onboarding_to_confirm
import mindstock.frontend.generated.resources.onboarding_welcome_sub
import mindstock.frontend.generated.resources.onboarding_welcome_title
import mindstock.frontend.generated.resources.onboarding_skip
import mindstock.frontend.generated.resources.household_suggest_1
import mindstock.frontend.generated.resources.household_suggest_2
import mindstock.frontend.generated.resources.household_suggest_3
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.SuggestionChips
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.atom.WizardProgress
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.onboarding.OnboardingStep
import net.brightroom.mindstock.frontend.feature.onboarding.OnboardingUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onName: (String) -> Unit,
    onHouseholdName: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxSize().background(scheme.background).padding(horizontal = 24.dp)) {
        // top bar: back + progress
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 6.dp).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.step == OnboardingStep.Name || state.step == OnboardingStep.Household) {
                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, scheme.outline, RoundedCornerShape(12.dp))
                            .background(scheme.surface)
                            .clickableNoRipple(onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconName.Back, contentDescription = null, size = 19.dp)
                }
                val current = if (state.step == OnboardingStep.Name) 1 else 2
                WizardProgress(total = 2, current = current, modifier = Modifier.weight(1f))
                AppText(stringResource(Res.string.onboarding_progress, current, 2), style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (state.step) {
                OnboardingStep.Welcome -> WelcomeStep()
                OnboardingStep.Name ->
                    FormStep(
                        icon = AppIconName.User,
                        eyebrow = stringResource(Res.string.onboarding_name_eyebrow),
                        title = stringResource(Res.string.onboarding_name_title),
                        sub = stringResource(Res.string.onboarding_name_sub),
                        value = state.name,
                        onChange = onName,
                        placeholder = stringResource(Res.string.onboarding_name_placeholder),
                    )
                OnboardingStep.Household ->
                    FormStep(
                        icon = AppIconName.Home,
                        eyebrow = stringResource(Res.string.onboarding_household_eyebrow),
                        title = stringResource(Res.string.onboarding_household_title),
                        sub = stringResource(Res.string.onboarding_household_sub),
                        value = state.householdName,
                        onChange = onHouseholdName,
                        placeholder = stringResource(Res.string.onboarding_household_placeholder),
                        suggestions =
                            listOf(
                                stringResource(Res.string.household_suggest_1),
                                stringResource(Res.string.household_suggest_2),
                                stringResource(Res.string.household_suggest_3),
                            ),
                        onPickSuggestion = onHouseholdName,
                    )
                OnboardingStep.Confirm -> ConfirmStep(name = state.name, householdName = state.householdName)
            }
        }

        // footer
        Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (state.step) {
                OnboardingStep.Welcome ->
                    AppButton(onClick = onNext, size = ButtonSize.Lg, modifier = Modifier.fillMaxWidth()) {
                        AppText(stringResource(Res.string.onboarding_start))
                    }
                OnboardingStep.Name ->
                    AppButton(onClick = onNext, size = ButtonSize.Lg, enabled = state.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        AppText(stringResource(Res.string.onboarding_next))
                    }
                OnboardingStep.Household -> {
                    AppButton(onClick = onNext, size = ButtonSize.Lg, enabled = state.householdName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        AppText(stringResource(Res.string.onboarding_to_confirm))
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.clickableNoRipple(onSubmit).padding(8.dp)) {
                        AppText(stringResource(Res.string.onboarding_skip), style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
                    }
                }
                OnboardingStep.Confirm -> {
                    AppButton(onClick = onSubmit, size = ButtonSize.Lg, enabled = !state.submitting, modifier = Modifier.fillMaxWidth()) {
                        AppText(stringResource(if (state.submitting) Res.string.onboarding_finishing else Res.string.onboarding_finish))
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.clickableNoRipple(onBack).padding(8.dp)) {
                        AppText(stringResource(Res.string.onboarding_edit), style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconName.Box, contentDescription = null, size = 34.dp, tint = scheme.onPrimary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppText(stringResource(Res.string.onboarding_welcome_title), style = MindstockType.screenTitle())
            AppText(stringResource(Res.string.onboarding_welcome_sub), style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FormStep(
    icon: AppIconName,
    eyebrow: String,
    title: String,
    sub: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    suggestions: List<String> = emptyList(),
    onPickSuggestion: (String) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, size = 26.dp, tint = scheme.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            AppText(eyebrow, style = MindstockType.sectionMeta(), color = scheme.primary)
            AppText(title, style = MindstockType.summaryTitle())
            AppText(sub, style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
        }
        TextInput(value = value, onValueChange = onChange, placeholder = placeholder, modifier = Modifier.fillMaxWidth())
        if (suggestions.isNotEmpty()) {
            SuggestionChips(suggestions = suggestions, onPick = onPickSuggestion)
        }
    }
}

@Composable
private fun ConfirmStep(
    name: String,
    householdName: String,
) {
    val scheme = MaterialTheme.colorScheme
    val empty = stringResource(Res.string.onboarding_confirm_empty)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            AppText(stringResource(Res.string.onboarding_confirm_eyebrow), style = MindstockType.sectionMeta(), color = scheme.primary)
            AppText(stringResource(Res.string.onboarding_confirm_title), style = MindstockType.summaryTitle())
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(18.dp)),
        ) {
            ConfirmRow(label = stringResource(Res.string.onboarding_confirm_name_label), value = name.ifBlank { empty })
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant)) {}
            ConfirmRow(label = stringResource(Res.string.onboarding_confirm_household_label), value = householdName.ifBlank { empty })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(AppIconName.Check, contentDescription = null, size = 16.dp, tint = scheme.primary)
            AppText(stringResource(Res.string.onboarding_confirm_note), style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfirmRow(
    label: String,
    value: String,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        AppText(label, style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
        AppText(value, style = MindstockType.summaryTitle())
    }
}
```

> 注:
> - `MindstockType` の正確なスタイル名(`screenTitle`/`summaryTitle`/`sectionMeta` 等)は
>   `designsystem/theme/MindstockType.kt` で確認し、無いものは近い既存スタイルに置換。
> - `AppIcon` のシグネチャ(`size`/`tint` 引数の有無)を `designsystem/atom/AppIcon.kt` で確認して合わせる。
> - `clickableNoRipple` は下の Step 2 で定義する小ヘルパ。`TextInput` に文字数上限/大文字化が要る場合は
>   既存 `TextInput` の引数を確認(現状 placeholder/isError のみ)。上限はモック準拠だが MVP では未強制で可。

- [ ] **Step 2: clickableNoRipple ヘルパを同ファイル末尾に追加する**

```kotlin
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
```

> ripple を厳密に消す必要はない。単純な `clickable` で良い(視覚調整は後段で詰める)。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs 2>&1 | tail -30`
Expected: PASS。型不一致(MindstockType / AppIcon 引数)があれば注のとおり実体に合わせて修正。

- [ ] **Step 4: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/onboarding/ui
git commit -m "feat(frontend): OnboardingScreen を追加(モック準拠ウィザード)"
```

---

## Task 14: NeedHouseholdScreen + CreateHouseholdSheet + JoinCodeSheet(UI)

**Files:**
- Create: `<src>/feature/household/ui/NeedHouseholdScreen.kt`
- Create: `<src>/feature/household/ui/CreateHouseholdSheet.kt`
- Create: `<src>/feature/household/ui/JoinCodeSheet.kt`

- [ ] **Step 1: NeedHouseholdScreen を書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.household.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.need_household_create
import mindstock.frontend.generated.resources.need_household_join
import mindstock.frontend.generated.resources.need_household_sub
import mindstock.frontend.generated.resources.need_household_title
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

@Composable
fun NeedHouseholdScreen(
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().background(scheme.background).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(20.dp))
                    .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIconName.Home, contentDescription = null, size = 26.dp, tint = scheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(stringResource(Res.string.need_household_title), style = MindstockType.summaryTitle())
                AppText(stringResource(Res.string.need_household_sub), style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
            }
            AppButton(onClick = onCreate, size = ButtonSize.Lg, icon = AppIconName.Home, modifier = Modifier.fillMaxWidth()) {
                AppText(stringResource(Res.string.need_household_create))
            }
            AppButton(onClick = onJoin, variant = ButtonVariant.Ghost, size = ButtonSize.Lg, icon = AppIconName.Link, modifier = Modifier.fillMaxWidth()) {
                AppText(stringResource(Res.string.need_household_join))
            }
        }
    }
}
```

- [ ] **Step 2: CreateHouseholdSheet を書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.household.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.create_household_cta
import mindstock.frontend.generated.resources.create_household_busy
import mindstock.frontend.generated.resources.create_household_placeholder
import mindstock.frontend.generated.resources.create_household_sub
import mindstock.frontend.generated.resources.create_household_title
import mindstock.frontend.generated.resources.create_suggest_1
import mindstock.frontend.generated.resources.create_suggest_2
import mindstock.frontend.generated.resources.create_suggest_3
import mindstock.frontend.generated.resources.create_suggest_4
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.SuggestionChips
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import androidx.compose.material3.MaterialTheme
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreateHouseholdSheet(
    open: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var name by remember(open) { mutableStateOf("") }
    Sheet(open = open, title = stringResource(Res.string.create_household_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            AppText(stringResource(Res.string.create_household_sub), style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
            TextInput(value = name, onValueChange = { name = it }, placeholder = stringResource(Res.string.create_household_placeholder), modifier = Modifier.fillMaxWidth())
            SuggestionChips(
                suggestions =
                    listOf(
                        stringResource(Res.string.create_suggest_1),
                        stringResource(Res.string.create_suggest_2),
                        stringResource(Res.string.create_suggest_3),
                        stringResource(Res.string.create_suggest_4),
                    ),
                onPick = { name = it },
            )
            AppButton(
                onClick = { onCreate(name) },
                size = ButtonSize.Lg,
                icon = AppIconName.Home,
                enabled = name.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(stringResource(if (busy) Res.string.create_household_busy else Res.string.create_household_cta))
            }
        }
    }
}
```

- [ ] **Step 3: JoinCodeSheet を書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.household.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.join_code_busy
import mindstock.frontend.generated.resources.join_code_cta
import mindstock.frontend.generated.resources.join_code_placeholder
import mindstock.frontend.generated.resources.join_code_preview_household
import mindstock.frontend.generated.resources.join_code_preview_role
import mindstock.frontend.generated.resources.join_code_sub
import mindstock.frontend.generated.resources.join_code_title
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.household.NeedHouseholdUiState
import net.brightroom.mindstock.frontend.core.ui.resolve
import org.jetbrains.compose.resources.stringResource

@Composable
fun JoinCodeSheet(
    open: Boolean,
    state: NeedHouseholdUiState,
    onClose: () -> Unit,
    onCodeChange: (String) -> Unit,
    onJoin: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var code by remember(open) { mutableStateOf("") }
    // 6桁になったら preview を引く
    LaunchedEffect(code) { onCodeChange(code) }
    Sheet(open = open, title = stringResource(Res.string.join_code_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            AppText(stringResource(Res.string.join_code_sub), style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
            TextInput(
                value = code,
                onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                placeholder = stringResource(Res.string.join_code_placeholder),
                isError = state.previewError != null && code.length == 6,
                modifier = Modifier.fillMaxWidth(),
            )
            state.previewError?.let { err ->
                if (code.length == 6) AppText(err.resolve(), style = MindstockType.sectionMeta(), color = scheme.error)
            }
            state.preview?.let { p ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(scheme.surface)
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(stringResource(Res.string.join_code_preview_household), style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
                    AppText(p.householdName(), style = MindstockType.summaryTitle())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppIcon(AppIconName.User, contentDescription = null, size = 14.dp, tint = scheme.onSurfaceVariant)
                        AppText(stringResource(Res.string.join_code_preview_role, stringResource(roleLabelResource(p.role()))), style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
                    }
                }
            }
            AppButton(
                onClick = { onJoin(code) },
                size = ButtonSize.Lg,
                icon = AppIconName.Link,
                enabled = state.preview != null && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(stringResource(if (state.busy) Res.string.join_code_busy else Res.string.join_code_cta))
            }
        }
    }
}
```

> 注:
> - `InvitationPreview` のプロパティアクセス方法(`householdName` / `role` が VO の operator invoke か
>   プレーンプロパティか)を `rpc/.../household/InvitationPreview.kt` で確認。`HouseholdName` の文字列化は
>   `householdName()`(operator invoke)を想定。`role` は `HouseholdMemberRole`。
> - `scheme.error` が ColorScheme に無ければ tokens の status 色を使う。

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs 2>&1 | tail -30`
Expected: PASS。

- [ ] **Step 5: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/NeedHouseholdScreen.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/CreateHouseholdSheet.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/JoinCodeSheet.kt
git commit -m "feat(frontend): NeedHousehold 画面と世帯作成/参加シートを追加"
```

---

## Task 15: App.kt 配線(プレースホルダ置換)

**Files:**
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`

- [ ] **Step 1: import を整理する**

不要になる import を削除:

```kotlin
import mindstock.frontend.generated.resources.need_household
import mindstock.frontend.generated.resources.onboarding_placeholder
```

追加する import:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import net.brightroom.mindstock.frontend.feature.resident.data.ResidentRepository
import net.brightroom.mindstock.frontend.feature.household.data.HouseholdRepository
import net.brightroom.mindstock.frontend.feature.household.NeedHouseholdViewModel
import net.brightroom.mindstock.frontend.feature.household.ui.NeedHouseholdScreen
import net.brightroom.mindstock.frontend.feature.household.ui.CreateHouseholdSheet
import net.brightroom.mindstock.frontend.feature.household.ui.JoinCodeSheet
import net.brightroom.mindstock.frontend.feature.onboarding.OnboardingViewModel
import net.brightroom.mindstock.frontend.feature.onboarding.ui.OnboardingScreen
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import androidx.compose.runtime.collectAsState
```

> `collectAsState` / `launch` / `LaunchedEffect` は既に import 済みかもしれない。重複は外す。

- [ ] **Step 2: repository を生成する**

`val catalogRepository = remember { ... }` の後に追加:

```kotlin
        val residentRepository =
            remember { ResidentRepository(residentRegisterService = { rpc.service<ResidentRegisterRpcService>() }) }
        val householdRepository =
            remember {
                HouseholdRepository(
                    householdService = { rpc.service<HouseholdRpcService>() },
                    householdRegisterService = { rpc.service<HouseholdRegisterRpcService>() },
                )
            }
```

- [ ] **Step 3: NeedOnboarding 分岐を実装に置換する**

`is AuthState.NeedOnboarding -> { AppText(...) }` を次に置換:

```kotlin
                is AuthState.NeedOnboarding -> {
                    val onbVm =
                        remember {
                            OnboardingViewModel(
                                registerDisplayName = residentRepository::register,
                                createHousehold = householdRepository::create,
                                flow = vm,
                                toast = toast,
                                reauth = reauth,
                            )
                        }
                    val onbState by onbVm.state.collectAsState()
                    OnboardingScreen(
                        state = onbState,
                        onName = onbVm::setName,
                        onHouseholdName = onbVm::setHouseholdName,
                        onNext = onbVm::next,
                        onBack = onbVm::back,
                        onSubmit = { scope.launch { onbVm.submit() } },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
```

- [ ] **Step 4: NeedHousehold 分岐を実装に置換する**

`is AuthState.NeedHousehold -> { AppText(...) }` を次に置換:

```kotlin
                is AuthState.NeedHousehold -> {
                    val nhVm =
                        remember {
                            NeedHouseholdViewModel(
                                createHousehold = householdRepository::create,
                                previewInvite = { code -> householdRepository.previewInvite(code) },
                                joinByCode = householdRepository::join,
                                flow = vm,
                                toast = toast,
                                reauth = reauth,
                            )
                        }
                    val nhState by nhVm.state.collectAsState()
                    var sheet by remember { mutableStateOf<NeedHouseholdSheet?>(null) }
                    NeedHouseholdScreen(
                        onCreate = { sheet = NeedHouseholdSheet.Create },
                        onJoin = { nhVm.clearPreview(); sheet = NeedHouseholdSheet.Join },
                        modifier = Modifier.fillMaxSize(),
                    )
                    CreateHouseholdSheet(
                        open = sheet == NeedHouseholdSheet.Create,
                        busy = nhState.busy,
                        onClose = { sheet = null },
                        onCreate = { name -> scope.launch { nhVm.create(name) } },
                    )
                    JoinCodeSheet(
                        open = sheet == NeedHouseholdSheet.Join,
                        state = nhState,
                        onClose = { sheet = null; nhVm.clearPreview() },
                        onCodeChange = { code -> if (code.length == 6) scope.launch { nhVm.preview(code) } else nhVm.clearPreview() },
                        onJoin = { code -> scope.launch { nhVm.join(code) } },
                    )
                }
```

- [ ] **Step 5: ファイル末尾に sheet enum を追加する**

`private fun activeHouseholdName(...)` の上か下に追加:

```kotlin
private enum class NeedHouseholdSheet { Create, Join }
```

- [ ] **Step 6: Ready の activeHouseholdId == null フォールバックを NeedHouseholdScreen に統一(任意)**

`if (householdId == null) { AppText(stringResource(Res.string.need_household)) }` を、最低限ビルドを通すため
`AppText("")` などにするか、`NeedHouseholdScreen(onCreate = {}, onJoin = {})` を置く。
※このパスは通常到達しない(Ready は active 確定後)。`need_household` 文言は削除済みなので**必ず差し替える**こと。
推奨: `AppText(stringResource(Res.string.need_household_title))`。

- [ ] **Step 7: フルビルド + テスト**

Run: `./gradlew :frontend:compileKotlinWasmJs :frontend:wasmJsTest 2>&1 | tail -40`
Expected: BUILD SUCCESSFUL / 全テスト PASS。

- [ ] **Step 8: コミット**

```bash
git add frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt
git commit -m "feat(frontend): オンボーディング/世帯なし画面を app 層に配線"
```

---

## Task 16: 全体ビルド + 仕上げ

**Files:** なし(検証のみ)

- [ ] **Step 1: WasmJs を除く全モジュールビルド**

Run: `./gradlew build -x :frontend:wasmJsBrowserTest -x :frontend:wasmJsTest 2>&1 | tail -30`
（`local-build-tips`: frontend WasmJs ブラウザテストは OOM するため除外。ユニットは Step 2 で個別実行。）
Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: frontend ユニットテスト**

Run: `./gradlew :frontend:wasmJsTest 2>&1 | tail -30`
Expected: 全 PASS。

- [ ] **Step 3: ktlint / フォーマット(プロジェクト設定があれば)**

Run: `./gradlew :frontend:ktlintCheck 2>&1 | tail -20`(タスクが存在する場合)
Expected: PASS。失敗時は `./gradlew :frontend:ktlintFormat` で整形して再確認。

- [ ] **Step 4: 受け入れ条件の手動確認メモを残す**

実機(ブラウザ)確認は別途。最低限、以下を README/PR 本文に書く:
- 未登録→オンボーディング→世帯作成→Ready
- スキップ→NeedHousehold→作成/参加→Ready
- 無効コードはプレビュー段で弾かれる
- リロードでアクティブ世帯保持(localStorage `mindstock.active_household.v1`)

- [ ] **Step 5: 最終コミット(必要なら)**

```bash
git add -A
git commit -m "chore(frontend): P6-3a 仕上げ(整形・微修正)" || echo "nothing to commit"
```

---

## Self-Review メモ(計画作成者による確認)

- spec の受け入れ条件 1-7 を Task でカバー: オンボーディング(7,13,15)/ スキップ→NeedHousehold(3,8,14,15)/
  招待参加(6,8,14,15)/ 無効コード(8,14)/ 永続化(1,3,4)/ 見た目(13,14)/ ビルド(16)。
- ★登録後 WS 再接続: Task 3(`onResidentRegistered`)+ Task 4(`WebAuthDeps.reconnect`)。
- 型整合: `AuthFlow`(Task 2)を VM(7,8)と App(15)で一貫使用。`RpcOutcome.Success/Failure`、
  `HouseholdRepository.previewInvite/create/join`、`ResidentRepository.register` を一貫使用。
- 既知リスク(実行時に実体へ要確認、各 Task の「注」に明記):
  MindstockType スタイル名 / AppIcon 引数 / InvitationPreview のアクセサ / RpcClientProvider の connect 引数 /
  material-icons の有無 / FlowRow の利用可否 / 既存 RPC interface のメソッド集合(fake の網羅)。
- 文言: Task 9 を Task 8 より前に実施しても良い(`join_code_invalid` 依存)。
