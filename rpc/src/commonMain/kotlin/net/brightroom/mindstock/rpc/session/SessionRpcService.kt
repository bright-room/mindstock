package net.brightroom.mindstock.rpc.session

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface SessionRpcService {
    /** 現在の接続の登録状態を返す。認証済みなら未登録でも呼べる。 */
    suspend fun whoami(): RpcResult<SessionStatus, RpcError>
}
