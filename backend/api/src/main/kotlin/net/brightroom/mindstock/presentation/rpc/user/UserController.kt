package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserController(
    private val userRegisterService: UserRegisterService,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
    private val database: Database,
) : UserRpcService {
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> =
        tx(database) {
            userRegisterService.rename(actor, displayName)
            RpcResult.Ok(Unit)
        }
}
