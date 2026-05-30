package net.brightroom.mindstock.configuration.rpc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RpcBoundaryTest :
    FunSpec({
        fun sessionWith(exp: Instant): MindstockSession =
            MindstockSession(
                identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
                userId = UserId(Uuid.random()),
                exp = exp,
                callId = Uuid.random(),
            )

        test("session.exp が過去 → Err(Unauthorized(token expired))、block は呼ばれない") {
            var called = false
            val result =
                runBlocking {
                    rpcBoundary(sessionWith(Clock.System.now() - 1.hours)) {
                        called = true
                        1
                    }
                }
            result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            result.error.shouldBeInstanceOf<RpcError.Unauthorized>()
            (result.error as RpcError.Unauthorized).reason shouldBe "token expired"
            called shouldBe false
        }

        test("正常系: block の戻り値が RpcResult.Ok に包まれる") {
            val result =
                runBlocking {
                    rpcBoundary(sessionWith(Clock.System.now() + 1.hours)) { "hello" }
                }
            result shouldBe RpcResult.Ok("hello")
        }

        test("block 内で ResourceNotFoundException → Err(NotFound) でメッセージがパススルー") {
            val result =
                runBlocking {
                    rpcBoundary<Int>(sessionWith(Clock.System.now() + 1.hours)) {
                        throw ResourceNotFoundException("household not found: test-id")
                    }
                }
            result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            val err = result.error
            err.shouldBeInstanceOf<RpcError.NotFound>()
            err.message shouldBe "household not found: test-id"
        }

        test("block 内で IllegalStateException → Err(Internal)") {
            val result =
                runBlocking {
                    rpcBoundary<Int>(sessionWith(Clock.System.now() + 1.hours)) { error("boom") }
                }
            result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            result.error.shouldBeInstanceOf<RpcError.Internal>()
        }

        test("block 内で IllegalArgumentException → Err(BadRequest) でメッセージがパススルー") {
            val result =
                runBlocking {
                    rpcBoundary<Int>(sessionWith(Clock.System.now() + 1.hours)) {
                        require(false) { "quantity must be > 0" }
                        1
                    }
                }
            result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            val err = result.error
            err.shouldBeInstanceOf<RpcError.BadRequest>()
            err.reason shouldBe "quantity must be > 0"
        }

        test("Unit を返す block は RpcResult.Ok(Unit) に包まれる") {
            val result =
                runBlocking {
                    rpcBoundary(sessionWith(Clock.System.now() + 1.hours)) { }
                }
            result shouldBe RpcResult.Ok(Unit)
        }

        test("block 内の CancellationException は伝播する(Internal に変換しない)") {
            shouldThrow<CancellationException> {
                runBlocking {
                    rpcBoundary<Int>(sessionWith(Clock.System.now() + 1.hours)) {
                        throw CancellationException("cancelled")
                    }
                }
            }
        }
    })
