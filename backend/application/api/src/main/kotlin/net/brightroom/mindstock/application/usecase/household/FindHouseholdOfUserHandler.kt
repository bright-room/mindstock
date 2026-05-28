package net.brightroom.mindstock.application.usecase.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.User

class FindHouseholdOfUserHandler(
    private val householdRepository: HouseholdRepository,
) {
    fun handle(user: User): Household? = householdRepository.findOf(user)
}
