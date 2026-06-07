package net.brightroom.mindstock.frontend.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName

/** ログイン中の住人とアクティブ世帯。画面横断で参照する単一の真実。 */
class AppSession {
    data class State(
        val residentId: ResidentId? = null,
        val displayName: DisplayName? = null,
        val households: Households? = null,
        val activeHouseholdId: HouseholdId? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setResident(
        residentId: ResidentId,
        displayName: DisplayName,
    ) = _state.update { it.copy(residentId = residentId, displayName = displayName) }

    fun setDisplayName(displayName: DisplayName) = _state.update { it.copy(displayName = displayName) }

    fun setHouseholds(
        households: Households,
        active: HouseholdId?,
    ) = _state.update { it.copy(households = households, activeHouseholdId = active) }

    fun setActiveHousehold(id: HouseholdId) = _state.update { it.copy(activeHouseholdId = id) }

    fun clear() {
        _state.value = State()
    }
}
