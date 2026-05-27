package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresAt: Instant,
) {
    fun willExpireWithin(seconds: Long, now: Instant = Clock.System.now()): Boolean =
        expiresAt <= now + seconds.seconds

    companion object {
        fun fromTokenResponse(
            accessToken: String,
            refreshToken: String,
            idToken: String,
            expiresInSeconds: Long,
            now: Instant = Clock.System.now(),
        ): Tokens = Tokens(accessToken, refreshToken, idToken, now + expiresInSeconds.seconds)
    }
}
