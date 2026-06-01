package net.brightroom.mindstock.rpc.result

import kotlinx.serialization.Serializable

/**
 * RPC メソッドの戻り値共通型。成功 [Ok] と失敗 [Err] の sealed 二択。
 *
 * クライアント側は `when (r) { is Ok -> ...; is Err -> ... }` で網羅性検証可能。
 * `T` は non-null(`T?` 禁止)。「不在」は [Err] の [RpcError.NotFound] で表す。
 */
@Serializable
sealed interface RpcResult<out T : Any, out E : Any> {
    @Serializable
    data class Ok<T : Any>(
        val value: T,
    ) : RpcResult<T, Nothing>

    @Serializable
    data class Err<E : Any>(
        val error: E,
    ) : RpcResult<Nothing, E>
}
