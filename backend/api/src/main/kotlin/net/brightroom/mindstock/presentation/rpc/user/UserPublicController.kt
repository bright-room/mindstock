package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.rpc.rpcBoundary
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserPublicRpcService

class UserPublicController(
    private val userRegisterService: UserRegisterService,
    private val session: MindstockSession,
) : UserPublicRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<Profile, RpcError> =
        rpcBoundary(session) {
            userRegisterService.register(session.identity, displayName)
        }
}
