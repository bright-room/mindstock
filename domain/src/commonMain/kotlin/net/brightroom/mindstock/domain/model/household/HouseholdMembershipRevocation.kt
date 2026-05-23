package net.brightroom.mindstock.domain.model.household

import kotlinx.datetime.Instant

public class HouseholdMembershipRevocation(
    public val id: HouseholdMembershipRevocationId,
    internal val membershipId: HouseholdMembershipId,
    internal val createdAt: Instant,
)
