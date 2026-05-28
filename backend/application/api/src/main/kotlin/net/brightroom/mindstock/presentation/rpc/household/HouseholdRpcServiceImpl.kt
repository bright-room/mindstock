package net.brightroom.mindstock.presentation.rpc.household

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.error.NotFoundException
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.presentation.rpc.HouseholdRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class HouseholdRpcServiceImpl(
    private val householdService: HouseholdService,
    private val householdRegisterService: HouseholdRegisterService,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
    private val database: Database,
) : HouseholdRpcService {
    // Memoized for the lifetime of this per-connection Service Impl.
    // NOTE: a rename within the same connection won't refresh this cache until reconnect.
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun findOf(): Household? = tx(database) { householdService.findOf(actor) }

    override suspend fun create(): Household = tx(database) { householdRegisterService.create(actor) }

    override suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ) = tx(database) {
        // TODO(authz): verify actor is a member of household $householdId
        actor
        val household =
            householdRepository.findById(householdId)
                ?: throw NotFoundException("household not found: $householdId")
        val user =
            userRepository.findById(invitee)
                ?: throw NotFoundException("user not found: $invitee")
        householdRegisterService.invite(household, user, role)
    }

    override suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ) = tx(database) {
        // TODO(authz): verify actor is a member of household $householdId
        actor
        val household =
            householdRepository.findById(householdId)
                ?: throw NotFoundException("household not found: $householdId")
        val user =
            userRepository.findById(target)
                ?: throw NotFoundException("user not found: $target")
        householdRegisterService.revoke(household, user)
    }
}
