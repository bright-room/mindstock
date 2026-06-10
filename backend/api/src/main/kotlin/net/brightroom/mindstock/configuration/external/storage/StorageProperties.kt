package net.brightroom.mindstock.configuration.external.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StorageProperties(
    @SerialName("endpoint") val endpoint: String,
    @SerialName("region") val region: String,
    @SerialName("bucket") val bucket: String,
    @SerialName("access-key") val accessKey: String,
    @SerialName("secret-key") val secretKey: String,
    @SerialName("cors-allowed-origins") val corsAllowedOrigins: String,
)
