package net.brightroom.mindstock.domain.model.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SearchLimitTest {
    @Test
    fun `1 と 100 は許容`() {
        SearchLimit(1)() shouldBe 1
        SearchLimit(100)() shouldBe 100
    }

    @Test
    fun `0 以下は IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> { SearchLimit(0) }
        shouldThrow<IllegalArgumentException> { SearchLimit(-1) }
    }

    @Test
    fun `100 超は IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> { SearchLimit(101) }
    }
}
