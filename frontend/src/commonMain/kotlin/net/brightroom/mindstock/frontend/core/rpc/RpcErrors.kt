package net.brightroom.mindstock.frontend.core.rpc

import net.brightroom.mindstock.rpc.result.RpcError

/** RpcError variant を網羅し、ユーザ向け文言(暫定)を返す。新 variant 追加でコンパイルエラー。 */
fun userMessageOf(error: RpcError): String =
    when (error) {
        is RpcError.Unauthorized -> "セッションが切れました。再ログインしてください。"
        is RpcError.NotFound -> "対象が見つかりませんでした。"
        is RpcError.BadRequest -> "入力に誤りがあります: ${error.reason}"
        is RpcError.Conflict -> "操作が競合しました: ${error.reason}"
        is RpcError.Internal -> "サーバでエラーが発生しました。"
    }

/** 再認証が必要なエラーか(boot/呼び出し側が token 破棄 → authorize へ倒す判定)。 */
fun RpcError.requiresReauth(): Boolean = this is RpcError.Unauthorized
