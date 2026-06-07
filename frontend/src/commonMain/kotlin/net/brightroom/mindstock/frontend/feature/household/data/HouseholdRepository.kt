package net.brightroom.mindstock.frontend.feature.household.data

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.household.InvitationPreview

/**
 * 世帯の作成・参加・招待プレビューまわりの RPC を隠蔽。サービスは opener として遅延注入する。
 * P6-3b で rename / leave / changeRole / removeMember / createInvite / revokeInvite を追加予定。
 */
class HouseholdRepository(
    private val householdService: () -> HouseholdRpcService,
    private val householdRegisterService: () -> HouseholdRegisterRpcService,
) {
    suspend fun create(name: HouseholdName): RpcOutcome<Household> = householdRegisterService().create(name).toOutcome()

    suspend fun join(code: InvitationCode): RpcOutcome<Household> = householdRegisterService().join(code).toOutcome()

    suspend fun previewInvite(code: InvitationCode): RpcOutcome<InvitationPreview> = householdService().previewInvite(code).toOutcome()
}
