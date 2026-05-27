package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresAt: Instant,
) {
    fun willExpireWithin(seconds: Int, now: Instant = Clock.System.now()): Boolean =
        expiresAt <= now.plusSeconds(seconds)

    private fun Instant.plusSeconds(s: Int): Instant = Instant.fromEpochSeconds(epochSeconds + s)

    companion object {
        fun fromTokenResponse(
            accessToken: String,
            refreshToken: String,
            idToken: String,
            expiresInSeconds: Long,
            now: Instant = Clock.System.now(),
        ): Tokens = Tokens(accessToken, refreshToken, idToken, Instant.fromEpochSeconds(now.epochSeconds + expiresInSeconds))
    }
}
