package net.brightroom.mindstock.presentation.rpc.household

import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.rpc.rpcBoundary
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.rpc.HouseholdRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult

class HouseholdController(
    private val householdService: HouseholdService,
    private val householdRegisterService: HouseholdRegisterService,
    private val userService: UserService,
    private val session: MindstockSession,
) : HouseholdRpcService {
    override suspend fun findOf(): RpcResult<Household, RpcError> =
        rpcBoundary(session) { householdService.findOf(requireNotNull(session.userId)) }

    override suspend fun create(): RpcResult<Household, RpcError> =
        rpcBoundary(session) { householdRegisterService.create(requireNotNull(session.userId)) }

    override suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> =
        rpcBoundary(session) {
            val household = householdService.findById(householdId)
            val inviteeProfile = userService.findById(invitee)
            householdRegisterService.invite(household, inviteeProfile.userId, role)
        }

    override suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ): RpcResult<Unit, RpcError> =
        rpcBoundary(session) {
            val household = householdService.findById(householdId)
            val targetProfile = userService.findById(target)
            householdRegisterService.revoke(household, targetProfile.userId)
        }
}
