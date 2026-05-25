package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.usecase.user.RenameUserHandler
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.presentation.rpc.UserRpcService

class UserRpcServiceImpl(
    private val rename: RenameUserHandler,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
) : UserRpcService {
    // Memoized for the lifetime of this per-connection Service Impl.
    // NOTE: a rename within the same connection won't refresh this cache until reconnect.
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun rename(displayName: DisplayName) {
        rename.handle(actor, displayName)
    }
}
