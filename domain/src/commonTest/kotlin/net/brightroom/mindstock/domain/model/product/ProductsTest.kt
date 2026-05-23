package net.brightroom.mindstock.domain.model.product

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ProductsTest {
    private fun catalogItem(name: String) =
        CatalogItem(
            id = CatalogItemId(Uuid.generateV7()),
            name = CatalogItemName(name),
            unit = CatalogItemUnit("個"),
        )

    private fun product(
        name: String,
        archived: Boolean = false,
    ) = Product(
        id = ProductId(Uuid.generateV7()),
        catalogItem = catalogItem(name),
        minimumStock = null,
        archived = archived,
    )

    @Test
    fun `activeOnly excludes archived products`() {
        val active = product("a", archived = false)
        val archived = product("b", archived = true)
        val products = Products(listOf(active, archived))
        products.activeOnly().asList() shouldBe listOf(active)
    }

    @Test
    fun `activeOnly returns empty when all archived`() {
        val a = product("a", archived = true)
        val products = Products(listOf(a))
        products.activeOnly().size shouldBe 0
    }
}
