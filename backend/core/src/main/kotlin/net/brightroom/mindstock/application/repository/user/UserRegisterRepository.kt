package net.brightroom.mindstock.application.repository.user

import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

interface UserRegisterRepository {
    /** users + 初回 user_display_names を 1 トランザクションで INSERT。 */
    fun register(
        identity: AuthIdentity,
        defaultDisplayName: DisplayName,
    ): Profile

    /** user_display_names に新規行を INSERT。 */
    fun rename(
        userId: UserId,
        newName: DisplayName,
    )
}
