package net.brightroom.mindstock.frontend.feature.onboarding

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

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

    override fun switchActiveHousehold(id: HouseholdId) {}

    override suspend fun refreshHouseholds() {}

    override fun applyDisplayName(name: DisplayName) {}

    override suspend fun leaveActiveHousehold() {}
}

private fun resident() = Resident(ResidentId.create(), ResidentProfile(DisplayName("たろう")))

private fun household() = Household(HouseholdId.create(), HouseholdProfile(HouseholdName("家")), Members(emptyList()))

private fun vm(
    register: suspend (DisplayName) -> RpcOutcome<Resident> = { RpcOutcome.Success(resident()) },
    create: suspend (HouseholdName) -> RpcOutcome<Household> = { RpcOutcome.Success(household()) },
    flow: AuthFlow = FakeAuthFlow(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = OnboardingViewModel(register, create, flow, toast, reauth)

class OnboardingViewModelTest {
    @Test
    fun submit_with_household_registers_creates_and_enters() =
        runTest {
            val flow = FakeAuthFlow()
            val h = household()
            val v = vm(create = { RpcOutcome.Success(h) }, flow = flow)
            v.setName("たろう")
            v.setHouseholdName("わたしの家")
            v.submit()
            flow.registered.shouldNotBeNull()
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
            flow.registered.shouldNotBeNull()
            flow.needHouseholdCalled.shouldBeTrue()
            flow.enteredId.shouldBeNull()
        }

    @Test
    fun submit_register_failure_keeps_step_and_does_not_register() =
        runTest {
            val flow = FakeAuthFlow()
            val v = vm(register = { RpcOutcome.Failure(RpcError.Internal("boom")) }, flow = flow)
            v.setName("たろう")
            v.setHouseholdName("わたしの家")
            v.submit()
            flow.registered.shouldBeNull()
            flow.enteredId.shouldBeNull()
            v.state.value.submitting shouldBe false
        }

    @Test
    fun submit_register_unauthorized_does_not_enter() =
        runTest {
            val flow = FakeAuthFlow()
            val v = vm(register = { RpcOutcome.Failure(RpcError.Unauthorized("expired")) }, flow = flow)
            v.setName("たろう")
            v.submit()
            flow.registered.shouldBeNull()
            flow.enteredId.shouldBeNull()
            v.state.value.submitting shouldBe false
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
