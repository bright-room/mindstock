package net.brightroom.mindstock.infrastructure.datasource.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdMembershipRevocationsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdMembershipsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.household.HouseholdsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class HouseholdRegisterRepositoryImpl(
    private val database: Database,
) : HouseholdRegisterRepository {
    override fun create(owner: User): Household {
        val newHouseholdId =
            HouseholdsTable.insert {
                // id は default uuidv7()
            } get HouseholdsTable.id

        HouseholdMembershipsTable.insert {
            it[household_id] = newHouseholdId
            it[user_id] = owner.id().toJavaUuid()
            it[role] = HouseholdMemberRole.OWNER
        }

        return hydrateHousehold(
            householdId = newHouseholdId.toKotlinUuid(),
            members = listOf(HouseholdMember(owner, HouseholdMemberRole.OWNER)),
        )
    }

    override fun invite(
        household: Household,
        user: User,
        role: HouseholdMemberRole,
    ) {
        HouseholdMembershipsTable.insert {
            it[household_id] = household.id().toJavaUuid()
            it[user_id] = user.id().toJavaUuid()
            it[this.role] = role
        }
    }

    override fun revoke(
        household: Household,
        user: User,
    ) {
        val householdUuid = household.id().toJavaUuid()
        val userUuid = user.id().toJavaUuid()
        val membershipId =
            HouseholdMembershipsTable
                .selectAll()
                .where {
                    (HouseholdMembershipsTable.household_id eq householdUuid) and
                        (HouseholdMembershipsTable.user_id eq userUuid)
                }.orderBy(HouseholdMembershipsTable.id, SortOrder.DESC)
                .limit(1)
                .single()[HouseholdMembershipsTable.id]

        HouseholdMembershipRevocationsTable.insert {
            it[membership_id] = membershipId
        }
    }
}
