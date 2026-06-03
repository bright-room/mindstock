package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.Base64

class WebSocketProtocolsTest :
    FunSpec({
        fun protocols(vararg raw: String) = WebSocketProtocols.from(raw.toList())

        fun b64(token: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray())

        test("has: 提示された protocol を検出") {
            protocols("mindstock.v1, mindstock.bearer.x").has("mindstock.v1") shouldBe true
        }

        test("has: 提示されていない protocol は false") {
            protocols("other.proto").has("mindstock.v1") shouldBe false
        }

        test("bearerToken: mindstock.bearer.<b64> を decode") {
            val token = "abc.def.ghi"
            protocols("mindstock.v1, mindstock.bearer.${b64(token)}").bearerToken() shouldBe token
        }

        test("bearerToken: bearer entry 無し → null") {
            protocols("mindstock.v1, other.proto").bearerToken().shouldBeNull()
        }

        test("複数ヘッダ行・entry 間の空白を trim") {
            val token = "abc"
            WebSocketProtocols.from(listOf("mindstock.v1 ", " mindstock.bearer.${b64(token)}")).bearerToken() shouldBe token
        }

        test("bearerToken: 不正 base64 → null") {
            protocols("mindstock.bearer.!!!notbase64!!!").bearerToken().shouldBeNull()
        }
    })
