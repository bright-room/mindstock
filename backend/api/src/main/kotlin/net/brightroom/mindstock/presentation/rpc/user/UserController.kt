package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.rpc.rpcBoundary
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserRpcService

class UserController(
    private val userRegisterService: UserRegisterService,
    private val session: MindstockSession,
) : UserRpcService {
    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> =
        rpcBoundary(session) {
            userRegisterService.rename(requireNotNull(session.userId), displayName)
        }
}
