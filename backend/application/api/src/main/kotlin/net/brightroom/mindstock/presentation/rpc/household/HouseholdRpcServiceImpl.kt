package net.brightroom.mindstock.presentation.rpc.household

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.usecase.household.CreateHouseholdHandler
import net.brightroom.mindstock.application.usecase.household.FindHouseholdOfUserHandler
import net.brightroom.mindstock.application.usecase.household.InviteMemberHandler
import net.brightroom.mindstock.application.usecase.household.RevokeMembershipHandler
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.error.NotFoundException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.presentation.rpc.HouseholdRpcService

class HouseholdRpcServiceImpl(
    private val findHouseholdOfUser: FindHouseholdOfUserHandler,
    private val createHousehold: CreateHouseholdHandler,
    private val inviteMember: InviteMemberHandler,
    private val revokeMembership: RevokeMembershipHandler,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
) : HouseholdRpcService {
    override suspend fun findOf(): Household? {
        val actor = call.actor(userRepository)
        return findHouseholdOfUser.handle(actor)
    }

    override suspend fun create(): Household {
        val actor = call.actor(userRepository)
        return createHousehold.handle(actor)
    }

    override suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ) {
        call.actor(userRepository)
        val household =
            householdRepository.findById(householdId)
                ?: throw NotFoundException("household not found: $householdId")
        val user =
            userRepository.findById(invitee)
                ?: throw NotFoundException("user not found: $invitee")
        inviteMember.handle(household, user, role)
    }

    override suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ) {
        call.actor(userRepository)
        val household =
            householdRepository.findById(householdId)
                ?: throw NotFoundException("household not found: $householdId")
        val user =
            userRepository.findById(target)
                ?: throw NotFoundException("user not found: $target")
        revokeMembership.handle(household, user)
    }
}
