package net.brightroom.mindstock.application.usecase.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository

class FindHouseholdOfUserHandler(
    private val householdRepository: HouseholdRepository,
) {
    fun handle(user: User): Household? = householdRepository.findOf(user)
}
