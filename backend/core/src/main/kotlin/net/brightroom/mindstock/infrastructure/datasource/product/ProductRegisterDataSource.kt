@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.product

import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.infrastructure.datasource.Created
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductBarcodesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductCatalogLinksTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductRevisionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductWantedEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ProductRegisterDataSource(
    private val database: Database,
) : ProductRegisterRepository {
    override fun registerAdopted(
        product: Product,
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ) {
        transaction(database) {
            insertProductAndRevision(product, householdId)
            ProductCatalogLinksTable.insert {
                it[productId] = product.id()
                it[ProductCatalogLinksTable.catalogItemId] = catalogItemId()
            }
        }
    }

    override fun registerCustom(
        product: Product,
        householdId: HouseholdId,
    ) {
        transaction(database) {
            insertProductAndRevision(product, householdId)
        }
    }

    override fun appendRevision(product: Product) {
        transaction(database) {
            val createdTime = Created.now()
            ProductRevisionsTable.insert {
                it[productId] = product.id()
                it[unit] = product.setting.unit()
                it[minimumStock] = product.setting.minimumStock()
                it[imageRef] = product.image.toImageRefColumn()
                it[status] = product.status
                it[recordedAt] = createdTime()
            }
        }
    }

    override fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ) {
        transaction(database) {
            val createdTime = Created.now()
            ProductWantedEventsTable.insert {
                it[ProductWantedEventsTable.productId] = productId()
                it[ProductWantedEventsTable.wanted] = wanted
                it[recordedAt] = createdTime()
            }
        }
    }

    private fun insertProductAndRevision(
        product: Product,
        householdId: HouseholdId,
    ) {
        val createdTime = Created.now()
        ProductsTable.insert {
            it[id] = product.id()
            it[ProductsTable.householdId] = householdId()
            it[name] = product.name()
            it[createdAt] = createdTime()
        }
        val janValue = product.barcode.toJanColumn()
        if (janValue != null) {
            ProductBarcodesTable.insert {
                it[productId] = product.id()
                it[jan] = janValue
            }
        }
        ProductRevisionsTable.insert {
            it[productId] = product.id()
            it[unit] = product.setting.unit()
            it[minimumStock] = product.setting.minimumStock()
            it[imageRef] = product.image.toImageRefColumn()
            it[status] = product.status
            it[recordedAt] = createdTime()
        }
    }
}
