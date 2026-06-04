@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.resident

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class ResidentControllerTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val residentId = ResidentId.create()
        val session =
            MindstockSession.Registered(
                identity,
                residentId,
                Clock.System.now().plus(1.hours),
                Uuid.random(),
            )

        val residentService = mockk<ResidentService>()
        val controller = ResidentController(residentService, session)

        test("me は ResidentService.me(residentId) の結果を Ok で包んで返す") {
            val me = Resident(residentId, Profile(DisplayName("じぶん")))
            every { residentService.me(residentId) } returns me

            controller.me() shouldBe RpcResult.Ok(me)
        }
    })
