package net.brightroom.mindstock.frontend.core.rpc

import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.error_bad_request
import mindstock.frontend.generated.resources.error_conflict
import mindstock.frontend.generated.resources.error_internal
import mindstock.frontend.generated.resources.error_not_found
import mindstock.frontend.generated.resources.error_unauthorized
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

/** RpcError variant を網羅し UiText（リソース）に変換。新 variant 追加でコンパイルエラー。 */
fun errorText(error: RpcError): UiText =
    when (error) {
        is RpcError.Unauthorized -> UiText(Res.string.error_unauthorized)
        is RpcError.NotFound -> UiText(Res.string.error_not_found)
        is RpcError.BadRequest -> UiText(Res.string.error_bad_request, listOf(error.reason))
        is RpcError.Conflict -> UiText(Res.string.error_conflict, listOf(error.reason))
        is RpcError.Internal -> UiText(Res.string.error_internal)
    }

/** 再認証が必要なエラーか（呼び出し側が token 破棄 → authorize へ倒す判定）。 */
fun RpcError.requiresReauth(): Boolean = this is RpcError.Unauthorized
