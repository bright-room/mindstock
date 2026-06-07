package net.brightroom.mindstock.frontend.app

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.frontend.auth.Tokens
import net.brightroom.mindstock.frontend.core.auth.AuthState
import net.brightroom.mindstock.rpc.session.SessionStatus
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import net.brightroom.mindstock.domain.model.household.Profile as HouseholdProfile

@OptIn(ExperimentalTime::class)
private class FakeAuthDeps(
    private val path: String,
    private val token: String?,
    private val status: SessionStatus? = null,
    private val households: Households = Households(emptyList()),
    private val failHouseholds: Boolean = false,
    private val savedActive: HouseholdId? = null,
) : AuthDeps {
    var redirectCalled = false
    var onAuthenticatedCalled = false
    var setHouseholdsCalled: Households? = null
    var reconnectCalled = false
    var persistedActive: HouseholdId? = null

    override fun currentPath(): String = path

    override suspend fun handleCallback() {
        // no-op for tests not on callback path
    }

    override fun loadValidToken(): Tokens? =
        token?.let {
            Tokens(
                accessToken = it,
                refreshToken = "refresh",
                idToken = "id",
                expiresAt = Instant.fromEpochSeconds(Long.MAX_VALUE / 2),
            )
        }

    override suspend fun redirectToAuthorize() {
        redirectCalled = true
    }

    override suspend fun fetchSessionStatus(token: Tokens): SessionStatus = status ?: throw RuntimeException("boot failed")

    override fun onAuthenticated(resident: Resident) {
        onAuthenticatedCalled = true
    }

    override suspend fun loadHouseholds(): Households = if (failHouseholds) throw RuntimeException("household load failed") else households

    override fun onHouseholdsLoaded(
        households: Households,
        active: HouseholdId,
    ) {
        setHouseholdsCalled = households
    }

    override suspend fun reconnect(token: Tokens) {
        reconnectCalled = true
    }

    override fun persistActiveHousehold(id: HouseholdId) {
        persistedActive = id
    }

    override fun savedActiveHousehold(): HouseholdId? = savedActive
}

class AuthViewModelTest {
    @Test
    fun no_token_redirects_to_authorize_and_stays_booting() =
        runTest {
            val deps = FakeAuthDeps(path = "/", token = null)
            val vm = AuthViewModel(deps)
            vm.boot()
            deps.redirectCalled shouldBe true
            vm.state.value.shouldBeInstanceOf<AuthState.Booting>()
        }

    @Test
    fun registered_becomes_ready() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
            val hh = Household(HouseholdId.create(), HouseholdProfile(HouseholdName("家")), Members(emptyList()))
            val deps =
                FakeAuthDeps(
                    path = "/",
                    token = "tok",
                    status = SessionStatus.Registered(resident),
                    households = Households(listOf(hh)),
                )
            val vm = AuthViewModel(deps)
            vm.boot()
            deps.onAuthenticatedCalled shouldBe true
            deps.setHouseholdsCalled.shouldNotBeNull().size() shouldBe 1
            vm.state.value.shouldBeInstanceOf<AuthState.Ready>()
        }

    @Test
    fun registered_without_household_becomes_need_household() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
            val deps =
                FakeAuthDeps(
                    path = "/",
                    token = "tok",
                    status = SessionStatus.Registered(resident),
                    households = Households(emptyList()),
                )
            val vm = AuthViewModel(deps)
            vm.boot()
            vm.state.value.shouldBeInstanceOf<AuthState.NeedHousehold>()
        }

    @Test
    fun unregistered_becomes_onboarding() =
        runTest {
            val deps = FakeAuthDeps(path = "/", token = "tok", status = SessionStatus.Unregistered)
            val vm = AuthViewModel(deps)
            vm.boot()
            vm.state.value.shouldBeInstanceOf<AuthState.NeedOnboarding>()
        }

    @Test
    fun whoami_failure_becomes_failed() =
        runTest {
            val deps = FakeAuthDeps(path = "/", token = "tok", status = null)
            val vm = AuthViewModel(deps)
            vm.boot()
            vm.state.value.shouldBeInstanceOf<AuthState.Failed>()
        }

    @Test
    fun household_load_failure_becomes_failed() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
            val deps =
                FakeAuthDeps(
                    path = "/",
                    token = "tok",
                    status = SessionStatus.Registered(resident),
                    failHouseholds = true,
                )
            val vm = AuthViewModel(deps)
            vm.boot()
            vm.state.value.shouldBeInstanceOf<AuthState.Failed>()
        }

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
                    savedActive = HouseholdId.create(),
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
}
