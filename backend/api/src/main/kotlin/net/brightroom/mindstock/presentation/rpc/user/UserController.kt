package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
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
    private val session: MindstockSession,
    private val database: Database,
) : UserRpcService {
    private suspend fun resolveActor(): User =
        userRepository.findById(requireNotNull(session.userId))
            ?: error("session.userId points to non-existent User — likely deleted between upgrade and call")

    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val actor = resolveActor()
            userRegisterService.rename(actor, displayName)
            RpcResult.Ok(Unit)
        }
}
