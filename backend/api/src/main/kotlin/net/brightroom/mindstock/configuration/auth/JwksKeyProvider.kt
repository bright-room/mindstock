package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwt.interfaces.RSAKeyProvider
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * java-jwt の `Algorithm.RSA256(...)` が要求する [RSAKeyProvider] を
 * `auth0/jwk` の [JwkProvider] から橋渡しする。
 *
 * - JWKS の cache / rate-limit は [JwkProvider] 側で行う
 * - 秘密鍵は不要(検証専用)
 */
class JwksKeyProvider(
    private val jwkProvider: JwkProvider,
) : RSAKeyProvider {
    override fun getPublicKeyById(keyId: String?): RSAPublicKey = jwkProvider.get(keyId).publicKey as RSAPublicKey

    override fun getPrivateKey(): RSAPrivateKey? = null

    override fun getPrivateKeyId(): String? = null
}
