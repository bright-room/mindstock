@file:Suppress("ktlint:standard:filename")

package net.brightroom.mindstock.frontend.core.image

/** 画像ピッカーの結果。キャンセルを null でなく型で表す(nullable 禁止原則)。 */
sealed interface ImagePickResult {
    /** 画像を選択。base64(原バイト)。 */
    data class Selected(
        val base64: String,
    ) : ImagePickResult

    /** キャンセル/失敗。 */
    data object Cancelled : ImagePickResult
}

/** 端末から画像ファイルを選ばせ、base64(原バイト)で返す。キャンセル/失敗は [ImagePickResult.Cancelled]。 */
internal expect suspend fun pickImage(): ImagePickResult
