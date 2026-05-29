package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.infrastructure.datasource.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
import net.brightroom.mindstock.infrastructure.datasource.user.toProfile
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class HouseholdRegisterDataSource : HouseholdRegisterRepository {
    override fun create(ownerId: UserId): Household {
        val newHouseholdId =
            HouseholdsTable.insert {
                // id は default uuidv7()
            } get HouseholdsTable.id

        HouseholdMembershipsTable.insert {
            it[household_id] = newHouseholdId
            it[user_id] = ownerId()
            it[role] = HouseholdMemberRole.OWNER
        }

        val ownerProfile =
            run {
                val maxNameIdAlias = UserDisplayNamesTable.id.max().alias("max_name_id")
                val latestNames =
                    UserDisplayNamesTable
                        .select(UserDisplayNamesTable.user_id, maxNameIdAlias)
                        .groupBy(UserDisplayNamesTable.user_id)
                        .alias("latest_names")
                val latestNameUserId = latestNames[UserDisplayNamesTable.user_id]
                val latestNameMaxId = latestNames[maxNameIdAlias]

                UsersTable
                    .join(latestNames, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latestNameUserId)
                    .join(UserDisplayNamesTable, JoinType.INNER) {
                        (UserDisplayNamesTable.user_id eq latestNameUserId) and
                            (UserDisplayNamesTable.id eq latestNameMaxId)
                    }.selectAll()
                    .where { UsersTable.id eq ownerId() }
                    .single()
                    .toProfile()
            }

        return hydrateHousehold(
            householdId = newHouseholdId,
            members = listOf(HouseholdMember(ownerProfile, HouseholdMemberRole.OWNER)),
        )
    }

    override fun invite(
        household: Household,
        userId: UserId,
        role: HouseholdMemberRole,
    ) {
        HouseholdMembershipsTable.insert {
            it[household_id] = household.id()
            it[user_id] = userId()
            it[this.role] = role
        }
    }

    override fun revoke(
        household: Household,
        userId: UserId,
    ) {
        val activeMembershipId =
            HouseholdMembershipsTable
                .join(
                    HouseholdMembershipRevocationsTable,
                    JoinType.LEFT,
                    additionalConstraint = {
                        HouseholdMembershipRevocationsTable.membership_id eq HouseholdMembershipsTable.id
                    },
                ).select(HouseholdMembershipsTable.id)
                .where {
                    (HouseholdMembershipsTable.household_id eq household.id()) and
                        (HouseholdMembershipsTable.user_id eq userId()) and
                        HouseholdMembershipRevocationsTable.id.isNull()
                }.orderBy(HouseholdMembershipsTable.id, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.get(HouseholdMembershipsTable.id)
                ?: error("no active membership for user $userId in household ${household.id}")

        HouseholdMembershipRevocationsTable.insert {
            it[membership_id] = activeMembershipId
        }
    }
}
