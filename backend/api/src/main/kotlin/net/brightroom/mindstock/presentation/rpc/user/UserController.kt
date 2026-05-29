package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserController(
    private val userRegisterService: UserRegisterService,
    private val session: MindstockSession,
    private val database: Database,
) : UserRpcService {
    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> =
        tx(database, session) {
            userRegisterService.rename(requireNotNull(session.userId), displayName)
            RpcResult.Ok(Unit)
        }
}
