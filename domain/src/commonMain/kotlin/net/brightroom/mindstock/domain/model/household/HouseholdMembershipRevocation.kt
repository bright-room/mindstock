package net.brightroom.mindstock.domain.model.household

import kotlin.time.Instant

class HouseholdMembershipRevocation(
    val id: HouseholdMembershipRevocationId,
    internal val membershipId: HouseholdMembershipId,
    internal val createdAt: Instant,
)
