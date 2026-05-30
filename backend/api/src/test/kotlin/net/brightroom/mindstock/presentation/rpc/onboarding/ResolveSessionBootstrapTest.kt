package net.brightroom.mindstock.presentation.rpc.onboarding

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.SessionBootstrap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ResolveSessionBootstrapTest :
    FunSpec({

        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1"))
        val userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001"))

        // userId フィールドは handshake 時の値で、bootstrap は使わない(identity で引き直す)。
        fun session() =
            MindstockSession(
                identity = identity,
                userId = userId,
                exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                callId = Uuid.random(),
            )

        test("identity の User が存在しなければ Unregistered を返す") {
            val userService = mockk<UserService>()
            val householdService = mockk<HouseholdService>()

            every { userService.findByIdentity(identity) } throws ResourceNotFoundException("user not found")

            resolveSessionBootstrap(session(), userService, householdService) shouldBe
                SessionBootstrap.Unregistered
        }

        test("identity の User が存在すれば Registered(displayName / householdId / householdName)を返す") {
            val householdId = HouseholdId(Uuid.parse("00000000-0000-0000-0000-0000000000aa"))
            val userService = mockk<UserService>()
            val householdService = mockk<HouseholdService>()

            every { userService.findByIdentity(identity) } returns Profile(userId, DisplayName("Alice"))
            every { householdService.findOf(userId) } returns
                Household(householdId, HouseholdName("Aliceの家"), HouseholdMembers(emptyList()))

            resolveSessionBootstrap(session(), userService, householdService) shouldBe
                SessionBootstrap.Registered(
                    displayName = DisplayName("Alice"),
                    householdId = householdId,
                    householdName = HouseholdName("Aliceの家"),
                )
        }
    })
