package net.brightroom.mindstock.infrastructure.datasource.user

import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class UserRegisterDataSource : UserRegisterRepository {
    override fun register(
        identity: AuthIdentity,
        defaultDisplayName: DisplayName,
    ): Profile {
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
            .toProfile()
    }

    override fun rename(
        userId: UserId,
        newName: DisplayName,
    ) {
        UserDisplayNamesTable.insert {
            it[user_id] = userId()
            it[display_name] = newName()
        }
    }
}
