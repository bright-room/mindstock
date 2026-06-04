package net.brightroom.mindstock.frontend.core.rpc

import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

/** Repository が ViewModel に返す結果型。RPC 契約 RpcResult を frontend 都合に変換。 */
sealed interface RpcOutcome<out T> {
    data class Success<T>(
        val value: T,
    ) : RpcOutcome<T>

    data class Failure(
        val error: RpcError,
    ) : RpcOutcome<Nothing>
}

fun <T : Any> RpcResult<T, RpcError>.toOutcome(): RpcOutcome<T> =
    when (this) {
        is RpcResult.Ok -> RpcOutcome.Success(value)
        is RpcResult.Err -> RpcOutcome.Failure(error)
    }
