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

    /**
     * 単一値の resource が見つからなかった。
     *
     * message は server 側で発生した `ResourceNotFoundException` の reason がそのまま
     * 乗る。例: "household not found: $id" / "product not found: $id"。
     * クライアントは呼び出しコンテキスト(どの RPC method を呼んだか)から意味を組み立てる。
     */
    @Serializable
    data class NotFound(
        val message: String,
    ) : RpcError

    /** 入力検証エラー。VO の値域違反(IllegalArgumentException)等。 */
    @Serializable
    data class BadRequest(
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
