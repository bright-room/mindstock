package net.brightroom.mindstock.presentation.rpc.household

import net.brightroom.mindstock.application.scenario.household.JoinHouseholdScenario
import net.brightroom.mindstock.application.scenario.invitation.CreateInvitationScenario
import net.brightroom.mindstock.application.scenario.invitation.RevokeInvitationScenario
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.requireRegistered
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class HouseholdRegisterController(
    private val householdRegisterService: HouseholdRegisterService,
    private val createInvitationScenario: CreateInvitationScenario,
    private val revokeInvitationScenario: RevokeInvitationScenario,
    private val joinHouseholdScenario: JoinHouseholdScenario,
    private val session: MindstockSession,
) : HouseholdRegisterRpcService {
    override suspend fun create(name: HouseholdName): RpcResult<Household, RpcError> =
        requireRegistered(session) { residentId -> RpcResult.Ok(householdRegisterService.create(name, residentId)) }

    override suspend fun rename(
        householdId: HouseholdId,
        name: HouseholdName,
    ): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            householdRegisterService.rename(householdId, name, residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun leave(householdId: HouseholdId): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            householdRegisterService.leave(householdId, residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun changeRole(
        householdId: HouseholdId,
        target: ResidentId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            householdRegisterService.changeRole(householdId, target, role, residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun removeMember(
        householdId: HouseholdId,
        target: ResidentId,
    ): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            householdRegisterService.removeMember(householdId, target, residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun createInvite(
        householdId: HouseholdId,
        role: HouseholdMemberRole,
    ): RpcResult<Invitation, RpcError> =
        requireRegistered(session) { residentId -> RpcResult.Ok(createInvitationScenario.run(householdId, role, residentId)) }

    override suspend fun revokeInvite(code: InvitationCode): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            revokeInvitationScenario.run(code, residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun join(code: InvitationCode): RpcResult<Household, RpcError> =
        requireRegistered(session) { residentId -> RpcResult.Ok(joinHouseholdScenario.run(code, residentId)) }
}
