package net.brightroom.mindstock.infrastructure.datasource.user

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.infrastructure.datasource.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class UserDataSource : UserRepository {
    override fun findProfileByAuthIdentity(identity: AuthIdentity): Profile =
        queryLatest { UsersTable.zitadel_sub eq identity.subject() }
            ?: throw ResourceNotFoundException("user not found")

    override fun findProfileById(id: UserId): Profile =
        queryLatest { UsersTable.id eq id() }
            ?: throw ResourceNotFoundException("user not found")

    private fun queryLatest(where: () -> Op<Boolean>): Profile? {
        val latest = latestDisplayNames()

        return UsersTable
            .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
            .join(UserDisplayNamesTable, JoinType.INNER) {
                (UserDisplayNamesTable.user_id eq latest.userId) and
                    (UserDisplayNamesTable.id eq latest.maxId)
            }.selectAll()
            .where { where() }
            .singleOrNull()
            ?.toProfile()
    }
}
