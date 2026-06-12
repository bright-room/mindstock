@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import kotlinx.datetime.LocalDateTime
import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.infrastructure.datasource.Created
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdMembershipEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.MembershipStatus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class HouseholdRegisterDataSource(
    private val database: Database,
) : HouseholdRegisterRepository {
    override fun registerHousehold(household: Household) {
        transaction(database) {
            val createdTime = Created.now()
            HouseholdsTable.insert {
                it[id] = household.id()
                it[createdAt] = createdTime()
            }
            HouseholdNamesTable.insert {
                it[householdId] = household.id()
                it[name] = household.profile.name()
                it[recordedAt] = createdTime()
            }
            household.members.list.forEach { m ->
                insertMembershipEvent(household.id, m.resident.id, m.role, MembershipStatus.所属, createdTime())
            }
        }
    }

    override fun appendHouseholdName(
        householdId: HouseholdId,
        name: HouseholdName,
    ) {
        transaction(database) {
            val createdTime = Created.now()
            HouseholdNamesTable.insert {
                it[HouseholdNamesTable.householdId] = householdId()
                it[HouseholdNamesTable.name] = name()
                it[recordedAt] = createdTime()
            }
        }
    }

    override fun joinMember(
        householdId: HouseholdId,
        resident: Resident,
        role: HouseholdMemberRole,
    ) {
        transaction(database) {
            val createdTime = Created.now()
            insertMembershipEvent(householdId, resident.id, role, MembershipStatus.所属, createdTime())
        }
    }

    override fun changeMemberRole(
        householdId: HouseholdId,
        residentId: ResidentId,
        role: HouseholdMemberRole,
    ) {
        transaction(database) {
            val createdTime = Created.now()
            insertMembershipEvent(householdId, residentId, role, MembershipStatus.所属, createdTime())
        }
    }

    override fun removeMember(
        householdId: HouseholdId,
        residentId: ResidentId,
    ) {
        transaction(database) {
            val createdTime = Created.now()
            // role 列は NOT NULL。tombstone なので意味を持たないが NOT NULL を満たすため既定値を使う
            insertMembershipEvent(householdId, residentId, HouseholdMemberRole.閲覧者, MembershipStatus.除外, createdTime())
        }
    }

    /** household_membership_events への 1 行 insert(所属/除外イベントの共通形)。tx 内で呼ぶ前提。 */
    private fun insertMembershipEvent(
        householdId: HouseholdId,
        residentId: ResidentId,
        role: HouseholdMemberRole,
        status: MembershipStatus,
        recordedAt: LocalDateTime,
    ) {
        HouseholdMembershipEventsTable.insert {
            it[HouseholdMembershipEventsTable.householdId] = householdId()
            it[HouseholdMembershipEventsTable.residentId] = residentId()
            it[HouseholdMembershipEventsTable.role] = role
            it[HouseholdMembershipEventsTable.status] = status
            it[HouseholdMembershipEventsTable.recordedAt] = recordedAt
        }
    }
}
