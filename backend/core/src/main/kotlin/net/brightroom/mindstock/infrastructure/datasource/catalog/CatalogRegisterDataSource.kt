@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.infrastructure.datasource.Created
import net.brightroom.mindstock.infrastructure.datasource.schemas.CatalogItemsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class CatalogRegisterDataSource(
    private val database: Database,
) : CatalogRegisterRepository {
    override fun register(catalogItem: CatalogItem) {
        transaction(database) {
            val createdTime = Created.now()
            CatalogItemsTable.insert {
                it[id] = catalogItem.id()
                it[jan] = catalogItem.jan()
                it[name] = catalogItem.name()
                it[createdAt] = createdTime()
            }
        }
    }
}
