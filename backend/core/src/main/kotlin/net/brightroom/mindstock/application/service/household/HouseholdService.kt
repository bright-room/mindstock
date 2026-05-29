package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.UserId

class HouseholdService(
    private val householdRepository: HouseholdRepository,
) {
    fun findOf(userId: UserId): Household? = householdRepository.findOf(userId)
}
