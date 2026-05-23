package net.brightroom.mindstock.domain.model.household

import net.brightroom.mindstock.domain.model.user.UserId
import kotlin.time.Instant

class HouseholdMembership(
    val id: HouseholdMembershipId,
    internal val householdId: HouseholdId,
    internal val userId: UserId,
    internal val role: HouseholdMemberRole,
    internal val createdAt: Instant,
)
