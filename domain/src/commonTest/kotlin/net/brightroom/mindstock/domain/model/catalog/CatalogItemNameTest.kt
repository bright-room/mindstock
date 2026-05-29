package net.brightroom.mindstock.domain.model.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CatalogItemNameTest {
    @Test
    fun `accepts non-blank within 200 chars`() {
        CatalogItemName("キレイキレイ").toString() shouldBe "キレイキレイ"
        CatalogItemName("x".repeat(200)).toString().length shouldBe 200
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<IllegalArgumentException> { CatalogItemName("") }
        shouldThrow<IllegalArgumentException> { CatalogItemName("  ") }
    }

    @Test
    fun `rejects over 200 chars`() {
        shouldThrow<IllegalArgumentException> { CatalogItemName("x".repeat(201)) }
    }
}
