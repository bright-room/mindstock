package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
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
    private val householdRepository: HouseholdRepository,
    private val catalogItemRepository: CatalogItemRepository,
    private val productRepository: ProductRepository,
    private val session: MindstockSession,
    private val database: Database,
) : ProductRpcService {
    override suspend fun listOfHousehold(householdId: HouseholdId): RpcResult<Products, RpcError> =
        tx(database, session) {
            val household =
                householdRepository.findById(householdId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            RpcResult.Ok(productService.listOf(household))
        }

    override suspend fun find(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product?, RpcError> =
        tx(database, session) {
            val household =
                householdRepository.findById(householdId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val catalogItem =
                catalogItemRepository.findById(catalogItemId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "catalog item", id = "$catalogItemId"))
            RpcResult.Ok(productService.find(household, catalogItem))
        }

    override suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product, RpcError> =
        tx(database, session) {
            val household =
                householdRepository.findById(householdId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            val catalogItem =
                catalogItemRepository.findById(catalogItemId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "catalog item", id = "$catalogItemId"))
            RpcResult.Ok(productRegisterService.adopt(household, catalogItem))
        }

    override suspend fun setMinimumStock(
        id: ProductId,
        minimumStock: MinimumStock.Set,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val product =
                productRepository.findById(id)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$id"))
            productRegisterService.setMinimumStock(product, minimumStock, requireNotNull(session.userId))
            RpcResult.Ok(Unit)
        }

    override suspend fun archive(id: ProductId): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val product =
                productRepository.findById(id)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$id"))
            productRegisterService.archive(product, requireNotNull(session.userId))
            RpcResult.Ok(Unit)
        }
}
