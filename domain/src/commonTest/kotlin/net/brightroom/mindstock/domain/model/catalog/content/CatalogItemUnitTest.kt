package net.brightroom.mindstock.domain.model.catalog.content

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CatalogItemUnitTest {
    @Test
    fun trims_and_accepts() {
        CatalogItemUnit("  ロール  ").invoke() shouldBe "ロール"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { CatalogItemUnit("  ") }
    }

    @Test
    fun rejects_over_10_chars() {
        shouldThrow<IllegalArgumentException> { CatalogItemUnit("あ".repeat(11)) }
    }
}
