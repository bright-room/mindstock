package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.Base64

class WsBearerTokenExtractorTest :
    FunSpec({
        suspend fun extractedTokenWith(
            authHeader: String? = null,
            wsProtocol: String? = null,
        ): String? {
            var captured: String? = null
            testApplication {
                application {
                    routing {
                        get("/probe") {
                            captured = WsBearerTokenExtractor.extractRaw(call)
                            call.respondText("ok")
                        }
                    }
                }
                client.get("/probe") {
                    if (authHeader != null) header(HttpHeaders.Authorization, authHeader)
                    if (wsProtocol != null) header(HttpHeaders.SecWebSocketProtocol, wsProtocol)
                }
            }
            return captured
        }

        test("returns null when no auth header and no Sec-WebSocket-Protocol") {
            extractedTokenWith().shouldBeNull()
        }

        test("returns token from Authorization Bearer header") {
            extractedTokenWith(authHeader = "Bearer abc.def.ghi") shouldBe "abc.def.ghi"
        }

        test("returns token from Sec-WebSocket-Protocol when it has a mindstock.bearer entry") {
            val token = "abc.def.ghi"
            val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray())
            extractedTokenWith(wsProtocol = "mindstock.v1, mindstock.bearer.$b64") shouldBe token
        }

        test("prefers Authorization header over Sec-WebSocket-Protocol when both present") {
            val token = "ws.token"
            val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray())
            extractedTokenWith(
                authHeader = "Bearer auth.token",
                wsProtocol = "mindstock.v1, mindstock.bearer.$b64",
            ) shouldBe "auth.token"
        }

        test("returns null when Sec-WebSocket-Protocol has no mindstock.bearer entry") {
            extractedTokenWith(wsProtocol = "mindstock.v1, other.proto").shouldBeNull()
        }

        test("trims whitespace between Sec-WebSocket-Protocol entries") {
            val token = "abc"
            val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray())
            extractedTokenWith(wsProtocol = "mindstock.v1 ,  mindstock.bearer.$b64") shouldBe token
        }
    })
