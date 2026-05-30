package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

/**
 * 未登録(JWT 有効・User 未登録)でも通る初期化 service。
 * 認証は WS subprotocol 一本。AuthIdentity は session(Principal)から取得する(なりすまし防止)。
 */
@Rpc
interface OnboardingRpcService {
    suspend fun register(displayName: DisplayName): RpcResult<Profile, RpcError>
}
