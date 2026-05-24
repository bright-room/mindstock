package net.brightroom.mindstock.infrastructure.datasource.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductArchivesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductMinimumStocksTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class ProductRegisterRepositoryImpl(
    private val database: Database,
) : ProductRegisterRepository {
    override fun adopt(
        household: Household,
        catalogItem: CatalogItem,
    ): Product {
        val newId =
            ProductsTable.insert {
                it[household_id] = household.id().toJavaUuid()
                it[catalog_item_id] = catalogItem.id().toJavaUuid()
            } get ProductsTable.id

        return hydrateProduct(
            id = newId.toKotlinUuid(),
            catalogItem = catalogItem,
            minimumStock = null,
            archived = false,
        )
    }

    override fun setMinimumStock(
        product: Product,
        value: MinimumStock,
        editedBy: User,
    ) {
        ProductMinimumStocksTable.insert {
            it[product_id] = product.id().toJavaUuid()
            it[minimum_stock] = value()
            it[edited_by] = editedBy.id().toJavaUuid()
        }
    }

    override fun archive(
        product: Product,
        by: User,
    ) {
        ProductArchivesTable.insert {
            it[product_id] = product.id().toJavaUuid()
            it[archived_by] = by.id().toJavaUuid()
        }
    }
}
