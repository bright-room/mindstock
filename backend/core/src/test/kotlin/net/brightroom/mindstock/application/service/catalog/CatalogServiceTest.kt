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
import net.brightroom.mindstock.application.repository.catalog.ExternalProductRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId

class CatalogServiceTest :
    FunSpec({
        val catalogRepository = mockk<CatalogRepository>()
        val catalogRegisterRepository = mockk<CatalogRegisterRepository>(relaxed = true)
        val externalProduct = mockk<ExternalProductRepository>()
        val service = CatalogService(catalogRepository, catalogRegisterRepository, externalProduct)
        val jan = Jan("4901234567894")

        beforeTest {
            clearMocks(catalogRepository, catalogRegisterRepository, externalProduct)
        }

        test("master にヒットしたら外部 API を呼ばない") {
            val item = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("お茶"))
            every { catalogRepository.findByJan(jan) } returns item
            service.lookupByJan(jan) shouldBe item
            verify(exactly = 0) { externalProduct.findByJan(jan) }
        }

        test("master 不在なら外部 API で取得し cache に保存して返す") {
            val received = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("コーヒー"))
            every { catalogRepository.findByJan(jan) } throws ResourceNotFoundException("not found")
            every { externalProduct.findByJan(jan) } returns received
            service.lookupByJan(jan) shouldBe received
            verify { catalogRegisterRepository.register(received) }
        }

        test("master にも外部 API にも無ければ ResourceNotFoundException を素通し") {
            every { catalogRepository.findByJan(jan) } throws ResourceNotFoundException("not found")
            every { externalProduct.findByJan(jan) } throws ResourceNotFoundException("external miss")
            shouldThrow<ResourceNotFoundException> { service.lookupByJan(jan) }
            verify(exactly = 0) { catalogRegisterRepository.register(any()) }
        }
    })
