package net.brightroom.mindstock.application.service.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.catalog.CatalogRegisterRepository
import net.brightroom.mindstock.application.repository.catalog.CatalogRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.infrastructure.gateway.ExternalProductGateway

class CatalogServiceTest :
    FunSpec({
        val catalogRepository = mockk<CatalogRepository>()
        val catalogRegisterRepository = mockk<CatalogRegisterRepository>(relaxed = true)
        val gateway = mockk<ExternalProductGateway>()
        val service = CatalogService(catalogRepository, catalogRegisterRepository, gateway)
        val jan = Jan("4901234567894")

        beforeTest {
            clearMocks(catalogRepository, catalogRegisterRepository, gateway)
        }

        test("master にヒットしたら外部 API を呼ばない") {
            val item = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("お茶"))
            every { catalogRepository.findByJan(jan) } returns item
            service.lookupByJan(jan) shouldBe item
            verify(exactly = 0) { gateway.fetch(jan) }
        }

        test("master 不在なら外部 API で取得し cache に保存して返す") {
            val fetched = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("コーヒー"))
            every { catalogRepository.findByJan(jan) } throws ResourceNotFoundException("not found")
            every { gateway.fetch(jan) } returns fetched
            service.lookupByJan(jan) shouldBe fetched
            verify { catalogRegisterRepository.register(fetched) }
        }

        test("master にも外部 API にも無ければ ResourceNotFoundException を素通し") {
            every { catalogRepository.findByJan(jan) } throws ResourceNotFoundException("not found")
            every { gateway.fetch(jan) } throws ResourceNotFoundException("external miss")
            shouldThrow<ResourceNotFoundException> { service.lookupByJan(jan) }
            verify(exactly = 0) { catalogRegisterRepository.register(any()) }
        }
    })
