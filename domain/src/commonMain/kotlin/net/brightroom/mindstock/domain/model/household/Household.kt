package net.brightroom.mindstock.domain.model.household

import kotlinx.datetime.Instant

class Household(
    val id: HouseholdId,
    internal val createdAt: Instant,
)
