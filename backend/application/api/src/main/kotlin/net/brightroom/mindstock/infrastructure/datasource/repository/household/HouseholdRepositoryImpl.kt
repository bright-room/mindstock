package net.brightroom.mindstock.infrastructure.datasource.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository
import net.brightroom.mindstock.infrastructure.datasource.repository.user.toUser
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdMembershipRevocationsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdMembershipsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
internal class HouseholdRepositoryImpl : HouseholdRepository {
    override fun findOf(user: User): Household? {
        // --- latest display name per user ---
        val maxNameIdAlias = UserDisplayNamesTable.id.max().alias("max_name_id")
        val latestNames =
            UserDisplayNamesTable
                .select(UserDisplayNamesTable.user_id, maxNameIdAlias)
                .groupBy(UserDisplayNamesTable.user_id)
                .alias("latest_names")
        val latestNameUserId = latestNames[UserDisplayNamesTable.user_id]
        val latestNameMaxId = latestNames[maxNameIdAlias]

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
                    (HouseholdMembershipsTable.user_id eq user.id().toJavaUuid()) and
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
                .join(latestNames, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latestNameUserId)
                .join(UserDisplayNamesTable, JoinType.INNER) {
                    (UserDisplayNamesTable.user_id eq latestNameUserId) and
                        (UserDisplayNamesTable.id eq latestNameMaxId)
                }.selectAll()
                .where { HouseholdMembershipRevocationsTable.id.isNull() }
                .orderBy(HouseholdMembershipsTable.id, SortOrder.ASC)
                .toList()

        if (rows.isEmpty()) return null

        val householdId = rows.first()[HouseholdMembershipsTable.household_id].toKotlinUuid()
        val members =
            rows.map { row ->
                HouseholdMember(
                    user = row.toUser(),
                    role = row[HouseholdMembershipsTable.role],
                )
            }
        return hydrateHousehold(householdId, members)
    }

    override fun findById(id: HouseholdId): Household? {
        // --- latest display name per user ---
        val maxNameIdAlias = UserDisplayNamesTable.id.max().alias("max_name_id")
        val latestNames =
            UserDisplayNamesTable
                .select(UserDisplayNamesTable.user_id, maxNameIdAlias)
                .groupBy(UserDisplayNamesTable.user_id)
                .alias("latest_names")
        val latestNameUserId = latestNames[UserDisplayNamesTable.user_id]
        val latestNameMaxId = latestNames[maxNameIdAlias]

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
                .join(latestNames, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latestNameUserId)
                .join(UserDisplayNamesTable, JoinType.INNER) {
                    (UserDisplayNamesTable.user_id eq latestNameUserId) and
                        (UserDisplayNamesTable.id eq latestNameMaxId)
                }.selectAll()
                .where {
                    (HouseholdMembershipsTable.household_id eq id().toJavaUuid()) and
                        HouseholdMembershipRevocationsTable.id.isNull()
                }.orderBy(HouseholdMembershipsTable.id, SortOrder.ASC)
                .toList()

        if (rows.isEmpty()) return null

        val householdId = rows.first()[HouseholdMembershipsTable.household_id].toKotlinUuid()
        val members =
            rows.map { row ->
                HouseholdMember(
                    user = row.toUser(),
                    role = row[HouseholdMembershipsTable.role],
                )
            }
        return hydrateHousehold(householdId, members)
    }
}
