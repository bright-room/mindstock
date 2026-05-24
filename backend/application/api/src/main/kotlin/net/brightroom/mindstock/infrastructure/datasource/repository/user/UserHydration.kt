package net.brightroom.mindstock.infrastructure.datasource.repository.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
internal fun ResultRow.toUser(): User =
    User(
        id = UserId(this[UsersTable.id].toKotlinUuid()),
        authIdentity =
            AuthIdentity(
                provider = AuthProvider.ZITADEL,
                subject = AuthSubject(this[UsersTable.zitadel_sub]),
            ),
        displayName = DisplayName(this[UserDisplayNamesTable.display_name]),
    )
