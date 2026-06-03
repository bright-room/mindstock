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

        fun b64(token: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray())

        test("どちらも無し → null") {
            extractedTokenWith().shouldBeNull()
        }

        test("Authorization から抽出") {
            extractedTokenWith(authHeader = "Bearer abc.def") shouldBe "abc.def"
        }

        test("Sec-WebSocket-Protocol から抽出") {
            val token = "abc.def"
            extractedTokenWith(wsProtocol = "mindstock.v1, mindstock.bearer.${b64(token)}") shouldBe token
        }

        test("両方ある場合は Authorization を優先") {
            extractedTokenWith(
                authHeader = "Bearer auth.token",
                wsProtocol = "mindstock.v1, mindstock.bearer.${b64("ws.token")}",
            ) shouldBe "auth.token"
        }
    })
