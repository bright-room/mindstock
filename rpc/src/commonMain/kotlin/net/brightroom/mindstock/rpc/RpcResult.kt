package net.brightroom.mindstock.rpc

import kotlinx.serialization.Serializable

/**
 * RPC メソッドの戻り値共通型。成功 [Ok] と失敗 [Err] の sealed 二択。
 *
 * クライアント側は `when (r) { is Ok -> ...; is Err -> ... }` で網羅性検証可能。
 * 例外を throw せず本型を返す前提のため、エラーフィールド (NotFound.id 等) は
 * `@Serializable` 経由で完全に保持される。
 */
@Serializable
sealed interface RpcResult<out T, out E> {
    @Serializable
    data class Ok<T>(
        val value: T,
    ) : RpcResult<T, Nothing>

    @Serializable
    data class Err<E>(
        val error: E,
    ) : RpcResult<Nothing, E>
}
