package net.brightroom.mindstock.domain.model.household

import kotlinx.datetime.Instant

class HouseholdMembershipRevocation(
    val id: HouseholdMembershipRevocationId,
    internal val membershipId: HouseholdMembershipId,
    internal val createdAt: Instant,
)
