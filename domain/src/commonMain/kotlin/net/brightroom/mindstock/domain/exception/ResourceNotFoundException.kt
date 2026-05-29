package net.brightroom.mindstock.domain.exception

/**
 * 単一値の resource(集約 / Profile 等)が見つからなかったことを表す。
 *
 * Repository 実装(infrastructure)が起点として throw し、Service・Controller を素通りして
 * 最終的に `:backend:api` の tx() ヘルパーが捕捉して `RpcError.NotFound(message)` に変換する。
 *
 * メッセージは「リソース種別 + 識別子」の形式を推奨: e.g. "household not found: $id"
 */
class ResourceNotFoundException(
    reason: String,
) : RuntimeException(reason)
