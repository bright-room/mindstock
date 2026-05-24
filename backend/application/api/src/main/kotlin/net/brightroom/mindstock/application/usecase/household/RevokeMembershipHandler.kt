package net.brightroom.mindstock.application.usecase.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRegisterRepository

class RevokeMembershipHandler(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun handle(
        household: Household,
        user: User,
    ) {
        householdRegisterRepository.revoke(household, user)
    }
}
