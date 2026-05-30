package net.brightroom.mindstock.e2e

import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.RpcClient
import kotlinx.rpc.krpc.ktor.client.KtorRpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.e2e.auth.TestJwks
import net.brightroom.mindstock.e2e.auth.TestJwtIssuer
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
import net.brightroom.mindstock.test.TestDataSource
import net.brightroom.mindstock.test.testHikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.Base64
import javax.sql.DataSource

private const val TEST_JWKS_PATH = "/test-jwks"

/**
 * 全 e2e テストで共有する JWKS スタブ Server。
 *
 * `JwkProvider` は `java.net.URL` 経由で実際に HTTP fetch するため、testApplication
 * の in-memory routing では到達できない。よって本物の TCP ポートを 1 つだけバインドし、
 * `kid` が定数 ([TestKeyPair.KID]) なので 1 サーバーで全テストを賄う。
 *
 * JVM shutdown hook で停止する。
 */
private object SharedJwksServer {
    private val server by lazy {
        val s =
            embeddedServer(CIO, port = 0) {
                routing {
                    get(TEST_JWKS_PATH) {
                        call.respondText(TestJwks.asJsonString(), ContentType.Application.Json)
                    }
                }
            }
        s.start(wait = false)
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { s.stop(0, 0) } })
        s
    }

    val jwksUrl: String by lazy {
        val port =
            runBlocking {
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }
        "http://localhost:$port$TEST_JWKS_PATH"
    }
}

fun e2eTest(block: suspend E2eContext.() -> Unit) {
    TestDataSource.withFreshSchema { jdbcUrl, _ ->
        val dataSource =
            testHikariDataSource(
                jdbcUrl,
                TestDataSource.user,
                TestDataSource.password,
            )
        try {
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            val database = Database.connect(dataSource)
            testApplication {
                environment {
                    config =
                        ApplicationConfig("application.yaml").mergeWith(
                            MapApplicationConfig(
                                "external.datasource.database.jdbc-url" to jdbcUrl,
                                "external.datasource.database.username" to TestDataSource.user,
                                "external.datasource.database.password" to TestDataSource.password,
                                // 本番 application.yaml は maximum-pool-size: 10。テストでは
                                // RPC を概ね直列に叩くため 2 で足り、接続枯渇を防ぐ。
                                "external.datasource.database.maximum-pool-size" to "2",
                                "external.auth.issuer" to TestJwtIssuer.DEFAULT_ISSUER,
                                "external.auth.audience" to TestJwtIssuer.DEFAULT_AUDIENCE,
                                "external.auth.jwks-url" to SharedJwksServer.jwksUrl,
                            ),
                        )
                }
                val client =
                    createClient {
                        installKrpc {
                            serialization { json(KrpcJson) }
                        }
                    }
                val ctx = E2eContext(client, database, dataSource)
                try {
                    ctx.block()
                } finally {
                    try {
                        ctx.closeOpenedRpcClients()
                    } finally {
                        client.close()
                    }
                }
            }
        } finally {
            dataSource.close()
        }
    }
}

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class E2eContext(
    val httpClient: HttpClient,
    val database: Database,
    val dataSource: DataSource,
) {
    private val opened = mutableListOf<KtorRpcClient>()

    /** Opens a Krpc connection to `/api/v1/$path` with no auth header. */
    fun publicRpcClient(path: String): RpcClient = httpClient.rpc("/api/v1/$path").also { opened += it }

    /**
     * Opens an authenticated Krpc connection for [asUser].
     *
     * [asUser] は `authIdentity` を持たないため、JWT の sub は `UsersTable.zitadel_sub`
     * を `asUser.userId` で引いて取得する(DB に該当行が無ければ失敗)。
     */
    fun authenticatedRpcClient(
        asUser: Profile,
        path: String,
    ): RpcClient {
        val subject =
            transaction(database) {
                UsersTable
                    .selectAll()
                    .where { UsersTable.id eq asUser.userId() }
                    .single()[UsersTable.zitadel_sub]
            }
        val token = TestJwtIssuer.issue(subject = subject)
        return authenticatedRpcClientWithToken(token = token, path = path)
    }

    /** 任意の sub で JWT を発行して接続(DB に存在しないユーザーをシミュレートするため)。 */
    fun authenticatedRpcClientWithSubject(
        subject: String,
        path: String,
    ): RpcClient {
        val token = TestJwtIssuer.issue(subject = subject)
        return authenticatedRpcClientWithToken(token = token, path = path)
    }

    /**
     * 任意の token で接続(エラーケース検証用)。
     *
     * 本番フロント([RpcClientFactory])と同じく `Sec-WebSocket-Protocol` の
     * `mindstock.bearer.<base64url(jwt)>` で token を運ぶ。テストが本番経路を踏むことで
     * 認証経路の忠実性を担保する。
     */
    fun authenticatedRpcClientWithToken(
        token: String,
        path: String,
    ): RpcClient {
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray())
        return httpClient
            .rpc("/api/v1/$path") {
                // 本番ブラウザ([RpcClientFactory])と同じく subprotocol を 2 本に分けて送る。
                headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
                headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.$b64")
            }.also { opened += it }
    }

    /**
     * 2 つの `mindstock.bearer.*` entry を同時に提示して接続(fail-closed 検証用)。
     * 各 token 単体なら有効でも、曖昧なため extractor が null を返し 401 になることを確認する。
     */
    fun rpcClientWithDuplicateBearer(
        firstToken: String,
        secondToken: String,
        path: String,
    ): RpcClient {
        fun encode(token: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray())
        return httpClient
            .rpc("/api/v1/$path") {
                headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
                headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.${encode(firstToken)}")
                headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.${encode(secondToken)}")
            }.also { opened += it }
    }

    internal fun closeOpenedRpcClients() {
        opened.forEach { it.close("e2e test completed") }
        opened.clear()
    }
}
