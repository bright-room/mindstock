package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class WsSubprotocolEchoPluginTest :
    FunSpec({
        suspend fun probeWith(wsProtocol: String? = null): HttpResponse {
            lateinit var response: HttpResponse
            testApplication {
                application {
                    install(WsSubprotocolEchoPlugin)
                    routing { get("/probe") { call.respondText("ok") } }
                }
                response =
                    client.get("/probe") {
                        if (wsProtocol != null) header(HttpHeaders.SecWebSocketProtocol, wsProtocol)
                    }
            }
            return response
        }

        test("mindstock.v1 提示 → mindstock.v1 を echo") {
            val res = probeWith(wsProtocol = "mindstock.v1, mindstock.bearer.xyz")
            res.headers[HttpHeaders.SecWebSocketProtocol] shouldBe "mindstock.v1"
        }

        test("mindstock.bearer.* は response header に echo しない") {
            val res = probeWith(wsProtocol = "mindstock.v1, mindstock.bearer.secrettoken")
            (res.headers[HttpHeaders.SecWebSocketProtocol] ?: "") shouldNotContain "bearer"
            (res.headers[HttpHeaders.SecWebSocketProtocol] ?: "") shouldNotContain "secrettoken"
        }

        test("Sec-WebSocket-Protocol 無し → echo しない") {
            val res = probeWith(wsProtocol = null)
            (res.headers[HttpHeaders.SecWebSocketProtocol] ?: "") shouldNotContain "mindstock.v1"
        }

        test("mindstock.v1 を提示しない場合は echo しない") {
            val res = probeWith(wsProtocol = "other.proto")
            (res.headers[HttpHeaders.SecWebSocketProtocol] ?: "") shouldNotContain "mindstock.v1"
        }
    })
