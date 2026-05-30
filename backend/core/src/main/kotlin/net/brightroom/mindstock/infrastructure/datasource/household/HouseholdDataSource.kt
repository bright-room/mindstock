package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.infrastructure.datasource.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
import net.brightroom.mindstock.infrastructure.datasource.user.latestDisplayNames
import net.brightroom.mindstock.infrastructure.datasource.user.toProfile
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HouseholdDataSource : HouseholdRepository {
    override fun findOf(userId: UserId): Household {
        val latest = latestDisplayNames()

        // --- target household: most recent active membership's household for this user ---
        val maxMembershipIdAlias = HouseholdMembershipsTable.id.max().alias("max_membership_id")
        val targetHousehold =
            HouseholdMembershipsTable
                .join(
                    HouseholdMembershipRevocationsTable,
                    JoinType.LEFT,
                    additionalConstraint = {
                        HouseholdMembershipRevocationsTable.membership_id eq HouseholdMembershipsTable.id
                    },
                ).select(HouseholdMembershipsTable.household_id, maxMembershipIdAlias)
                .where {
                    (HouseholdMembershipsTable.user_id eq userId()) and
                        HouseholdMembershipRevocationsTable.id.isNull()
                }.groupBy(HouseholdMembershipsTable.household_id)
                .orderBy(maxMembershipIdAlias, SortOrder.DESC)
                .limit(1)
                .alias("target_household")

        val targetHouseholdId = targetHousehold[HouseholdMembershipsTable.household_id]

        // --- full member list of that household (active memberships only) ---
        val rows =
            HouseholdMembershipsTable
                .join(
                    HouseholdMembershipRevocationsTable,
                    JoinType.LEFT,
                    additionalConstraint = {
                        HouseholdMembershipRevocationsTable.membership_id eq HouseholdMembershipsTable.id
                    },
                ).join(targetHousehold, JoinType.INNER, onColumn = HouseholdMembershipsTable.household_id, otherColumn = targetHouseholdId)
                .join(UsersTable, JoinType.INNER, onColumn = HouseholdMembershipsTable.user_id, otherColumn = UsersTable.id)
                .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
                .join(UserDisplayNamesTable, JoinType.INNER) {
                    (UserDisplayNamesTable.user_id eq latest.userId) and
                        (UserDisplayNamesTable.id eq latest.maxId)
                }.selectAll()
                .where { HouseholdMembershipRevocationsTable.id.isNull() }
                .orderBy(HouseholdMembershipsTable.id, SortOrder.ASC)
                .toList()

        if (rows.isEmpty()) throw ResourceNotFoundException("household not found for user: $userId")

        val householdId = rows.first()[HouseholdMembershipsTable.household_id]
        val members =
            rows.map { row ->
                HouseholdMember(
                    profile = row.toProfile(),
                    role = row[HouseholdMembershipsTable.role],
                )
            }
        return hydrateHousehold(
            householdId = householdId,
            name = latestHouseholdNameOf(householdId),
            members = members,
        )
    }

    override fun findById(id: HouseholdId): Household {
        // --- household existence check (returns even if all memberships are revoked) ---
        val householdExists =
            HouseholdsTable
                .select(HouseholdsTable.id)
                .where { HouseholdsTable.id eq id() }
                .limit(1)
                .firstOrNull() != null
        if (!householdExists) throw ResourceNotFoundException("household not found: $id")

        val latest = latestDisplayNames()

        // --- full member list of that household (active memberships only) ---
        val rows =
            HouseholdMembershipsTable
                .join(
                    HouseholdMembershipRevocationsTable,
                    JoinType.LEFT,
                    additionalConstraint = {
                        HouseholdMembershipRevocationsTable.membership_id eq HouseholdMembershipsTable.id
                    },
                ).join(UsersTable, JoinType.INNER, onColumn = HouseholdMembershipsTable.user_id, otherColumn = UsersTable.id)
                .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
                .join(UserDisplayNamesTable, JoinType.INNER) {
                    (UserDisplayNamesTable.user_id eq latest.userId) and
                        (UserDisplayNamesTable.id eq latest.maxId)
                }.selectAll()
                .where {
                    (HouseholdMembershipsTable.household_id eq id()) and
                        HouseholdMembershipRevocationsTable.id.isNull()
                }.orderBy(HouseholdMembershipsTable.id, SortOrder.ASC)
                .toList()

        val members =
            rows.map { row ->
                HouseholdMember(
                    profile = row.toProfile(),
                    role = row[HouseholdMembershipsTable.role],
                )
            }
        return hydrateHousehold(
            householdId = id(),
            name = latestHouseholdNameOf(id()),
            members = members,
        )
    }

    private fun latestHouseholdNameOf(householdId: Uuid): HouseholdName =
        HouseholdNamesTable
            .selectAll()
            .where { HouseholdNamesTable.household_id eq householdId }
            .orderBy(HouseholdNamesTable.id, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let { HouseholdName(it[HouseholdNamesTable.name]) }
            ?: throw ResourceNotFoundException("household name not found: $householdId")
}
