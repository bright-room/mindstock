package net.brightroom.mindstock.rpc.household

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface HouseholdRegisterRpcService {
    /** 世帯を作成する(UC3)。作成者が owner。actor は session 由来。 */
    suspend fun create(name: HouseholdName): RpcResult<Household, RpcError>

    /** 世帯名を変更する(UC6, owner のみ)。 */
    suspend fun rename(
        householdId: HouseholdId,
        name: HouseholdName,
    ): RpcResult<Unit, RpcError>

    /** 世帯から退出する(UC7)。 */
    suspend fun leave(householdId: HouseholdId): RpcResult<Unit, RpcError>

    /** メンバーの権限変更(UC9, owner のみ)。 */
    suspend fun changeRole(
        householdId: HouseholdId,
        target: ResidentId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError>

    /** メンバーを除外(UC9, owner のみ)。 */
    suspend fun removeMember(
        householdId: HouseholdId,
        target: ResidentId,
    ): RpcResult<Unit, RpcError>

    /** 招待を発行/再発行する(UC8, owner のみ。role 指定・期限なし)。 */
    suspend fun createInvite(
        householdId: HouseholdId,
        role: HouseholdMemberRole,
    ): RpcResult<Invitation, RpcError>

    /** 招待を失効する(UC8, owner のみ)。 */
    suspend fun revokeInvite(code: InvitationCode): RpcResult<Unit, RpcError>

    /** 招待コードで世帯に参加する(UC4)。 */
    suspend fun join(code: InvitationCode): RpcResult<Household, RpcError>
}
