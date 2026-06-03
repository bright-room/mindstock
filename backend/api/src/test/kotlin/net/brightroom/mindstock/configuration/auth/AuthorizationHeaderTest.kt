package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AuthorizationHeaderTest :
    FunSpec({
        test("Bearer <token> → token") {
            AuthorizationHeader("Bearer abc.def.ghi").bearerToken() shouldBe "abc.def.ghi"
        }

        test("scheme は大文字小文字を無視") {
            AuthorizationHeader("bearer abc").bearerToken() shouldBe "abc"
        }

        test("前後・scheme と credentials 間の余分な空白を trim") {
            AuthorizationHeader("  Bearer   abc.def  ").bearerToken() shouldBe "abc.def"
        }

        test("Bearer 以外の scheme → null") {
            AuthorizationHeader("Basic dXNlcjpwYXNz").bearerToken().shouldBeNull()
        }

        test("scheme のみ(credentials 無し)→ null") {
            AuthorizationHeader("Bearer").bearerToken().shouldBeNull()
        }

        test("空文字 → null") {
            AuthorizationHeader("").bearerToken().shouldBeNull()
        }
    })
