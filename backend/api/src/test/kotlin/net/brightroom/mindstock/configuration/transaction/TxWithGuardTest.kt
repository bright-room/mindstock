package net.brightroom.mindstock.configuration.transaction

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.test.TestDataSource
import net.brightroom.mindstock.test.testHikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class TxWithGuardTest :
    FunSpec({
        fun sessionWith(exp: Instant): MindstockSession =
            MindstockSession(
                identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
                userId = UserId(Uuid.random()),
                exp = exp,
                callId = Uuid.random(),
            )

        test("session.exp が過去 → Err(Unauthorized(token expired))、block は呼ばれない") {
            val db = mockk<Database>()
            var called = false
            val expired = Clock.System.now() - 1.hours
            val result =
                runBlocking {
                    tx<Int>(db, sessionWith(expired)) {
                        called = true
                        RpcResult.Ok(1)
                    }
                }
            result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            result.error.shouldBeInstanceOf<RpcError.Unauthorized>()
            (result.error as RpcError.Unauthorized).reason shouldBe "token expired"
            called shouldBe false
        }

        test("block 内で IllegalStateException → Err(Internal)") {
            TestDataSource.withFreshSchema { jdbcUrl, _ ->
                val ds = testHikariDataSource(jdbcUrl, TestDataSource.user, TestDataSource.password)
                try {
                    Flyway
                        .configure()
                        .dataSource(ds)
                        .locations("classpath:db/migration")
                        .load()
                        .migrate()
                    val database = Database.connect(ds)
                    val result =
                        runBlocking {
                            tx<Int>(database, sessionWith(Clock.System.now() + 1.hours)) {
                                error("boom")
                            }
                        }
                    result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                    result.error.shouldBeInstanceOf<RpcError.Internal>()
                } finally {
                    ds.close()
                }
            }
        }

        test("正常系: block が Ok を返せばそのまま返る") {
            TestDataSource.withFreshSchema { jdbcUrl, _ ->
                val ds = testHikariDataSource(jdbcUrl, TestDataSource.user, TestDataSource.password)
                try {
                    Flyway
                        .configure()
                        .dataSource(ds)
                        .locations("classpath:db/migration")
                        .load()
                        .migrate()
                    val database = Database.connect(ds)
                    val result =
                        runBlocking {
                            tx<String>(database, sessionWith(Clock.System.now() + 1.hours)) {
                                RpcResult.Ok("hello")
                            }
                        }
                    result shouldBe RpcResult.Ok("hello")
                } finally {
                    ds.close()
                }
            }
        }
    })
