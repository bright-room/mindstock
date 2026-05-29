package net.brightroom.mindstock.infrastructure.datasource.user

import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
internal fun ResultRow.toProfile(): Profile =
    Profile(
        userId = UserId(this[UsersTable.id]),
        displayName = DisplayName(this[UserDisplayNamesTable.display_name]),
    )
