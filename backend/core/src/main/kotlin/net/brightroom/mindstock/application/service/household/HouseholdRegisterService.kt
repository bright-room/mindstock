package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User

class HouseholdRegisterService(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun create(owner: User): Household = householdRegisterRepository.create(owner)

    fun invite(
        household: Household,
        user: User,
        role: HouseholdMemberRole,
    ) {
        householdRegisterRepository.invite(household, user, role)
    }

    fun revoke(
        household: Household,
        user: User,
    ) {
        householdRegisterRepository.revoke(household, user)
    }
}
