package net.brightroom.mindstock.presentation.rpc.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.rpc.HouseholdRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class HouseholdController(
    private val householdService: HouseholdService,
    private val householdRegisterService: HouseholdRegisterService,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val session: MindstockSession,
    private val database: Database,
) : HouseholdRpcService {
    override suspend fun findOf(): RpcResult<Household?, RpcError> =
        tx(database, session) { RpcResult.Ok(householdService.findOf(requireNotNull(session.userId))) }

    override suspend fun create(): RpcResult<Household, RpcError> =
        tx(database, session) { RpcResult.Ok(householdRegisterService.create(requireNotNull(session.userId))) }

    override suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val household =
                householdRepository.findById(householdId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val inviteeProfile =
                userRepository.findProfileById(invitee)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "user", id = "$invitee"))
            householdRegisterService.invite(household, inviteeProfile.userId, role)
            RpcResult.Ok(Unit)
        }

    override suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val household =
                householdRepository.findById(householdId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val targetProfile =
                userRepository.findProfileById(target)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "user", id = "$target"))
            householdRegisterService.revoke(household, targetProfile.userId)
            RpcResult.Ok(Unit)
        }
}
