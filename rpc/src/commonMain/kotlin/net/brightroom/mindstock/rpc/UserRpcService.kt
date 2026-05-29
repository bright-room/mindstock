package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.user.profile.DisplayName

@Rpc
interface UserRpcService {
    suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError>
}
