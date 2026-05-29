package net.brightroom.mindstock.infrastructure.datasource.product

import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.UserId
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ProductRegisterDataSource : ProductRegisterRepository {
    override fun adopt(
        household: Household,
        catalogItem: CatalogItem,
    ): Product {
        val newId =
            ProductsTable.insert {
                it[household_id] = household.id()
                it[catalog_item_id] = catalogItem.id()
            } get ProductsTable.id

        return hydrateProduct(
            id = newId,
            catalogItem = catalogItem,
            minimumStock = null,
            archived = false,
        )
    }

    override fun setMinimumStock(
        product: Product,
        value: MinimumStock.Set,
        editedBy: UserId,
    ) {
        ProductMinimumStocksTable.insert {
            it[product_id] = product.id()
            it[minimum_stock] = value()
            it[edited_by] = editedBy()
        }
    }

    override fun archive(
        product: Product,
        by: UserId,
    ) {
        ProductArchivesTable.insert {
            it[product_id] = product.id()
            it[archived_by] = by()
        }
    }
}
