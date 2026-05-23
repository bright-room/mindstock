package net.brightroom.mindstock.domain.model.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CatalogItemIdTest {
    @Test
    fun `wraps a Uuid and stringifies to canonical form`() {
        val uuid = Uuid.parse("0190a8e9-7f3c-7c1e-b3e0-1a2b3c4d5e6f")
        val id = CatalogItemId(uuid)
        id.toString() shouldBe "0190a8e9-7f3c-7c1e-b3e0-1a2b3c4d5e6f"
    }

    @Test
    fun `equality follows underlying Uuid`() {
        val uuid = Uuid.parse("0190a8e9-7f3c-7c1e-b3e0-1a2b3c4d5e6f")
        CatalogItemId(uuid) shouldBe CatalogItemId(uuid)
        CatalogItemId(uuid) shouldNotBe CatalogItemId(Uuid.parse("0190a8ea-0000-7000-8000-000000000000"))
    }
}
