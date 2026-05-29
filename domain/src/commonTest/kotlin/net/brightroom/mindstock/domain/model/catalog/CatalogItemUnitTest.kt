package net.brightroom.mindstock.domain.model.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
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
        shouldThrow<IllegalArgumentException> { CatalogItemUnit("") }
        shouldThrow<IllegalArgumentException> { CatalogItemUnit("   ") }
    }

    @Test
    fun `rejects over 10 chars`() {
        shouldThrow<IllegalArgumentException> { CatalogItemUnit("x".repeat(11)) }
    }
}
