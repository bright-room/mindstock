package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.quantity.NetQuantity
import kotlin.test.Test

class ArchivabilityTest {
    @Test
    fun 在庫ゼロはアーカイブ可能() {
        Archivability.of(NetQuantity(0)).archivable shouldBe true
    }

    @Test
    fun 在庫が残っていればアーカイブ不可() {
        Archivability.of(NetQuantity(1)).archivable shouldBe false
    }
}
