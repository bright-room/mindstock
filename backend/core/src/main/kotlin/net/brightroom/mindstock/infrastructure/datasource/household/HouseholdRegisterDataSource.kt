@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdMembershipEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class HouseholdRegisterDataSource(
    private val database: Database,
) : HouseholdRegisterRepository {
    override fun registerHousehold(household: Household): Household =
        transaction(database) {
            HouseholdsTable.insert { it[id] = household.id() }
            HouseholdNamesTable.insert {
                it[householdId] = household.id()
                it[name] = household.profile.name()
            }
            household.members.list.forEach { m ->
                HouseholdMembershipEventsTable.insert {
                    it[householdId] = household.id()
                    it[residentId] = m.resident.id()
                    it[role] = m.role
                    it[status] = HouseholdMembershipEventsTable.STATUS_ACTIVE
                }
            }
            household
        }

    override fun appendHouseholdName(
        householdId: HouseholdId,
        name: HouseholdName,
    ) {
        transaction(database) {
            HouseholdNamesTable.insert {
                it[HouseholdNamesTable.householdId] = householdId()
                it[HouseholdNamesTable.name] = name()
            }
        }
    }

    override fun joinMember(
        householdId: HouseholdId,
        resident: Resident,
        role: HouseholdMemberRole,
    ) {
        transaction(database) {
            HouseholdMembershipEventsTable.insert {
                it[HouseholdMembershipEventsTable.householdId] = householdId()
                it[residentId] = resident.id()
                it[HouseholdMembershipEventsTable.role] = role
                it[status] = HouseholdMembershipEventsTable.STATUS_ACTIVE
            }
        }
    }

    override fun changeMemberRole(
        householdId: HouseholdId,
        residentId: ResidentId,
        role: HouseholdMemberRole,
    ) {
        transaction(database) {
            HouseholdMembershipEventsTable.insert {
                it[HouseholdMembershipEventsTable.householdId] = householdId()
                it[HouseholdMembershipEventsTable.residentId] = residentId()
                it[HouseholdMembershipEventsTable.role] = role
                it[status] = HouseholdMembershipEventsTable.STATUS_ACTIVE
            }
        }
    }

    override fun removeMember(
        householdId: HouseholdId,
        residentId: ResidentId,
    ) {
        transaction(database) {
            // role 列は NOT NULL。tombstone なので意味を持たないが NOT NULL を満たすため既定値を使う
            HouseholdMembershipEventsTable.insert {
                it[HouseholdMembershipEventsTable.householdId] = householdId()
                it[HouseholdMembershipEventsTable.residentId] = residentId()
                it[role] = HouseholdMemberRole.閲覧者
                it[status] = HouseholdMembershipEventsTable.STATUS_REMOVED
            }
        }
    }
}
