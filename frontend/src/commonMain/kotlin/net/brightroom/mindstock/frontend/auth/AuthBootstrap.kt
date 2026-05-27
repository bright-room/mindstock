package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

enum class PingResult { Success, Unauthorized, Other }

class AuthBootstrap(
    private val authClient: AuthClient,
    private val ping: suspend (Tokens) -> PingResult,
    private val refreshLeewaySeconds: Long = 60,
) {
    suspend fun start(now: Instant = Clock.System.now()): AuthState {
        var tokens = TokenStore.load() ?: return AuthState.LoggedOut
        if (tokens.willExpireWithin(refreshLeewaySeconds, now)) {
            tokens = try {
                authClient.refresh(tokens.refreshToken, now).also { TokenStore.save(it) }
            } catch (_: OidcException) {
                TokenStore.clear()
                return AuthState.LoggedOut
            }
        }
        return when (ping(tokens)) {
            PingResult.Success -> AuthState.Ready(tokens)
            PingResult.Unauthorized -> AuthState.NeedRegister
            PingResult.Other -> AuthState.Error("接続できませんでした")
        }
    }
}
