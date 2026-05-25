package net.brightroom.mindstock.presentation.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.UserId

@Rpc
interface HouseholdRpcService {
    suspend fun findOf(): Household?

    suspend fun create(): Household

    suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    )

    suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    )
}
