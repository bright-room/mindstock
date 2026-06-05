package net.brightroom.mindstock.presentation.rpc.household

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.requireRegistered
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.household.InvitationPreview
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class HouseholdController(
    private val householdService: HouseholdService,
    private val invitationService: InvitationService,
    private val session: MindstockSession,
) : HouseholdRpcService {
    override suspend fun list(): RpcResult<Households, RpcError> =
        requireRegistered(session) { residentId -> RpcResult.Ok(householdService.list(residentId)) }

    override suspend fun previewInvite(code: InvitationCode): RpcResult<InvitationPreview, RpcError> =
        requireRegistered(session) { _ ->
            val invitation = invitationService.findByCode(code)
            val household = householdService.findById(invitation.householdId)
            RpcResult.Ok(InvitationPreview(household.profile.name, invitation.grantedRole))
        }
}
