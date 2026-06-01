package net.brightroom.mindstock.domain.model.catalog.content

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CatalogItemNameTest {
    @Test
    fun 前後の空白をトリムして受理する() {
        CatalogItemName("  トイレットペーパー  ").invoke() shouldBe "トイレットペーパー"
    }

    @Test
    fun 空白のみは拒否する() {
        shouldThrow<IllegalArgumentException> { CatalogItemName("  ") }
    }

    @Test
    fun 最大長を超える名前は拒否する() {
        shouldThrow<IllegalArgumentException> { CatalogItemName("あ".repeat(61)) }
    }
}
