package net.brightroom.mindstock.e2e

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
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
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.e2e.auth.TestJwks
import net.brightroom.mindstock.e2e.auth.TestJwtIssuer
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.infrastructure.migration.executor.MigrationRunner
import net.brightroom.mindstock.infrastructure.migration.executor.TestContainersPostgres
import net.brightroom.mindstock.infrastructure.migration.executor.testHikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
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
    TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
        val dataSource =
            testHikariDataSource(
                jdbcUrl,
                TestContainersPostgres.username,
                TestContainersPostgres.password,
            )
        try {
            MigrationRunner.migrate(dataSource)
            val database = Database.connect(dataSource)
            testApplication {
                environment {
                    config =
                        ApplicationConfig("application.yaml").mergeWith(
                            MapApplicationConfig(
                                "external.datasource.database.jdbc-url" to jdbcUrl,
                                "external.datasource.database.username" to TestContainersPostgres.username,
                                "external.datasource.database.password" to TestContainersPostgres.password,
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

class E2eContext(
    val httpClient: HttpClient,
    val database: Database,
    val dataSource: DataSource,
) {
    private val opened = mutableListOf<KtorRpcClient>()

    /** Opens a Krpc connection to `/api/v1/$path` with no auth header. */
    fun publicRpcClient(path: String): RpcClient = httpClient.rpc("/api/v1/$path").also { opened += it }

    /**
     * Opens an authenticated Krpc connection for [asUser]. JWT は sub=user.authIdentity.subject() で発行。
     */
    fun authenticatedRpcClient(
        asUser: User,
        path: String,
    ): RpcClient {
        val token = TestJwtIssuer.issue(subject = asUser.authIdentity.subject())
        return authenticatedRpcClientWithToken(token = token, path = path)
    }

    /** 任意の token で接続(エラーケース検証用)。 */
    fun authenticatedRpcClientWithToken(
        token: String,
        path: String,
    ): RpcClient =
        httpClient
            .rpc("/api/v1/$path") {
                authorize(token)
            }.also { opened += it }

    internal fun closeOpenedRpcClients() {
        opened.forEach { it.close("e2e test completed") }
        opened.clear()
    }
}

private fun HttpRequestBuilder.authorize(token: String) {
    headers.append(HttpHeaders.Authorization, "Bearer $token")
}
