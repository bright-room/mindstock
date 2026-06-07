package net.brightroom.mindstock.frontend.app

import io.kotest.matchers.shouldBe
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
class AuthViewModelSwitchTest {
    private fun resident(name: String = "わたし") = Resident(ResidentId.create(), Profile(DisplayName(name)))

    private fun household(
        id: HouseholdId,
        name: String,
    ) = Household(id, HouseholdProfile(HouseholdName(name)), Members(emptyList()))

    private class FakeDeps(
        var households: Households,
        var active: HouseholdId?,
    ) : AuthDeps {
        val persisted = mutableListOf<HouseholdId>()
        var displayName: DisplayName? = null
        var clearedTo: Households? = null

        override fun currentPath(): String = "/"

        override suspend fun handleCallback() = Unit

        override fun loadValidToken(): Tokens? =
            Tokens(
                accessToken = "tok",
                refreshToken = "refresh",
                idToken = "id",
                expiresAt = Instant.fromEpochSeconds(Long.MAX_VALUE / 2),
            )

        override suspend fun redirectToAuthorize() = Unit

        override suspend fun fetchSessionStatus(token: Tokens): SessionStatus = error("unused")

        override fun onAuthenticated(resident: Resident) = Unit

        override suspend fun loadHouseholds(): Households = households

        override fun onHouseholdsLoaded(
            households: Households,
            active: HouseholdId,
        ) {
            this.households = households
            this.active = active
        }

        override suspend fun reconnect(token: Tokens) = Unit

        override fun persistActiveHousehold(id: HouseholdId) {
            persisted += id
            active = id
        }

        override fun savedActiveHousehold(): HouseholdId? = active

        override fun setActiveHousehold(id: HouseholdId) {
            active = id
        }

        override fun setDisplayName(name: DisplayName) {
            displayName = name
        }

        override fun currentActiveHousehold(): HouseholdId? = active

        override fun onHouseholdsCleared(households: Households) {
            clearedTo = households
            active = null
        }
    }

    @Test
    fun switchActiveHousehold_sets_and_persists() {
        val h1 = household(HouseholdId.create(), "家1")
        val h2 = household(HouseholdId.create(), "家2")
        val deps = FakeDeps(Households(listOf(h1, h2)), active = h1.id)
        val vm = AuthViewModel(deps)
        vm.switchActiveHousehold(h2.id)
        deps.active shouldBe h2.id
        deps.persisted shouldBe listOf(h2.id)
    }

    @Test
    fun applyDisplayName_updates_session() {
        val h1 = household(HouseholdId.create(), "家1")
        val deps = FakeDeps(Households(listOf(h1)), active = h1.id)
        val vm = AuthViewModel(deps)
        vm.applyDisplayName(DisplayName("あたらしい"))
        deps.displayName shouldBe DisplayName("あたらしい")
    }

    @Test
    fun leaveActiveHousehold_picks_remaining_when_active_gone() =
        runTest {
            val h1 = household(HouseholdId.create(), "家1")
            val h2 = household(HouseholdId.create(), "家2")
            // after leave, list = h2 only; current active (h1) is gone
            val deps = FakeDeps(Households(listOf(h2)), active = h1.id)
            val vm = AuthViewModel(deps)
            vm.leaveActiveHousehold()
            deps.active shouldBe h2.id
            deps.persisted shouldBe listOf(h2.id)
        }

    @Test
    fun leaveActiveHousehold_goes_need_household_when_empty() =
        runTest {
            val h1 = household(HouseholdId.create(), "家1")
            val deps = FakeDeps(Households(emptyList()), active = h1.id)
            val vm = AuthViewModel(deps)
            vm.leaveActiveHousehold()
            deps.clearedTo shouldBe Households(emptyList())
            vm.state.value shouldBe AuthState.NeedHousehold
        }

    @Test
    fun refreshHouseholds_keeps_current_active() =
        runTest {
            val id = HouseholdId.create()
            val renamed = household(id, "家1改")
            val deps = FakeDeps(Households(listOf(renamed)), active = id)
            val vm = AuthViewModel(deps)
            vm.refreshHouseholds()
            deps.active shouldBe id
            deps.households shouldBe Households(listOf(renamed))
        }
}
