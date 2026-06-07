package net.brightroom.mindstock.frontend.feature.household.data

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.household.InvitationPreview

/** 世帯の作成・参加・招待プレビューまわりの RPC を隠蔽。サービスは opener として遅延注入する。 */
class HouseholdRepository(
    private val householdService: () -> HouseholdRpcService,
    private val householdRegisterService: () -> HouseholdRegisterRpcService,
) {
    suspend fun create(name: HouseholdName): RpcOutcome<Household> = householdRegisterService().create(name).toOutcome()

    suspend fun join(code: InvitationCode): RpcOutcome<Household> = householdRegisterService().join(code).toOutcome()

    suspend fun previewInvite(code: InvitationCode): RpcOutcome<InvitationPreview> = householdService().previewInvite(code).toOutcome()

    suspend fun rename(
        householdId: HouseholdId,
        name: HouseholdName,
    ): RpcOutcome<Unit> = householdRegisterService().rename(householdId, name).toOutcome()

    suspend fun leave(householdId: HouseholdId): RpcOutcome<Unit> = householdRegisterService().leave(householdId).toOutcome()

    suspend fun changeRole(
        householdId: HouseholdId,
        target: ResidentId,
        role: HouseholdMemberRole,
    ): RpcOutcome<Unit> = householdRegisterService().changeRole(householdId, target, role).toOutcome()

    suspend fun removeMember(
        householdId: HouseholdId,
        target: ResidentId,
    ): RpcOutcome<Unit> = householdRegisterService().removeMember(householdId, target).toOutcome()

    suspend fun createInvite(
        householdId: HouseholdId,
        role: HouseholdMemberRole,
    ): RpcOutcome<Invitation> = householdRegisterService().createInvite(householdId, role).toOutcome()

    suspend fun revokeInvite(code: InvitationCode): RpcOutcome<Unit> = householdRegisterService().revokeInvite(code).toOutcome()
}
