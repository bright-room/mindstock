package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.user.DisplayName

@Rpc
interface UserRpcService {
    suspend fun rename(displayName: DisplayName)
}
