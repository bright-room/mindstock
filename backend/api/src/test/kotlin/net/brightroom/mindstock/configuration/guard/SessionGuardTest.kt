@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.guard

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.ArchivedProductMovementException
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class SessionGuardTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val residentId = ResidentId.create()

        fun registered(exp: kotlin.time.Instant = Clock.System.now().plus(1.hours)) =
            MindstockSession.Registered(identity, residentId, exp, Uuid.random())

        fun unregistered(exp: kotlin.time.Instant = Clock.System.now().plus(1.hours)) =
            MindstockSession.Unregistered(identity, exp, Uuid.random())

        // ---- allowUnregistered ----
        test("allowUnregistered: 未登録でも block を実行する") {
            allowUnregistered(unregistered()) { RpcResult.Ok(42) } shouldBe RpcResult.Ok(42)
        }

        test("allowUnregistered: 登録済みでも block を実行する") {
            allowUnregistered(registered()) { RpcResult.Ok(7) } shouldBe RpcResult.Ok(7)
        }

        test("allowUnregistered: 期限切れは Unauthorized で短絡") {
            var ran = false
            val r =
                allowUnregistered<Unit>(unregistered(exp = Clock.System.now().minus(1.hours))) {
                    ran = true
                    RpcResult.Ok(Unit)
                }
            ran shouldBe false
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        // ---- requireRegistered ----
        test("requireRegistered: 登録済みは residentId を渡して block 実行") {
            var seen: ResidentId? = null
            val r =
                requireRegistered(registered()) { id ->
                    seen = id
                    RpcResult.Ok(1)
                }
            seen shouldBe residentId
            r shouldBe RpcResult.Ok(1)
        }

        test("requireRegistered: 未登録は Unauthorized(block は実行されない / fail-closed)") {
            var ran = false
            val r =
                requireRegistered<Unit>(unregistered()) {
                    ran = true
                    RpcResult.Ok(Unit)
                }
            ran shouldBe false
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        test("requireRegistered: 期限切れは Unauthorized で短絡") {
            val r =
                requireRegistered<Unit>(registered(exp = Clock.System.now().minus(1.hours))) {
                    RpcResult.Ok(Unit)
                }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        // ---- 例外翻訳(requireRegistered 経由で共通処理を確認)----
        test("IllegalArgumentException は BadRequest") {
            val r = requireRegistered<Unit>(registered()) { throw IllegalArgumentException("bad") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.BadRequest>()
        }

        test("ResourceNotFoundException は NotFound") {
            val r = requireRegistered<Unit>(registered()) { throw ResourceNotFoundException("x not found") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.NotFound>()
        }

        test("MembershipRequiredException は Unauthorized") {
            val r = requireRegistered<Unit>(registered()) { throw MembershipRequiredException("not member") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        test("DuplicateJanException は Conflict") {
            val r = requireRegistered<Unit>(registered()) { throw DuplicateJanException("dup") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Conflict>()
        }

        test("ArchivedProductMovementException は Conflict") {
            val r = requireRegistered<Unit>(registered()) { throw ArchivedProductMovementException("archived") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Conflict>()
        }

        test("想定外例外は Internal") {
            val r = requireRegistered<Unit>(registered()) { throw RuntimeException("boom") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Internal>()
        }

        test("CancellationException は握り潰さず再 throw する") {
            shouldThrow<CancellationException> {
                requireRegistered<Unit>(registered()) { throw CancellationException("cancelled") }
            }
        }
    })
