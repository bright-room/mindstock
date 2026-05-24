package net.brightroom.mindstock.infrastructure.datasource.repository.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.repository.user.UserRepository
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class UserRepositoryImpl(
    private val database: Database,
) : UserRepository {
    override fun findByAuthIdentity(identity: AuthIdentity): User? {
        val sql =
            """
            SELECT u.id AS user_id,
                   u.zitadel_sub,
                   d.display_name
            FROM users u
            INNER JOIN (
                SELECT DISTINCT ON (user_id) user_id, display_name, id
                FROM user_display_names
                ORDER BY user_id, id DESC
            ) d ON d.user_id = u.id
            WHERE u.zitadel_sub = ?
            """.trimIndent()

        return TransactionManager.current().exec(
            sql,
            args = listOf(TextColumnType() to identity.subject()),
        ) { rs ->
            if (rs.next()) {
                User(
                    id = UserId(rs.getObject("user_id", UUID::class.java).toKotlinUuid()),
                    authIdentity =
                        AuthIdentity(
                            provider = AuthProvider.ZITADEL,
                            subject = AuthSubject(rs.getString("zitadel_sub")),
                        ),
                    displayName = DisplayName(rs.getString("display_name")),
                )
            } else {
                null
            }
        }
    }
}
