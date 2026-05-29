package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserPublicRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserPublicController(
    private val userRegisterService: UserRegisterService,
    private val session: MindstockSession,
    private val database: Database,
) : UserPublicRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<User, RpcError> =
        tx(database, session) {
            RpcResult.Ok(userRegisterService.register(session.identity, displayName))
        }
}
