package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class AuthCallbackHandler(
    private val authClient: AuthClient,
    private val savedState: String?,
    private val savedVerifier: String?,
) {
    suspend fun handle(
        receivedState: String,
        code: String,
        now: Instant = Clock.System.now(),
    ): Tokens {
        if (savedState == null || savedVerifier == null) {
            throw OidcException("state_missing", "no PKCE state saved for this callback")
        }
        if (savedState != receivedState) {
            throw OidcException("state_mismatch", "expected $savedState, got $receivedState")
        }
        return authClient.exchangeCode(code = code, codeVerifier = savedVerifier, now = now)
    }
}
