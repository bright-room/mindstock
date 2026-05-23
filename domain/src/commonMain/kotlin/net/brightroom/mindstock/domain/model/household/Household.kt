package net.brightroom.mindstock.domain.model.household

import kotlin.time.Instant

class Household(
    val id: HouseholdId,
    internal val createdAt: Instant,
)
