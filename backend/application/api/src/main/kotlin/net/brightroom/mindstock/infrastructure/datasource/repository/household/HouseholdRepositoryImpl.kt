package net.brightroom.mindstock.infrastructure.datasource.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository
import org.jetbrains.exposed.v1.core.UUIDColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class HouseholdRepositoryImpl(
    private val database: Database,
) : HouseholdRepository {
    override fun findOf(user: User): Household? {
        val sql =
            """
            WITH active AS (
                SELECT m.id, m.household_id, m.user_id, m.role
                FROM household_memberships m
                LEFT JOIN household_membership_revocations r ON r.membership_id = m.id
                WHERE r.id IS NULL
            ),
            target_household AS (
                SELECT household_id
                FROM active
                WHERE user_id = ?
                LIMIT 1
            )
            SELECT a.household_id,
                   a.role,
                   u.id AS user_uuid,
                   u.zitadel_sub,
                   d.display_name
            FROM active a
            INNER JOIN target_household t ON t.household_id = a.household_id
            INNER JOIN users u ON u.id = a.user_id
            INNER JOIN (
                SELECT DISTINCT ON (user_id) user_id, display_name, id
                FROM user_display_names
                ORDER BY user_id, id DESC
            ) d ON d.user_id = u.id
            ORDER BY a.id
            """.trimIndent()

        data class Row(
            val householdId: UUID,
            val role: String,
            val userUuid: UUID,
            val sub: String,
            val name: String,
        )
        val rows = mutableListOf<Row>()

        TransactionManager.current().exec(
            sql,
            args = listOf(UUIDColumnType() to user.id().toJavaUuid()),
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            while (rs.next()) {
                rows.add(
                    Row(
                        householdId = rs.getObject("household_id", UUID::class.java),
                        role = rs.getString("role"),
                        userUuid = rs.getObject("user_uuid", UUID::class.java),
                        sub = rs.getString("zitadel_sub"),
                        name = rs.getString("display_name"),
                    ),
                )
            }
        }

        if (rows.isEmpty()) return null

        val members =
            rows.map { r ->
                HouseholdMember(
                    user =
                        User(
                            id = UserId(r.userUuid.toKotlinUuid()),
                            authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject(r.sub)),
                            displayName = DisplayName(r.name),
                        ),
                    role = HouseholdMemberRole.valueOf(r.role),
                )
            }
        return hydrateHousehold(rows.first().householdId.toKotlinUuid(), members)
    }
}
