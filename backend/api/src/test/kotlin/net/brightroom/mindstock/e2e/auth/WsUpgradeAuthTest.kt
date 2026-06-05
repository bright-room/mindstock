package net.brightroom.mindstock.e2e.auth

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.SecWebSocketProtocol
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.configuration.auth.MindstockAuthPlugin
import net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * 認証付き WebSocket upgrade が有効トークンで成立するか(handshake 101)を、
 * 本番と同じ CIO エンジンの実サーバ + 実 WS クライアントで検証する回帰テスト。
 * testApplication は upgrade 不可のためこのバグはすり抜けていた。
 */
class WsUpgradeAuthTest :
    FunSpec({
        val issuer = TestJwtIssuer.DEFAULT_ISSUER
        val audience = TestJwtIssuer.DEFAULT_AUDIENCE

        fun stubJwkProvider(): JwkProvider =
            mockk<JwkProvider>().also { provider ->
                val jwk = mockk<Jwk>()
                every { jwk.publicKey } returns TestKeyPair.publicKey
                every { provider.get(any<String>()) } returns jwk
            }

        fun unregisteredRepo(): ResidentRepository =
            mockk<ResidentRepository>().also {
                every { it.findByAuth(any<AuthIdentity>()) } throws ResourceNotFoundException("not found")
            }

        // 本番同等(CIO + echo plugin + auth plugin)の WS サーバを立て、与えられた
        // request 設定で WS upgrade → "ping" を送り echo を受け取って返す。
        suspend fun roundtrip(configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String {
            val server =
                embeddedServer(ServerCIO, port = 0) {
                    install(ServerWebSockets)
                    install(WsSubprotocolEchoPlugin)
                    install(MindstockAuthPlugin) {
                        jwkProvider = stubJwkProvider()
                        this.issuer = issuer
                        this.audience = audience
                        residentRepository = unregisteredRepo()
                    }
                    routing {
                        webSocket("/probe") {
                            for (frame in incoming) {
                                if (frame is Frame.Text) send(Frame.Text("echo:" + frame.readText()))
                            }
                        }
                    }
                }
            server.start(wait = false)
            val port =
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            val client = HttpClient(ClientCIO) { install(ClientWebSockets) }
            return try {
                var received = ""
                client.webSocket(host = "127.0.0.1", port = port, path = "/probe", request = configure) {
                    send(Frame.Text("ping"))
                    val frame = incoming.receive()
                    if (frame is Frame.Text) received = frame.readText()
                }
                received
            } finally {
                client.close()
                server.stop(0, 0)
            }
        }

        @OptIn(ExperimentalEncodingApi::class)
        fun bearerSubprotocol(token: String): String = "mindstock.bearer." + Base64.UrlSafe.encode(token.encodeToByteArray()).trimEnd('=')

        test("有効 JWT + Authorization ヘッダで WS upgrade が成立する(101 + echo 往復)") {
            runBlocking {
                val token = TestJwtIssuer.issue(subject = "zitadel-sub-1")
                roundtrip { header(HttpHeaders.Authorization, "Bearer $token") } shouldBe "echo:ping"
            }
        }

        test("有効 JWT + bearer subprotocol(本番のブラウザ経路)で WS upgrade が成立する") {
            runBlocking {
                val token = TestJwtIssuer.issue(subject = "zitadel-sub-1")
                roundtrip {
                    header(SecWebSocketProtocol, "mindstock.v1")
                    header(SecWebSocketProtocol, bearerSubprotocol(token))
                } shouldBe "echo:ping"
            }
        }

        test("大きい JWT(本番 Zitadel 相当・長い subprotocol ヘッダ)でも WS upgrade が成立する") {
            runBlocking {
                // Zitadel の実トークンは roles 等を含み subprotocol ヘッダが ~1100 char になる。
                val bigSubject = "z".repeat(1200)
                val token = TestJwtIssuer.issue(subject = bigSubject)
                roundtrip {
                    header(SecWebSocketProtocol, "mindstock.v1")
                    header(SecWebSocketProtocol, bearerSubprotocol(token))
                } shouldBe "echo:ping"
            }
        }
    })
