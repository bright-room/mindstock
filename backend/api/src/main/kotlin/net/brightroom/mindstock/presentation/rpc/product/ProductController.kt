package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.requireRegistered
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
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
        requireRegistered(session) { residentId -> RpcResult.Ok(productService.list(householdId, residentId)) }

    override suspend fun listArchived(householdId: HouseholdId): RpcResult<Products, RpcError> =
        requireRegistered(session) { residentId -> RpcResult.Ok(productService.listArchived(householdId, residentId)) }

    override suspend fun shoppingList(householdId: HouseholdId): RpcResult<ShoppingList, RpcError> =
        requireRegistered(session) { residentId -> RpcResult.Ok(productService.shoppingList(householdId, residentId)) }

    override suspend fun imageUrl(productId: ProductId): RpcResult<ImageUrl, RpcError> =
        requireRegistered(session) { _ -> RpcResult.Ok(productService.imageUrl(productId)) }
}
