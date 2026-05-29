package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.UserId

@Rpc
interface HouseholdRpcService {
    suspend fun findOf(): RpcResult<Household?, RpcError>

    suspend fun create(): RpcResult<Household, RpcError>

    suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError>

    suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ): RpcResult<Unit, RpcError>
}
