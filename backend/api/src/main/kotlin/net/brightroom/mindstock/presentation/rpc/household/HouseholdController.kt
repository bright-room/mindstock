package net.brightroom.mindstock.presentation.rpc.household

import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.rpc.HouseholdRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class HouseholdController(
    private val householdService: HouseholdService,
    private val householdRegisterService: HouseholdRegisterService,
    private val userService: UserService,
    private val session: MindstockSession,
    private val database: Database,
) : HouseholdRpcService {
    override suspend fun findOf(): RpcResult<Household, RpcError> =
        tx(database, session) { RpcResult.Ok(householdService.findOf(requireNotNull(session.userId))) }

    override suspend fun create(): RpcResult<Household, RpcError> =
        tx(database, session) {
            // TODO: onboarding task で RPC 経由の世帯名(HouseholdRpcService.create 引数)に差し替える。暫定で固定名。
            RpcResult.Ok(householdRegisterService.create(requireNotNull(session.userId), HouseholdName("マイ世帯")))
        }

    override suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            val inviteeProfile = userService.findById(invitee)
            householdRegisterService.invite(household, inviteeProfile.userId, role)
            RpcResult.Ok(Unit)
        }

    override suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            val targetProfile = userService.findById(target)
            householdRegisterService.revoke(household, targetProfile.userId)
            RpcResult.Ok(Unit)
        }
}
