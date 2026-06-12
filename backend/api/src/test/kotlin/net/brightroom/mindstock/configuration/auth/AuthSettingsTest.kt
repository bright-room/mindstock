package net.brightroom.mindstock.configuration.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.config.MapApplicationConfig

class AuthSettingsTest :
    FunSpec({
        fun config(vararg pairs: Pair<String, String>) = MapApplicationConfig(*pairs.toList().toTypedArray())

        test("全て揃っていれば AuthSettings を返す") {
            val cfg =
                config(
                    "external.auth.issuer" to "https://idp.example",
                    "external.auth.audience" to "aud",
                    "external.auth.jwks-url" to "https://idp.example/jwks",
                )
            val settings = requireAuthSettings(cfg)
            settings.issuer shouldBe "https://idp.example"
            settings.audience shouldBe "aud"
            settings.jwksUrl shouldBe "https://idp.example/jwks"
        }

        test("issuer が未設定なら案内付きで即時エラー") {
            val cfg =
                config(
                    "external.auth.audience" to "aud",
                    "external.auth.jwks-url" to "https://idp.example/jwks",
                )
            val ex = shouldThrow<IllegalStateException> { requireAuthSettings(cfg) }
            ex.message!! shouldContain "AUTH_ISSUER"
            ex.message!! shouldContain "mise run up"
        }

        test("audience が空文字でも即時エラー") {
            val cfg =
                config(
                    "external.auth.issuer" to "https://idp.example",
                    "external.auth.audience" to "",
                    "external.auth.jwks-url" to "https://idp.example/jwks",
                )
            shouldThrow<IllegalStateException> { requireAuthSettings(cfg) }
        }
    })
