package net.brightroom.mindstock.infrastructure.datasource.repository.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.repository.user.UserRegisterRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class UserRegisterRepositoryImpl(
    private val database: Database,
) : UserRegisterRepository {
    override fun register(
        identity: AuthIdentity,
        defaultDisplayName: DisplayName,
    ): User {
        val insertedUserId =
            UsersTable.insert {
                it[zitadel_sub] = identity.subject()
            } get UsersTable.id

        UserDisplayNamesTable.insert {
            it[user_id] = insertedUserId
            it[display_name] = defaultDisplayName()
        }

        return (UsersTable innerJoin UserDisplayNamesTable)
            .selectAll()
            .where { UsersTable.id eq insertedUserId }
            .single()
            .toUser()
    }

    override fun rename(
        user: User,
        newName: DisplayName,
    ) {
        UserDisplayNamesTable.insert {
            it[user_id] = user.id().toJavaUuid()
            it[display_name] = newName()
        }
    }
}
