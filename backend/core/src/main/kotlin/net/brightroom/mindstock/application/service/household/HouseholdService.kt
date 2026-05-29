package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.User

class HouseholdService(
    private val householdRepository: HouseholdRepository,
) {
    fun findOf(user: User): Household? = householdRepository.findOf(user)
}
