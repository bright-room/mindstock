package net.brightroom.mindstock.rpc.product

import kotlinx.serialization.Serializable

/** 画像アップロードの wire 型。原画像を base64 で運ぶ(WS-RPC は JSON 文字列で安全)。 */
@Serializable
data class UploadImageRequest(
    val base64: String,
)
