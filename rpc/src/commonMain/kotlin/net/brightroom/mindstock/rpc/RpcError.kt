package net.brightroom.mindstock.rpc

import kotlinx.serialization.Serializable

/**
 * API 全体で共有する RPC エラー語彙。
 *
 * read/write を分けず、ありうるエラー集合の和集合を持つ。「この read メソッドは
 * BadRequest を返さない」という型レベル保証は失うが、クライアントは when の
 * 網羅性検証で新しい variant の追加に必ず気付ける。
 */
@Serializable
sealed interface RpcError {
    /** 認証失敗 / トークン期限切れ / Principal 未解決 等。 */
    @Serializable
    data class Unauthorized(
        val reason: String,
    ) : RpcError

    /** 集約 resolve 失敗(例: Repository.findById() が null)。 */
    @Serializable
    data class NotFound(
        val resource: String,
        val id: String,
    ) : RpcError

    /** 入力検証エラー。 */
    @Serializable
    data class BadRequest(
        val field: String,
        val reason: String,
    ) : RpcError

    /** 競合(重複登録 等)。 */
    @Serializable
    data class Conflict(
        val reason: String,
    ) : RpcError

    /** 想定外のサーバエラー。クライアントにスタックトレースは漏らさない。 */
    @Serializable
    data class Internal(
        val reason: String,
    ) : RpcError
}
