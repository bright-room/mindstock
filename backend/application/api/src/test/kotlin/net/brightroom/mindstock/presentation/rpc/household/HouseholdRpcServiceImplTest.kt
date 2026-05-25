package net.brightroom.mindstock.presentation.rpc.household

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.brightroom.mindstock.application.usecase.household.CreateHouseholdHandler
import net.brightroom.mindstock.application.usecase.household.FindHouseholdOfUserHandler
import net.brightroom.mindstock.application.usecase.household.InviteMemberHandler
import net.brightroom.mindstock.application.usecase.household.RevokeMembershipHandler
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.repository.user.UserRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HouseholdRpcServiceImplTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("findOf resolves actor and delegates to FindHouseholdOfUserHandler") {
            val findHandler = mockk<FindHouseholdOfUserHandler>()
            val createHandler = mockk<CreateHouseholdHandler>()
            val inviteHandler = mockk<InviteMemberHandler>()
            val revokeHandler = mockk<RevokeMembershipHandler>()
            val householdRepository = mockk<HouseholdRepository>()
            val userRepository = mockk<UserRepository>()
            val call = mockk<ApplicationCall>()
            val user =
                User(
                    id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
                    displayName = DisplayName("Alice"),
                )
            val household =
                Household(
                    id = HouseholdId(Uuid.parse("00000000-0000-0000-0000-000000000002")),
                    members = HouseholdMembers(emptyList()),
                )

            mockkStatic(ApplicationCall::actor)
            every { call.actor(userRepository) } returns user
            every { findHandler.handle(user) } returns household

            val impl =
                HouseholdRpcServiceImpl(
                    findHouseholdOfUser = findHandler,
                    createHousehold = createHandler,
                    inviteMember = inviteHandler,
                    revokeMembership = revokeHandler,
                    householdRepository = householdRepository,
                    userRepository = userRepository,
                    call = call,
                )
            impl.findOf() shouldBe household
        }
    })
