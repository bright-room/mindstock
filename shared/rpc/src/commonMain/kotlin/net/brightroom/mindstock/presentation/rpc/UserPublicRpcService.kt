package net.brightroom.mindstock.presentation.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User

/**
 * JWT 検証は通すが User 未登録でも通る RPC。新規ユーザー登録のみ。
 * AuthIdentity は Principal から取得するため引数では受け取らない(なりすまし防止)。
 */
@Rpc
interface UserPublicRpcService {
    suspend fun register(displayName: DisplayName): User
}
