package net.brightroom.mindstock.e2e

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.testApplication
import kotlinx.rpc.RpcClient
import kotlinx.rpc.krpc.ktor.client.KtorRpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.infrastructure.migration.executor.MigrationRunner
import net.brightroom.mindstock.infrastructure.migration.executor.TestContainersPostgres
import net.brightroom.mindstock.infrastructure.migration.executor.testHikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource
import kotlin.uuid.ExperimentalUuidApi

/**
 * 1 e2e test = 1 fresh Postgres schema + 1 testApplication + 1 krpc HttpClient.
 *
 * Inside the block:
 * - [E2eContext.httpClient] is a configured Krpc-capable HttpClient
 * - [E2eContext.database] is the Exposed Database connected to the fresh schema
 *
 * Tests construct service handles via the [E2eContext.publicRpcClient] /
 * [E2eContext.authenticatedRpcClient] helpers, which return an [RpcClient]
 * the caller pairs with `.withService<SomeRpcService>()`.
 */
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
                    ctx.closeOpenedRpcClients()
                    client.close()
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

    /** Opens a Krpc connection to `/api/v1/$path` with a Bearer token derived from [asUser]'s id. */
    @OptIn(ExperimentalUuidApi::class)
    fun authenticatedRpcClient(
        asUser: User,
        path: String,
    ): RpcClient {
        val token = asUser.id().toString()
        return httpClient.rpc("/api/v1/$path") {
            authorize(token)
        }.also { opened += it }
    }

    internal fun closeOpenedRpcClients() {
        opened.forEach { it.close("e2e test completed") }
        opened.clear()
    }
}

private fun HttpRequestBuilder.authorize(token: String) {
    headers.append("Authorization", "Bearer $token")
}
