package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.rpc.ProductRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class ProductController(
    private val productService: ProductService,
    private val productRegisterService: ProductRegisterService,
    private val householdService: HouseholdService,
    private val catalogItemService: CatalogItemService,
    private val session: MindstockSession,
    private val database: Database,
) : ProductRpcService {
    override suspend fun listOfHousehold(householdId: HouseholdId): RpcResult<Products, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            RpcResult.Ok(productService.listOf(household))
        }

    override suspend fun find(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            val catalogItem = catalogItemService.findById(catalogItemId)
            RpcResult.Ok(productService.find(household, catalogItem))
        }

    override suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            val catalogItem = catalogItemService.findById(catalogItemId)
            RpcResult.Ok(productRegisterService.adopt(household, catalogItem))
        }

    override suspend fun setMinimumStock(
        id: ProductId,
        minimumStock: MinimumStock.Set,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val product = productService.findById(id)
            productRegisterService.setMinimumStock(product, minimumStock, requireNotNull(session.userId))
            RpcResult.Ok(Unit)
        }

    override suspend fun archive(id: ProductId): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val product = productService.findById(id)
            productRegisterService.archive(product, requireNotNull(session.userId))
            RpcResult.Ok(Unit)
        }
}
