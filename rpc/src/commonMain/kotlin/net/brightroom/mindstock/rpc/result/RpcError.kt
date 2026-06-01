package net.brightroom.mindstock.rpc.result

import kotlinx.serialization.Serializable

/**
 * API 全体で共有する RPC エラー語彙。
 *
 * read/write を分けず、ありうるエラー集合の和集合を持つ。クライアントは when の
 * 網羅性検証で新しい variant の追加に必ず気付ける。例外 → variant の翻訳は
 * P5 の Controller が担う(domain は RpcError を import しない)。
 */
@Serializable
sealed interface RpcError {
    /** 認証失敗 / トークン期限切れ / Principal 未解決 等。 */
    @Serializable
    data class Unauthorized(
        val reason: String,
    ) : RpcError

    /**
     * 単一値の resource が見つからなかった。message は server 側の
     * `ResourceNotFoundException` の reason がそのまま乗る(例: "household not found: $id")。
     */
    @Serializable
    data class NotFound(
        val message: String,
    ) : RpcError

    /** 入力検証エラー(IAE の翻訳先)。 */
    @Serializable
    data class BadRequest(
        val field: String,
        val reason: String,
    ) : RpcError

    /** 競合(重複登録・前提崩れ 等)。 */
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
