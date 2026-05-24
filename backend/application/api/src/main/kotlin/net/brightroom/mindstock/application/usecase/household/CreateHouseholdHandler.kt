package net.brightroom.mindstock.application.usecase.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRegisterRepository

class CreateHouseholdHandler(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun handle(owner: User): Household = householdRegisterRepository.create(owner)
}
