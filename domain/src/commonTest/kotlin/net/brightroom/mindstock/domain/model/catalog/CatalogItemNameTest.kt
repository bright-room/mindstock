package net.brightroom.mindstock.domain.model.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class CatalogItemNameTest {
    @Test
    fun `accepts non-blank within 200 chars`() {
        CatalogItemName("キレイキレイ").toString() shouldBe "キレイキレイ"
        CatalogItemName("x".repeat(200)).toString().length shouldBe 200
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<DomainException.CatalogItemNameBlank> { CatalogItemName("") }
        shouldThrow<DomainException.CatalogItemNameBlank> { CatalogItemName("  ") }
    }

    @Test
    fun `rejects over 200 chars`() {
        shouldThrow<DomainException.CatalogItemNameTooLong> { CatalogItemName("x".repeat(201)) }
    }
}
