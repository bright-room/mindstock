package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ArchivabilityTest {
    @Test
    fun zero_stock_is_archivable() {
        Archivability.of(0).archivable shouldBe true
    }

    @Test
    fun nonzero_stock_is_not_archivable() {
        Archivability.of(1).archivable shouldBe false
    }
}
