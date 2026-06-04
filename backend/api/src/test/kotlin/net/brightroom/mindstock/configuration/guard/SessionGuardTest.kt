@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.guard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.brightroom.mindstock.configuration.auth.MindstockSession
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

        fun active() = MindstockSession.Registered(identity, ResidentId.create(), Clock.System.now().plus(1.hours), Uuid.random())

        fun expired() = MindstockSession.Registered(identity, ResidentId.create(), Clock.System.now().minus(1.hours), Uuid.random())

        test("期限切れ session は Unauthorized で短絡(block は実行されない)") {
            var ran = false
            val result =
                guarded<Unit>(expired()) {
                    ran = true
                    RpcResult.Ok(Unit)
                }
            ran shouldBe false
            val err = result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            err.error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        test("正常系は block の結果をそのまま返す") {
            guarded(active()) { RpcResult.Ok(42) } shouldBe RpcResult.Ok(42)
        }

        test("IllegalArgumentException は BadRequest") {
            val r = guarded<Unit>(active()) { throw IllegalArgumentException("bad") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.BadRequest>()
        }

        test("ResourceNotFoundException は NotFound") {
            val r = guarded<Unit>(active()) { throw ResourceNotFoundException("x not found") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.NotFound>()
        }

        test("MembershipRequiredException は Unauthorized") {
            val r = guarded<Unit>(active()) { throw MembershipRequiredException("not member") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        test("DuplicateJanException は Conflict") {
            val r = guarded<Unit>(active()) { throw DuplicateJanException("dup") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Conflict>()
        }

        test("想定外例外は Internal") {
            val r = guarded<Unit>(active()) { throw RuntimeException("boom") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Internal>()
        }
    })
