@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.infrastructure.datasource.schemas.CatalogItemsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class CatalogDataSource(
    private val database: Database,
) : CatalogRepository {
    override fun search(
        query: String,
        limit: Int,
    ): CatalogItems =
        transaction(database) {
            val items =
                CatalogItemsTable
                    .selectAll()
                    .where { CatalogItemsTable.name like "%$query%" }
                    .limit(limit)
                    .map { it.toCatalogItem() }
            CatalogItems(items)
        }

    override fun findByJan(jan: Jan): CatalogItem =
        transaction(database) {
            CatalogItemsTable
                .selectAll()
                .where { CatalogItemsTable.jan eq jan() }
                .limit(1)
                .firstOrNull()
                ?.toCatalogItem()
                ?: throw ResourceNotFoundException("catalog item not found for jan: $jan")
        }

    override fun findById(id: CatalogItemId): CatalogItem =
        transaction(database) {
            CatalogItemsTable
                .selectAll()
                .where { CatalogItemsTable.id eq id() }
                .limit(1)
                .firstOrNull()
                ?.toCatalogItem()
                ?: throw ResourceNotFoundException("catalog item not found: $id")
        }
}
