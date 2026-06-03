package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class HouseholdRegisterService(
    private val residentRepository: ResidentRepository,
    private val householdRepository: HouseholdRepository,
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun create(
        name: HouseholdName,
        actor: ResidentId,
    ): Household {
        val owner = residentRepository.findById(actor)
        val household = Household.create(name, owner)
        householdRegisterRepository.registerHousehold(household)
        return household
    }

    fun rename(
        householdId: HouseholdId,
        name: HouseholdName,
        actor: ResidentId,
    ) {
        val household = householdRepository.findById(householdId)
        household.rename(name, actor)
        householdRegisterRepository.appendHouseholdName(householdId, name)
    }

    fun leave(
        householdId: HouseholdId,
        actor: ResidentId,
    ) {
        val household = householdRepository.findById(householdId)
        household.leave(actor)
        householdRegisterRepository.removeMember(householdId, actor)
    }

    fun changeRole(
        householdId: HouseholdId,
        target: ResidentId,
        role: HouseholdMemberRole,
        actor: ResidentId,
    ) {
        val household = householdRepository.findById(householdId)
        household.changeRole(target, role, actor)
        householdRegisterRepository.changeMemberRole(householdId, target, role)
    }

    fun removeMember(
        householdId: HouseholdId,
        target: ResidentId,
        actor: ResidentId,
    ) {
        val household = householdRepository.findById(householdId)
        household.removeMember(target, actor)
        householdRegisterRepository.removeMember(householdId, target)
    }

    /** Scenario(join)用。invitation の有効性確認・actor 解決は呼び出し元(JoinHouseholdScenario)が担う。 */
    fun join(
        householdId: HouseholdId,
        resident: Resident,
        grantedRole: HouseholdMemberRole,
    ): Household {
        val household = householdRepository.findById(householdId)
        val joined = household.join(resident, grantedRole)
        householdRegisterRepository.joinMember(householdId, resident, grantedRole)
        return joined
    }
}
