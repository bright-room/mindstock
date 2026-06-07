package net.brightroom.mindstock.frontend.feature.catalog.data

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.rpc.catalog.CatalogRpcService
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

private abstract class FakeCatalogRpc : CatalogRpcService {
    override suspend fun search(
        name: CatalogItemName,
        limit: Int,
    ): RpcResult<CatalogItems, RpcError> = error("unused")

    override suspend fun lookupByJan(jan: Jan): RpcResult<CatalogItem, RpcError> = error("unused")
}

private abstract class FakeArchiveProductRpc : ProductRpcService {
    override suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError> = error("unused")

    override suspend fun listArchived(householdId: HouseholdId): RpcResult<Products, RpcError> = error("unused")

    override suspend fun shoppingList(householdId: HouseholdId): RpcResult<ShoppingList, RpcError> = error("unused")
}

class CatalogRepositoryTest {
    @Test
    fun search_returns_success_outcome_on_ok() =
        runTest {
            val fake =
                object : FakeCatalogRpc() {
                    override suspend fun search(
                        name: CatalogItemName,
                        limit: Int,
                    ) = RpcResult.Ok(CatalogItems(emptyList()))
                }
            val repo =
                CatalogRepository(
                    catalogService = { fake },
                    productRegisterService = { error("unused") },
                    productService = { error("unused") },
                )
            repo.search(CatalogItemName("茶"), 20).shouldBeInstanceOf<RpcOutcome.Success<CatalogItems>>()
        }

    @Test
    fun list_archived_returns_success_outcome_on_ok() =
        runTest {
            val fake =
                object : FakeArchiveProductRpc() {
                    override suspend fun listArchived(householdId: HouseholdId) = RpcResult.Ok(Products(emptyList()))
                }
            val repo =
                CatalogRepository(
                    catalogService = { error("unused") },
                    productRegisterService = { error("unused") },
                    productService = { fake },
                )
            repo.listArchived(HouseholdId.create()).shouldBeInstanceOf<RpcOutcome.Success<Products>>()
        }
}
