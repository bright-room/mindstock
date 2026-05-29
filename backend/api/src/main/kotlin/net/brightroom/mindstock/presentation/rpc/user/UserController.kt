package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.UserRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserController(
    private val userRegisterService: UserRegisterService,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
    private val database: Database,
) : UserRpcService {
    // Memoized for the lifetime of this per-connection Service Impl.
    // NOTE: a rename within the same connection won't refresh this cache until reconnect.
    // The lazy initializer runs the first time `actor` is referenced; ensure that
    // first reference happens INSIDE a `tx(database) { ... }` block so the
    // userRepository.findProfile* call has a transactional context.
    private val actor: Profile by lazy { call.actor(userRepository) }

    override suspend fun rename(displayName: DisplayName) = tx(database) { userRegisterService.rename(actor.userId, displayName) }
}
