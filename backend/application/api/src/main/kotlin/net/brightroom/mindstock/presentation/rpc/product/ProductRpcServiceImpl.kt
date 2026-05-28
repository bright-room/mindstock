package net.brightroom.mindstock.presentation.rpc.product

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.error.NotFoundException
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.presentation.rpc.ProductRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class ProductRpcServiceImpl(
    private val productService: ProductService,
    private val productRegisterService: ProductRegisterService,
    private val householdRepository: HouseholdRepository,
    private val catalogItemRepository: CatalogItemRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
    private val database: Database,
) : ProductRpcService {
    // Memoized for the lifetime of this per-connection Service Impl.
    // NOTE: a rename within the same connection won't refresh this cache until reconnect.
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun listOfHousehold(householdId: HouseholdId): Products =
        tx(database) {
            // TODO(authz): verify actor is a member of household $householdId
            actor
            val household =
                householdRepository.findById(householdId)
                    ?: throw NotFoundException("household not found: $householdId")
            productService.listOf(household)
        }

    override suspend fun find(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): Product? =
        tx(database) {
            // TODO(authz): verify actor is a member of household $householdId
            actor
            val household =
                householdRepository.findById(householdId)
                    ?: throw NotFoundException("household not found: $householdId")
            val catalogItem =
                catalogItemRepository.findById(catalogItemId)
                    ?: throw NotFoundException("catalog item not found: $catalogItemId")
            productService.find(household, catalogItem)
        }

    override suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): Product =
        tx(database) {
            // TODO(authz): verify actor is a member of household $householdId
            actor
            val household =
                householdRepository.findById(householdId)
                    ?: throw NotFoundException("household not found: $householdId")
            val catalogItem =
                catalogItemRepository.findById(catalogItemId)
                    ?: throw NotFoundException("catalog item not found: $catalogItemId")
            productRegisterService.adopt(household, catalogItem)
        }

    override suspend fun setMinimumStock(
        id: ProductId,
        minimumStock: MinimumStock,
    ) = tx(database) {
        // TODO(authz): verify actor can modify product $id (member of its household)
        val product =
            productRepository.findById(id)
                ?: throw NotFoundException("product not found: $id")
        productRegisterService.setMinimumStock(product, minimumStock, actor)
    }

    override suspend fun archive(id: ProductId) =
        tx(database) {
            // TODO(authz): verify actor can modify product $id (member of its household)
            val product =
                productRepository.findById(id)
                    ?: throw NotFoundException("product not found: $id")
            productRegisterService.archive(product, actor)
        }
}
