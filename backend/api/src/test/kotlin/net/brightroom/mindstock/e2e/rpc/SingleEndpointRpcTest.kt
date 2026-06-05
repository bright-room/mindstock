@file:OptIn(ExperimentalSerializationApi::class)

package net.brightroom.mindstock.e2e.rpc

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders.SecWebSocketProtocol
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
import kotlinx.serialization.ExperimentalSerializationApi
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.configuration.auth.MindstockAuthPlugin
import net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin
import net.brightroom.mindstock.configuration.auth.sessionOf
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.e2e.auth.TestJwtIssuer
import net.brightroom.mindstock.e2e.auth.TestKeyPair
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.presentation.rpc.resident.ResidentRegisterController
import net.brightroom.mindstock.presentation.rpc.session.SessionController
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.session.SessionRpcService
import net.brightroom.mindstock.rpc.session.SessionStatus
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO

/**
 * 単一 `/api/rpc` エンドポイントの e2e。production と同じ構成
 * (CIO 実サーバ + 実 kRPC クライアント、1 接続に複数サービス相乗り)で、
 * whoami / 登録要否(allowUnregistered / requireRegistered)を 1 多重化接続から検証する。
 *
 * testApplication は WS upgrade ができないため、本物の embeddedServer(CIO) を使う。
 * JWKS は mock、JWT は TestJwtIssuer 発行(本番経路で検証される)。
 */
class SingleEndpointRpcTest :
    FunSpec({
        val issuer = TestJwtIssuer.DEFAULT_ISSUER
        val audience = TestJwtIssuer.DEFAULT_AUDIENCE
        val resident = Resident(ResidentId.create(), Profile(DisplayName("Alice")))

        // TestKeyPair の公開鍵を返す JwkProvider(kid に依らず同じ鍵を返す)。
        fun stubJwkProvider(): JwkProvider =
            mockk<JwkProvider>().also { provider ->
                val jwk = mockk<Jwk>()
                every { jwk.publicKey } returns TestKeyPair.publicKey
                every { provider.get(any<String>()) } returns jwk
            }

        // findByAuth は非 suspend。登録済みなら resident、未登録なら ResourceNotFoundException。
        fun residentRepo(registered: Boolean): ResidentRepository =
            mockk<ResidentRepository>().also {
                if (registered) {
                    every { it.findByAuth(any<AuthIdentity>()) } returns resident
                } else {
                    every { it.findByAuth(any<AuthIdentity>()) } throws ResourceNotFoundException("nf")
                }
            }

        // ResidentService.me は非 suspend。
        fun residentService(): ResidentService = mockk<ResidentService>().also { every { it.me(any()) } returns resident }

        // register / rename は非 suspend。register は同一 resident を返す。
        fun residentRegisterService(): ResidentRegisterService =
            mockk<ResidentRegisterService>(relaxed = true).also {
                every { it.register(any(), any()) } returns resident
            }

        @OptIn(ExperimentalEncodingApi::class)
        fun bearer(token: String): String = "mindstock.bearer." + Base64.UrlSafe.encode(token.encodeToByteArray()).trimEnd('=')

        suspend fun <T> withConn(
            registered: Boolean,
            withToken: Boolean = true,
            block: suspend (
                session: SessionRpcService,
                register: ResidentRegisterRpcService,
            ) -> T,
        ): T {
            val server =
                embeddedServer(ServerCIO, port = 0) {
                    install(ContentNegotiation) { jsonIo(CustomJson) }
                    install(Krpc) { serialization { json(KrpcJson) } }
                    install(WsSubprotocolEchoPlugin)
                    install(MindstockAuthPlugin) {
                        jwkProvider = stubJwkProvider()
                        this.issuer = issuer
                        this.audience = audience
                        residentRepository = residentRepo(registered)
                    }
                    val rs = residentService()
                    val rrs = residentRegisterService()
                    routing {
                        rpc("/api/rpc") {
                            registerService<SessionRpcService> { SessionController(rs, sessionOf(call)) }
                            registerService<ResidentRegisterRpcService> {
                                ResidentRegisterController(rrs, sessionOf(call))
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
                val rpcClient =
                    client.rpc("ws://127.0.0.1:$port/api/rpc") {
                        if (withToken) {
                            headers.append(SecWebSocketProtocol, "mindstock.v1")
                            headers.append(SecWebSocketProtocol, bearer(TestJwtIssuer.issue(subject = "sub-1")))
                        }
                    }
                block(rpcClient.withService<SessionRpcService>(), rpcClient.withService<ResidentRegisterRpcService>())
            } finally {
                client.close()
                server.stop(0, 0)
            }
        }

        test("未登録: whoami=Unregistered / registerDisplayName 成立 / rename は Unauthorized") {
            runBlocking {
                withConn(registered = false) { sessionSvc, registerSvc ->
                    sessionSvc.whoami() shouldBe RpcResult.Ok(SessionStatus.Unregistered)
                    registerSvc.registerDisplayName(DisplayName("Alice")).shouldBeInstanceOf<RpcResult.Ok<Resident>>()
                    val r = registerSvc.rename(DisplayName("Bob"))
                    (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
                }
            }
        }

        test("登録済み: whoami=Registered / rename 成立(1 接続で多重化)") {
            runBlocking {
                withConn(registered = true) { sessionSvc, registerSvc ->
                    sessionSvc.whoami() shouldBe RpcResult.Ok(SessionStatus.Registered(resident))
                    registerSvc.rename(DisplayName("Bob")) shouldBe RpcResult.Ok(Unit)
                }
            }
        }

        test("トークン無しはハンドシェイクで接続できない") {
            runBlocking {
                shouldThrow<Throwable> {
                    withConn(registered = false, withToken = false) { sessionSvc, _ -> sessionSvc.whoami() }
                }
            }
        }
    })
