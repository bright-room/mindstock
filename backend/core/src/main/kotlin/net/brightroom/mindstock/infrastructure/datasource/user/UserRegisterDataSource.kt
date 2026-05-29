package net.brightroom.mindstock.infrastructure.datasource.user

import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class UserRegisterDataSource : UserRegisterRepository {
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
            it[user_id] = user.id()
            it[display_name] = newName()
        }
    }
}
