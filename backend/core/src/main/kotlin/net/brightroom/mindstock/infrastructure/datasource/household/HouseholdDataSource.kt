@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import net.brightroom.mindstock.infrastructure.datasource.resident.latestResidentDisplayNames
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdMembershipEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.MembershipStatus
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

class HouseholdDataSource(
    private val database: Database,
) : HouseholdRepository {
    override fun findById(id: HouseholdId): Household = transaction(database) { hydrate(id) }

    override fun listByResident(residentId: ResidentId): Households =
        transaction(database) {
            val ids = currentHouseholdIds(residentId)
            if (ids.isEmpty()) return@transaction Households(emptyList())
            val names = latestHouseholdNames(ids)
            val membersByHousehold = currentMembersByHouseholds(ids)
            Households(
                ids.map { id ->
                    val name = names[id] ?: throw ResourceNotFoundException("household not found: $id")
                    Household(id, HouseholdProfile(name), Members(membersByHousehold[id] ?: emptyList()))
                },
            )
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
                    (sub[HouseholdMembershipEventsTable.status] eq MembershipStatus.所属)
            }.map { HouseholdId(it[sub[HouseholdMembershipEventsTable.householdId]]) }
    }

    private fun hydrate(id: HouseholdId): Household {
        val name =
            latestHouseholdName(id) ?: throw ResourceNotFoundException("household not found: $id")
        val members = currentMembers(id)
        return Household(id, HouseholdProfile(name), Members(members))
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
                        (mSub[HouseholdMembershipEventsTable.status] eq MembershipStatus.所属)
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
            HouseholdMember(resident, role)
        }
    }

    /** 複数世帯の最新名を一括取得。HouseholdId → HouseholdName のマップを返す。 */
    private fun latestHouseholdNames(ids: List<HouseholdId>): Map<HouseholdId, HouseholdName> {
        if (ids.isEmpty()) return emptyMap() // inList(emptyList()) で不正 IN () を生成しない(loadMovementsByProducts と同じ慣行)
        val rn =
            rowNumber()
                .over()
                .partitionBy(HouseholdNamesTable.householdId)
                .orderBy(HouseholdNamesTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val sub =
            HouseholdNamesTable
                .select(HouseholdNamesTable.householdId, HouseholdNamesTable.name, rnAlias)
                .where { HouseholdNamesTable.householdId inList ids.map { it() } }
                .alias("latest_names_bulk")
        return sub
            .selectAll()
            .where { sub[rnAlias] eq 1L }
            .associate { HouseholdId(it[sub[HouseholdNamesTable.householdId]]) to HouseholdName(it[sub[HouseholdNamesTable.name]]) }
    }

    /** 複数世帯の現メンバーを一括取得。HouseholdId → List<HouseholdMember> のマップを返す。 */
    private fun currentMembersByHouseholds(ids: List<HouseholdId>): Map<HouseholdId, List<HouseholdMember>> {
        if (ids.isEmpty()) return emptyMap() // inList(emptyList()) で不正 IN () を生成しない(loadMovementsByProducts と同じ慣行)

        // Step 1: current membership rows(window rn=1 & status=所属) → (householdId, residentId, role)
        data class MemberRow(
            val householdId: HouseholdId,
            val residentId: ResidentId,
            val role: HouseholdMemberRole,
        )

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
                ).where { HouseholdMembershipEventsTable.householdId inList ids.map { it() } }
                .alias("latest_members_bulk")

        val memberRows =
            mSub
                .selectAll()
                .where {
                    (mSub[rnAlias] eq 1L) and
                        (mSub[HouseholdMembershipEventsTable.status] eq MembershipStatus.所属)
                }.orderBy(mSub[HouseholdMembershipEventsTable.residentId] to SortOrder.ASC)
                .map { row ->
                    MemberRow(
                        householdId = HouseholdId(row[mSub[HouseholdMembershipEventsTable.householdId]]),
                        residentId = ResidentId(row[mSub[HouseholdMembershipEventsTable.residentId]]),
                        role = row[mSub[HouseholdMembershipEventsTable.role]],
                    )
                }

        if (memberRows.isEmpty()) return ids.associateWith { emptyList() }

        // Step 2: 各メンバーの最新 display_name をバッチロード
        val memberResidentIds = memberRows.map { it.residentId }.distinct()
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

        // Step 3: 世帯 → HouseholdMember リストに変換。メンバー不在の世帯も空リストで必ずキーに含める
        // (返却 Map は常に全 ids をキーに持つ。呼び出し側で欠落を気にしなくてよい)
        val rowsByHousehold = memberRows.groupBy { it.householdId }
        return ids.associateWith { id ->
            (rowsByHousehold[id] ?: emptyList()).map { memberRow ->
                val displayName =
                    displayNames[memberRow.residentId]
                        ?: throw ResourceNotFoundException("resident display name not found: ${memberRow.residentId}")
                val resident = Resident(memberRow.residentId, ResidentProfile(DisplayName(displayName)))
                HouseholdMember(resident, memberRow.role)
            }
        }
    }
}
