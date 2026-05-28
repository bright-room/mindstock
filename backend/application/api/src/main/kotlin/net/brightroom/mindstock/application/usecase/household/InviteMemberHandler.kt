package net.brightroom.mindstock.application.usecase.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User

class InviteMemberHandler(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun handle(
        household: Household,
        user: User,
        role: HouseholdMemberRole,
    ) {
        householdRegisterRepository.invite(household, user, role)
    }
}
