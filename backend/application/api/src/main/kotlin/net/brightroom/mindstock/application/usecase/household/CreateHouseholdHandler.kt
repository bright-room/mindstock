package net.brightroom.mindstock.application.usecase.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.User

class CreateHouseholdHandler(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun handle(owner: User): Household = householdRegisterRepository.create(owner)
}
