@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.resident.latestResidentDisplayNames
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdMembershipEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile

class HouseholdDataSource(
    private val database: Database,
) : HouseholdRepository {
    override fun findById(id: HouseholdId): Household = transaction(database) { hydrate(id) }

    override fun listByResident(residentId: ResidentId): Households =
        transaction(database) {
            // current メンバーである household_id を集める(membership window rn=1 & status=所属)
            val ids = currentHouseholdIds(residentId)
            // 1 resident は通常 1〜数世帯のみ所属するため per-household hydrate(1+3N)で許容。
            // 世帯数が増えるなら householdId IN (...) の一括ロードに切り替える(P5 でプロファイル後判断)。
            Households(ids.map { hydrate(it) })
        }

    private fun currentHouseholdIds(residentId: ResidentId): List<HouseholdId> {
        val rn =
            rowNumber()
                .over()
                .partitionBy(HouseholdMembershipEventsTable.householdId, HouseholdMembershipEventsTable.residentId)
                .orderBy(HouseholdMembershipEventsTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val sub =
            HouseholdMembershipEventsTable
                .select(
                    HouseholdMembershipEventsTable.householdId,
                    HouseholdMembershipEventsTable.residentId,
                    HouseholdMembershipEventsTable.status,
                    rnAlias,
                ).where { HouseholdMembershipEventsTable.residentId eq residentId() }
                .alias("latest_membership")
        return sub
            .selectAll()
            .where {
                (sub[rnAlias] eq 1L) and
                    (sub[HouseholdMembershipEventsTable.status] eq HouseholdMembershipEventsTable.STATUS_ACTIVE)
            }.map { HouseholdId(it[sub[HouseholdMembershipEventsTable.householdId]]) }
    }

    private fun hydrate(id: HouseholdId): Household {
        val name =
            latestHouseholdName(id) ?: throw ResourceNotFoundException("household not found: $id")
        val members = currentMembers(id)
        return assembleHousehold(id, name, members)
    }

    private fun latestHouseholdName(id: HouseholdId): HouseholdName? {
        val rn =
            rowNumber()
                .over()
                .partitionBy(HouseholdNamesTable.householdId)
                .orderBy(HouseholdNamesTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val sub =
            HouseholdNamesTable
                .select(HouseholdNamesTable.householdId, HouseholdNamesTable.name, rnAlias)
                .where { HouseholdNamesTable.householdId eq id() }
                .alias("latest_name")
        return sub
            .selectAll()
            .where { sub[rnAlias] eq 1L }
            .limit(1)
            .firstOrNull()
            ?.let { HouseholdName(it[sub[HouseholdNamesTable.name]]) }
    }

    private fun currentMembers(id: HouseholdId): List<HouseholdMember> {
        // Step 1: current membership rows(window rn=1 & status=所属) → (residentId, role)
        val rn =
            rowNumber()
                .over()
                .partitionBy(HouseholdMembershipEventsTable.householdId, HouseholdMembershipEventsTable.residentId)
                .orderBy(HouseholdMembershipEventsTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val mSub =
            HouseholdMembershipEventsTable
                .select(
                    HouseholdMembershipEventsTable.householdId,
                    HouseholdMembershipEventsTable.residentId,
                    HouseholdMembershipEventsTable.role,
                    HouseholdMembershipEventsTable.status,
                    rnAlias,
                ).where { HouseholdMembershipEventsTable.householdId eq id() }
                .alias("latest_member")

        val memberRows =
            mSub
                .selectAll()
                .where {
                    (mSub[rnAlias] eq 1L) and
                        (mSub[HouseholdMembershipEventsTable.status] eq HouseholdMembershipEventsTable.STATUS_ACTIVE)
                }.orderBy(mSub[HouseholdMembershipEventsTable.residentId] to SortOrder.ASC)
                .map { row ->
                    Pair(
                        ResidentId(row[mSub[HouseholdMembershipEventsTable.residentId]]),
                        row[mSub[HouseholdMembershipEventsTable.role]],
                    )
                }

        if (memberRows.isEmpty()) return emptyList()

        // Step 2: 各メンバーの最新 display_name をバッチロード
        val memberResidentIds = memberRows.map { it.first }
        val (dnSub, dnRefs) = latestResidentDisplayNames()
        val displayNames: Map<ResidentId, String> =
            ResidentsTable
                .join(dnSub, JoinType.INNER, onColumn = ResidentsTable.id, otherColumn = dnSub[ResidentDisplayNamesTable.residentId])
                .select(ResidentsTable.id, dnSub[ResidentDisplayNamesTable.displayName])
                .where {
                    (ResidentsTable.id inList memberResidentIds.map { it() }) and
                        (dnSub[dnRefs.rn] eq 1L)
                }.associate { row ->
                    ResidentId(row[ResidentsTable.id]) to row[dnSub[ResidentDisplayNamesTable.displayName]]
                }

        // Step 3: zip in memory
        return memberRows.map { (residentId, role) ->
            val displayName =
                displayNames[residentId]
                    ?: throw ResourceNotFoundException("resident display name not found: $residentId")
            val resident = Resident(residentId, ResidentProfile(DisplayName(displayName)))
            member(resident, role)
        }
    }
}
