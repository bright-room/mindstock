package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.collections.shouldContainExactly
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import kotlin.test.Test

private fun stockNamed(name: String): Stock =
    Stock(
        product = Product.custom(ProductName(name), Barcode.Unlinked, ProductUnit("個"), MinimumStock(1)),
        movements = StockMovements(emptyList()),
    )

class InventoryUiStateTest {
    private val milk = stockNamed("牛乳")
    private val eggs = stockNamed("たまご")
    private val content = InventoryUiState.Content(Stocks(listOf(milk, eggs)), StockView.List)

    @Test
    fun empty_query_returns_all() {
        content.copy(query = "  ").visibleStocks().list shouldContainExactly listOf(milk, eggs)
    }

    @Test
    fun query_filters_by_name_substring_case_insensitive() {
        content.copy(query = "牛").visibleStocks().list shouldContainExactly listOf(milk)
    }

    @Test
    fun non_matching_query_returns_empty() {
        content.copy(query = "存在しない").visibleStocks().list shouldContainExactly emptyList()
    }
}
