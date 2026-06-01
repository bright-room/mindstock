package net.brightroom.mindstock.domain.model.catalog.content

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CatalogItemNameTest {
    @Test
    fun trims_and_accepts() {
        CatalogItemName("  トイレットペーパー  ").invoke() shouldBe "トイレットペーパー"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { CatalogItemName("  ") }
    }

    @Test
    fun rejects_over_60_chars() {
        shouldThrow<IllegalArgumentException> { CatalogItemName("あ".repeat(61)) }
    }
}
