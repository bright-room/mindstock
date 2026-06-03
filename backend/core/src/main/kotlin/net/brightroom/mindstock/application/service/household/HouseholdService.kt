package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class HouseholdService(
    private val householdRepository: HouseholdRepository,
) {
    fun list(actor: ResidentId): Households = householdRepository.listByResident(actor)

    /** 内部用(RPC では非公開)。previewInvite 組立・Scenario の owner 認可で使う。 */
    fun findById(householdId: HouseholdId): Household = householdRepository.findById(householdId)
}
