package net.brightroom.mindstock.frontend.core.session

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import kotlin.test.Test

class AppSessionTest {
    @Test
    fun selectHousehold_updates_active() =
        runTest {
            val a = HouseholdId.create()
            val b = HouseholdId.create()
            val session = AppSession()
            session.setActiveHousehold(a)
            session.state.value.activeHouseholdId shouldBe a
            session.setActiveHousehold(b)
            session.state.value.activeHouseholdId shouldBe b
        }

    @Test
    fun clear_resets_state() =
        runTest {
            val session = AppSession()
            session.setActiveHousehold(HouseholdId.create())
            session.clear()
            session.state.value.activeHouseholdId shouldBe null
        }
}
