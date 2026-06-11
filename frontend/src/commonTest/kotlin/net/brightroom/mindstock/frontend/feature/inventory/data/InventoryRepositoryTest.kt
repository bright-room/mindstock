package net.brightroom.mindstock.frontend.feature.inventory.data

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

private abstract class FakeProductRpc : ProductRpcService {
    override suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError> = error("unused")

    override suspend fun listArchived(householdId: HouseholdId): RpcResult<Products, RpcError> = error("unused")

    override suspend fun shoppingList(householdId: HouseholdId): RpcResult<ShoppingList, RpcError> = error("unused")

    override suspend fun imageUrl(productId: ProductId): RpcResult<ImageUrl, RpcError> = error("unused")
}

class InventoryRepositoryTest {
    @Test
    fun list_returns_success_outcome_on_ok() =
        runTest {
            val fakeProduct =
                object : FakeProductRpc() {
                    override suspend fun list(householdId: HouseholdId) = RpcResult.Ok(Stocks(emptyList()))
                }
            val repo =
                InventoryRepository(
                    productService = { fakeProduct },
                    stockService = { error("unused") },
                    stockRegisterService = { error("unused") },
                    productRegisterService = { error("unused") },
                )
            val out = repo.list(HouseholdId.create())
            out.shouldBeInstanceOf<RpcOutcome.Success<Stocks>>()
        }

    @Test
    fun shopping_list_returns_success_outcome_on_ok() =
        runTest {
            val fakeProduct =
                object : FakeProductRpc() {
                    override suspend fun shoppingList(householdId: HouseholdId) = RpcResult.Ok(ShoppingList(emptyList()))
                }
            val repo =
                InventoryRepository(
                    productService = { fakeProduct },
                    stockService = { error("unused") },
                    stockRegisterService = { error("unused") },
                    productRegisterService = { error("unused") },
                )
            val out = repo.shoppingList(HouseholdId.create())
            out.shouldBeInstanceOf<RpcOutcome.Success<ShoppingList>>()
        }
}
