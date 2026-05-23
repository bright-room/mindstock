package net.brightroom.mindstock.domain.model.household

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

class HouseholdMembership(
    val id: HouseholdMembershipId,
    internal val householdId: HouseholdId,
    internal val userId: UserId,
    internal val role: HouseholdMemberRole,
    internal val createdAt: Instant,
)
