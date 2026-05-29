package net.brightroom.mindstock.application.service.user

import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName

class UserRegisterService(
    private val userRegisterRepository: UserRegisterRepository,
) {
    fun register(
        identity: AuthIdentity,
        defaultDisplayName: DisplayName,
    ): User = userRegisterRepository.register(identity, defaultDisplayName)

    fun rename(
        user: User,
        newName: DisplayName,
    ) {
        userRegisterRepository.rename(user, newName)
    }
}
