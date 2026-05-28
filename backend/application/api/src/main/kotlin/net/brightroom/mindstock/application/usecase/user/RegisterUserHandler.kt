package net.brightroom.mindstock.application.usecase.user

import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity

class RegisterUserHandler(
    private val userRegisterRepository: UserRegisterRepository,
) {
    fun handle(
        identity: AuthIdentity,
        defaultDisplayName: DisplayName,
    ): User = userRegisterRepository.register(identity, defaultDisplayName)
}
