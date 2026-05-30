package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.UserId

class HouseholdRegisterService(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun create(
        ownerId: UserId,
        name: HouseholdName,
    ): Household = householdRegisterRepository.create(ownerId, name)

    fun invite(
        household: Household,
        userId: UserId,
        role: HouseholdMemberRole,
    ) {
        householdRegisterRepository.invite(household, userId, role)
    }

    fun revoke(
        household: Household,
        userId: UserId,
    ) {
        householdRegisterRepository.revoke(household, userId)
    }
}
