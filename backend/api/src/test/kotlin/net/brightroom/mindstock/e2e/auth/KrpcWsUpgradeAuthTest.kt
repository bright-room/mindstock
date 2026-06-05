package net.brightroom.mindstock.e2e.auth

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.SecWebSocketProtocol
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.configuration.auth.MindstockAuthPlugin
import net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO

/**
 * 本番の RPC 経路(kotlinx-rpc `rpc()` route + kRPC client + bearer subprotocol)で
 * 認証付き WS upgrade が成立するかを、本番と同じ CIO エンジンで検証する回帰テスト。
 */
class KrpcWsUpgradeAuthTest :
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

        val stubService =
            object : ResidentRegisterRpcService {
                override suspend fun registerDisplayName(displayName: DisplayName): RpcResult<Resident, RpcError> = error("unused")

                override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)
            }

        @OptIn(ExperimentalEncodingApi::class)
        fun bearerSubprotocol(token: String): String = "mindstock.bearer." + Base64.UrlSafe.encode(token.encodeToByteArray()).trimEnd('=')

        // 本番 RoutingConfiguration と同じプラグイン構成・同じ CIO エンジンで `/api/v1/resident/register`
        // (RequireRegisteredUser の外 = 有効 JWT なら未登録でも通るルート)を立て、
        // 与えられた request 設定で kRPC client から rename() を呼び結果を返す。
        suspend fun callRename(configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit): RpcResult<Unit, RpcError> {
            val server =
                embeddedServer(ServerCIO, port = 0) {
                    install(ContentNegotiation) { jsonIo(CustomJson) }
                    install(Krpc) { serialization { json(KrpcJson) } }
                    install(WsSubprotocolEchoPlugin)
                    install(MindstockAuthPlugin) {
                        jwkProvider = stubJwkProvider()
                        this.issuer = issuer
                        this.audience = audience
                        residentRepository = unregisteredRepo()
                    }
                    routing {
                        route("/api/v1") {
                            rpc("/resident/register") {
                                registerService<ResidentRegisterRpcService> { stubService }
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
            val client =
                HttpClient(ClientCIO) {
                    installKrpc { serialization { json(KrpcJson) } }
                    install(ClientWebSockets)
                }
            return try {
                val rpcClient = client.rpc("ws://127.0.0.1:$port/api/v1/resident/register", configure)
                rpcClient.withService<ResidentRegisterRpcService>().rename(DisplayName("Alice"))
            } finally {
                client.close()
                server.stop(0, 0)
            }
        }

        test("有効 JWT + bearer subprotocol + kRPC route で RPC 呼び出しが成立する(本番のブラウザ経路)") {
            runBlocking {
                val token = TestJwtIssuer.issue(subject = "zitadel-sub-1")
                callRename {
                    headers.append(SecWebSocketProtocol, "mindstock.v1")
                    headers.append(SecWebSocketProtocol, bearerSubprotocol(token))
                } shouldBe RpcResult.Ok(Unit)
            }
        }

        // 旧セッションの「WS upgrade + Authorization ヘッダ → 401」切り分け(python raw socket)と
        // 同じリクエスト形(mindstock.v1 を offer しつつ token は Authorization ヘッダ)を kRPC route で再現。
        test("有効 JWT + Authorization ヘッダ + kRPC route(旧 401 切り分けと同形)でも成立する") {
            runBlocking {
                val token = TestJwtIssuer.issue(subject = "zitadel-sub-1")
                callRename {
                    headers.append(SecWebSocketProtocol, "mindstock.v1")
                    headers.append(HttpHeaders.Authorization, "Bearer $token")
                } shouldBe RpcResult.Ok(Unit)
            }
        }
    })
