package net.brightroom.mindstock.frontend.core.ui

import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.rpc.result.RpcError
import org.jetbrains.compose.resources.StringResource

/**
 * RPC 失敗の共通ハンドラ。各 ViewModel が reauth/toast から 1 つ生成して使う。
 * - [onMutationFailure]: 書込失敗。期限切れは再認証、それ以外はトースト(画面状態は変えない)。
 * - [onLoadFailure]: 読込失敗。期限切れのみ再認証。文言は呼び出し側が UiState.Error に出す(二重表示しない)。
 */
class FailureHandler(
    private val reauth: ReauthController,
    private val toast: ToastController,
) {
    fun onMutationFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }

    /** Conflict かつ [conflictText] 指定時は専用文言、それ以外は [onMutationFailure] と同じ。 */
    fun onMutationFailure(
        error: RpcError,
        conflictText: StringResource?,
    ) {
        if (error.requiresReauth()) {
            reauth.request()
            return
        }
        if (error is RpcError.Conflict && conflictText != null) {
            toast.show(UiText(conflictText))
            return
        }
        toast.show(errorText(error))
    }

    fun onLoadFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request()
    }
}
