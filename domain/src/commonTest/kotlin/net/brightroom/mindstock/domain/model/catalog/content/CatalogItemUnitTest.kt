package net.brightroom.mindstock.domain.model.catalog.content

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CatalogItemUnitTest {
    @Test
    fun 前後の空白をトリムして受理する() {
        CatalogItemUnit("  ロール  ").invoke() shouldBe "ロール"
    }

    @Test
    fun 空白のみは拒否する() {
        shouldThrow<IllegalArgumentException> { CatalogItemUnit("  ") }
    }

    @Test
    fun 最大長を超える単位名は拒否する() {
        shouldThrow<IllegalArgumentException> { CatalogItemUnit("あ".repeat(11)) }
    }
}
