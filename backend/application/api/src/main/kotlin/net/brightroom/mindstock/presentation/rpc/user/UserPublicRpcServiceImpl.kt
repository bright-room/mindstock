package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.usecase.user.RegisterUserHandler
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserPublicRpcServiceImpl(
    private val registerUser: RegisterUserHandler,
    private val database: Database,
) : UserPublicRpcService {
    override suspend fun register(
        displayName: DisplayName,
        authIdentity: AuthIdentity,
    ): User = tx(database) { registerUser.handle(authIdentity, displayName) }
}
