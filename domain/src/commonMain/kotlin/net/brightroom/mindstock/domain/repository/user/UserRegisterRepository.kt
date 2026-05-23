package net.brightroom.mindstock.domain.repository.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity

interface UserRegisterRepository {
    /** users + 初回 user_display_names を 1 トランザクションで INSERT。 */
    fun register(identity: AuthIdentity, defaultDisplayName: DisplayName): User

    /** user_display_names に新規行を INSERT。 */
    fun rename(user: User, newName: DisplayName)
}
