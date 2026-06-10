package net.brightroom.mindstock.frontend.core.image

/** 端末から画像ファイルを選ばせ、base64(原バイト)で返す。キャンセル時は null。 */
expect suspend fun pickImageAsBase64(): String?
