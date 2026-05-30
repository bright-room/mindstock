package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.UserId

class HouseholdRegisterService(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    suspend fun create(ownerId: UserId): Household = householdRegisterRepository.create(ownerId)

    suspend fun invite(
        household: Household,
        userId: UserId,
        role: HouseholdMemberRole,
    ) {
        householdRegisterRepository.invite(household, userId, role)
    }

    suspend fun revoke(
        household: Household,
        userId: UserId,
    ) {
        householdRegisterRepository.revoke(household, userId)
    }
}
