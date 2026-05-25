package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.usecase.user.RegisterUserHandler
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService

class UserPublicRpcServiceImpl(
    private val registerUser: RegisterUserHandler,
) : UserPublicRpcService {
    override suspend fun register(
        displayName: DisplayName,
        authIdentity: AuthIdentity,
    ): User = registerUser.handle(authIdentity, displayName)
}
