package net.brightroom.mindstock.presentation.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity

/** 認証不要 RPC。新規ユーザー登録のみ。 */
@Rpc
interface UserPublicRpcService {
    suspend fun register(
        displayName: DisplayName,
        authIdentity: AuthIdentity,
    ): User
}
