package net.brightroom.mindstock.infrastructure.datasource.user

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.infrastructure.datasource.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class UserDataSource : UserRepository {
    override fun findByAuthIdentity(identity: AuthIdentity): User? = queryLatest { UsersTable.zitadel_sub eq identity.subject() }

    override fun findById(id: UserId): User? = queryLatest { UsersTable.id eq id() }

    private fun queryLatest(where: () -> Op<Boolean>): User? {
        // Alias the max() expression so QueryAlias.get() can resolve it correctly.
        val maxIdAlias = UserDisplayNamesTable.id.max().alias("max_name_id")
        val latestNames =
            UserDisplayNamesTable
                .select(UserDisplayNamesTable.user_id, maxIdAlias)
                .groupBy(UserDisplayNamesTable.user_id)
                .alias("latest_names")

        val latestUserId = latestNames[UserDisplayNamesTable.user_id]
        val latestMaxId = latestNames[maxIdAlias]

        return UsersTable
            .join(latestNames, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latestUserId)
            .join(UserDisplayNamesTable, JoinType.INNER) {
                (UserDisplayNamesTable.user_id eq latestUserId) and
                    (UserDisplayNamesTable.id eq latestMaxId)
            }.selectAll()
            .where { where() }
            .singleOrNull()
            ?.toUser()
    }
}
