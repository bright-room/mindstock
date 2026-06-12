package net.brightroom.mindstock.application.service.resident

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile

class ResidentServiceTest :
    FunSpec({
        val repository = mockk<ResidentRepository>()
        val service = ResidentService(repository)

        test("me は repository.findById の結果を返す") {
            val id = ResidentId.create()
            val resident = Resident(id, ResidentProfile(DisplayName("じぶん")))
            every { repository.findById(id) } returns resident
            service.me(id) shouldBe resident
        }
    })
