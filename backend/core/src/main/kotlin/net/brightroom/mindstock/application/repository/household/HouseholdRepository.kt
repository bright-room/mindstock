package net.brightroom.mindstock.application.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

interface HouseholdRepository {
    fun findById(id: HouseholdId): Household

    /** resident が current メンバーである世帯一覧(空なら空 Households)。 */
    fun listByResident(residentId: ResidentId): Households
}
