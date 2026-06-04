package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class ProductController(
    private val productService: ProductService,
    private val session: MindstockSession,
) : ProductRpcService {
    override suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError> =
        guarded(session) { RpcResult.Ok(productService.list(householdId, session.requireResidentId())) }

    override suspend fun listArchived(householdId: HouseholdId): RpcResult<Products, RpcError> =
        guarded(session) { RpcResult.Ok(productService.listArchived(householdId, session.requireResidentId())) }

    override suspend fun shoppingList(householdId: HouseholdId): RpcResult<ShoppingList, RpcError> =
        guarded(session) { RpcResult.Ok(productService.shoppingList(householdId, session.requireResidentId())) }
}
