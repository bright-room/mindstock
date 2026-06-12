package net.brightroom.mindstock.application.service.resident

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.resident.ResidentRegisterRepository
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile

class ResidentRegisterServiceTest :
    FunSpec({
        val repository = mockk<ResidentRegisterRepository>(relaxed = true)
        val service = ResidentRegisterService(repository)

        test("register は採番済み Resident を返す") {
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
            val displayName = DisplayName("しんき")
            val registered = Resident(ResidentId.create(), ResidentProfile(displayName))
            every { repository.registerResident(identity, displayName) } returns registered
            service.register(identity, displayName) shouldBe registered
        }

        test("rename は appendDisplayName へ委譲する") {
            val id = ResidentId.create()
            val displayName = DisplayName("あたらしい名")
            service.rename(id, displayName)
            verify { repository.appendDisplayName(id, displayName) }
        }
    })
