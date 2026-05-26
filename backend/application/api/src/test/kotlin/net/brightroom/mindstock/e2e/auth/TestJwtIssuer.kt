package net.brightroom.mindstock.e2e.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.util.Date

/**
 * テスト用 JWT 発行器。デフォルトは「本番経路に通る」JWT。
 * 必要に応じて exp / iss / aud / 鍵を上書きしてエラーケースを作る。
 */
object TestJwtIssuer {
    const val DEFAULT_ISSUER = "test-issuer"
    const val DEFAULT_AUDIENCE = "mindstock-backend-test"

    fun issue(
        subject: String,
        issuer: String = DEFAULT_ISSUER,
        audience: String = DEFAULT_AUDIENCE,
        issuedAt: Instant = Instant.now(),
        expiresAt: Instant = issuedAt.plusSeconds(3600),
        signWith: Algorithm = Algorithm.RSA256(TestKeyPair.publicKey, TestKeyPair.privateKey),
        kid: String = TestKeyPair.KID,
    ): String =
        JWT
            .create()
            .withKeyId(kid)
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(subject)
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(expiresAt))
            .sign(signWith)
}
