package net.brightroom.mindstock.domain.model.household

import kotlinx.datetime.Instant

public class Household(
    public val id: HouseholdId,
    internal val createdAt: Instant,
)
