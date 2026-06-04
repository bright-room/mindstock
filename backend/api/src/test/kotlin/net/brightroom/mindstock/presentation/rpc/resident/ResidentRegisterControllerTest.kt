@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.resident

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class ResidentRegisterControllerTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val residentId = ResidentId.create()
        val exp = Clock.System.now().plus(1.hours)

        val registeredSession = MindstockSession.Registered(identity, residentId, exp, Uuid.random())
        val unregisteredSession = MindstockSession.Unregistered(identity, exp, Uuid.random())

        val displayName = DisplayName("じぶん")

        test("registerDisplayName on Unregistered → register を呼び Ok(created) を返す") {
            val service = mockk<ResidentRegisterService>(relaxed = true)
            val created = Resident(residentId, Profile(displayName))
            every { service.register(identity, displayName) } returns created

            val controller = ResidentRegisterController(service, unregisteredSession)
            controller.registerDisplayName(displayName) shouldBe RpcResult.Ok(created)
        }

        test("registerDisplayName on Registered → Err(Conflict)、register は呼ばれない") {
            val service = mockk<ResidentRegisterService>(relaxed = true)

            val controller = ResidentRegisterController(service, registeredSession)
            val result = controller.registerDisplayName(displayName)

            (result as RpcResult.Err).error shouldBe RpcError.Conflict(reason = "already registered")
            verify(exactly = 0) { service.register(any(), any()) }
        }

        test("rename on Registered → rename を呼び Ok(Unit) を返す") {
            val service = mockk<ResidentRegisterService>(relaxed = true)

            val controller = ResidentRegisterController(service, registeredSession)
            controller.rename(displayName) shouldBe RpcResult.Ok(Unit)

            verify(exactly = 1) { service.rename(residentId, displayName) }
        }

        test("rename on Unregistered → Err(Unauthorized)") {
            val service = mockk<ResidentRegisterService>(relaxed = true)

            val controller = ResidentRegisterController(service, unregisteredSession)
            val result = controller.rename(displayName)

            (result as RpcResult.Err).error shouldBe RpcError.Unauthorized(reason = "registration required")
        }
    })
