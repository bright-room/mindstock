package net.brightroom.mindstock.e2e.auth

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * テスト suite 単位で 1 つ生成される RSA 鍵ペア。
 * 全テスト共通の kid="test-key-1" を使う。
 */
object TestKeyPair {
    const val KID: String = "test-key-1"

    val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    val publicKey: RSAPublicKey get() = keyPair.public as RSAPublicKey
    val privateKey: RSAPrivateKey get() = keyPair.private as RSAPrivateKey
}
