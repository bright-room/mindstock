package net.brightroom.mindstock.frontend.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** トーストメッセージ。文言は UiText（UI 層で resolve）。 */
data class ToastMessage(
    val text: UiText,
)

/** 単一トーストチャネル。feature が show、app 層ホストが購読して描画。 */
class ToastController {
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    fun show(text: UiText) {
        _current.value = ToastMessage(text)
    }

    fun dismiss() {
        _current.value = null
    }
}
