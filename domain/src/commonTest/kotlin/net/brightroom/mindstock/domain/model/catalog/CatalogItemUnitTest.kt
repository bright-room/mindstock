package net.brightroom.mindstock.domain.model.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class CatalogItemUnitTest {
    @Test
    fun `accepts non-blank within 10 chars`() {
        CatalogItemUnit("個").toString() shouldBe "個"
        CatalogItemUnit("ml").toString() shouldBe "ml"
        CatalogItemUnit("x".repeat(10)).toString().length shouldBe 10
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<DomainException.CatalogItemUnitBlank> { CatalogItemUnit("") }
    }

    @Test
    fun `rejects over 10 chars`() {
        shouldThrow<DomainException.CatalogItemUnitTooLong> { CatalogItemUnit("x".repeat(11)) }
    }
}
