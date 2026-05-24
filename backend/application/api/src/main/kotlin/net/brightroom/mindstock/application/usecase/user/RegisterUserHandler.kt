package net.brightroom.mindstock.application.usecase.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.repository.user.UserRegisterRepository

class RegisterUserHandler(
    private val userRegisterRepository: UserRegisterRepository,
) {
    fun handle(
        identity: AuthIdentity,
        defaultName: DisplayName,
    ): User = userRegisterRepository.register(identity, defaultName)
}
