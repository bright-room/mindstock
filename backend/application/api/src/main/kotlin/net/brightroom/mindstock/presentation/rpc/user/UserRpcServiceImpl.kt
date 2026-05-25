package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.usecase.user.RenameUserHandler
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.presentation.rpc.UserRpcService

class UserRpcServiceImpl(
    private val rename: RenameUserHandler,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
) : UserRpcService {
    override suspend fun rename(displayName: DisplayName) {
        val user = call.actor(userRepository)
        rename.handle(user, displayName)
    }
}
