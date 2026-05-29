package net.brightroom.mindstock.presentation.rpc.household

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User
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
    private val call: ApplicationCall,
    private val database: Database,
) : HouseholdRpcService {
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun findOf(): RpcResult<Household?, RpcError> = tx(database) { RpcResult.Ok(householdService.findOf(actor)) }

    override suspend fun create(): RpcResult<Household, RpcError> = tx(database) { RpcResult.Ok(householdRegisterService.create(actor)) }

    override suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> =
        tx(database) {
            actor
            val household =
                householdRepository.findById(householdId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val user =
                userRepository.findById(invitee)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "user", id = "$invitee"))
            householdRegisterService.invite(household, user, role)
            RpcResult.Ok(Unit)
        }

    override suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ): RpcResult<Unit, RpcError> =
        tx(database) {
            actor
            val household =
                householdRepository.findById(householdId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val user =
                userRepository.findById(target)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "user", id = "$target"))
            householdRegisterService.revoke(household, user)
            RpcResult.Ok(Unit)
        }
}
