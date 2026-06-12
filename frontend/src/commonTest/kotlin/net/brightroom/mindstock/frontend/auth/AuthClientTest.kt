package net.brightroom.mindstock.frontend.auth

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class AuthClientTest {
    @Test
    fun buildAuthorizeUrl_includes_pkce_and_s256() {
        val url =
            AuthClient.buildAuthorizeUrl(
                issuer = "https://id.example",
                clientId = "cid",
                redirectUri = "https://app/cb",
                scope = "openid profile",
                state = "st",
                codeChallenge = "chal",
            )
        url shouldContain "https://id.example/oauth/v2/authorize?"
        url shouldContain "response_type=code"
        url shouldContain "client_id=cid"
        url shouldContain "code_challenge=chal"
        url shouldContain "code_challenge_method=S256"
        url shouldContain "scope=openid%20profile"
    }
}
