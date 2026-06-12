package net.brightroom.mindstock.application.service.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile

class HouseholdRegisterServiceTest :
    FunSpec({
        val residentRepository = mockk<ResidentRepository>()
        val householdRepository = mockk<HouseholdRepository>()
        val householdRegisterRepository = mockk<HouseholdRegisterRepository>(relaxed = true)
        val service =
            HouseholdRegisterService(residentRepository, householdRepository, householdRegisterRepository)

        val ownerId = ResidentId.create()
        val owner = Resident(ownerId, ResidentProfile(DisplayName("ぬし")))
        val memberId = ResidentId.create()
        val member = Resident(memberId, ResidentProfile(DisplayName("ひと")))
        val householdId = HouseholdId.create()

        fun household(vararg pairs: Pair<Resident, HouseholdMemberRole>) =
            Household(
                householdId,
                HouseholdProfile(HouseholdName("わが家")),
                Members(pairs.map { (r, role) -> HouseholdMember(r, role) }),
            )

        beforeTest {
            clearMocks(residentRepository, householdRepository, householdRegisterRepository)
        }

        test("create は owner を解決して Household を登録し返す") {
            every { residentRepository.findById(ownerId) } returns owner
            val created = service.create(HouseholdName("新居"), ownerId)
            created.profile shouldBe HouseholdProfile(HouseholdName("新居"))
            verify { householdRegisterRepository.registerHousehold(created) }
        }

        test("rename は世帯主なら appendHouseholdName を呼ぶ") {
            every { householdRepository.findById(householdId) } returns household(owner to HouseholdMemberRole.世帯主)
            service.rename(householdId, HouseholdName("改名後"), ownerId)
            verify { householdRegisterRepository.appendHouseholdName(householdId, HouseholdName("改名後")) }
        }

        test("rename は非世帯主メンバーなら OwnerRequiredException で write しない") {
            every { householdRepository.findById(householdId) } returns
                household(owner to HouseholdMemberRole.世帯主, member to HouseholdMemberRole.メンバー)
            shouldThrow<OwnerRequiredException> { service.rename(householdId, HouseholdName("改名後"), memberId) }
            verify(exactly = 0) { householdRegisterRepository.appendHouseholdName(any(), any()) }
        }

        test("changeRole は非世帯主メンバーなら OwnerRequiredException で write しない") {
            every { householdRepository.findById(householdId) } returns
                household(owner to HouseholdMemberRole.世帯主, member to HouseholdMemberRole.メンバー)
            // target=owner(降格対象), actor=member(非世帯主が操作 → OwnerRequiredException)
            shouldThrow<OwnerRequiredException> {
                service.changeRole(householdId, ownerId, HouseholdMemberRole.メンバー, memberId)
            }
            verify(exactly = 0) { householdRegisterRepository.changeMemberRole(any(), any(), any()) }
        }

        test("leave は非メンバーなら ResourceNotFoundException で write しない") {
            every { householdRepository.findById(householdId) } returns household(owner to HouseholdMemberRole.世帯主)
            shouldThrow<ResourceNotFoundException> { service.leave(householdId, memberId) }
            verify(exactly = 0) { householdRegisterRepository.removeMember(any(), any()) }
        }

        test("removeMember は世帯主なら removeMember を呼ぶ") {
            every { householdRepository.findById(householdId) } returns
                household(owner to HouseholdMemberRole.世帯主, member to HouseholdMemberRole.メンバー)
            service.removeMember(householdId, memberId, ownerId)
            verify { householdRegisterRepository.removeMember(householdId, memberId) }
        }
    })
