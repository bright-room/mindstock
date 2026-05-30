package net.brightroom.mindstock.infrastructure.datasource.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemRevisionsTable
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemsTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.upperCase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CatalogItemDataSource(
    private val database: Database,
) : CatalogItemRepository {
    private data class LatestRevs(
        val alias: QueryAlias,
        val catalogItemId: Column<Uuid>,
        val maxId: ExpressionWithColumnType<Long?>,
    )

    private fun buildLatestRevs(): LatestRevs {
        val maxRevIdAlias = CatalogItemRevisionsTable.id.max().alias("max_rev_id")
        val alias =
            CatalogItemRevisionsTable
                .select(CatalogItemRevisionsTable.catalog_item_id, maxRevIdAlias)
                .groupBy(CatalogItemRevisionsTable.catalog_item_id)
                .alias("latest_revs")
        return LatestRevs(alias, alias[CatalogItemRevisionsTable.catalog_item_id], alias[maxRevIdAlias])
    }

    override suspend fun search(
        query: String,
        limit: Int,
    ): CatalogItems = newSuspendedTransaction(db = database) { querySearch(query, limit) }

    private fun querySearch(
        query: String,
        limit: Int,
    ): CatalogItems {
        require(limit >= 0) { "limit must be >= 0" }

        val latestRevs = buildLatestRevs()

        val items =
            CatalogItemsTable
                .join(latestRevs.alias, JoinType.INNER, onColumn = CatalogItemsTable.id, otherColumn = latestRevs.catalogItemId)
                .join(CatalogItemRevisionsTable, JoinType.INNER) {
                    (CatalogItemRevisionsTable.catalog_item_id eq latestRevs.catalogItemId) and
                        (CatalogItemRevisionsTable.id eq latestRevs.maxId)
                }.selectAll()
                .where { CatalogItemRevisionsTable.name.upperCase() like "%${query.uppercase()}%" }
                .orderBy(CatalogItemRevisionsTable.name, SortOrder.ASC)
                .limit(limit)
                .map { row ->
                    hydrateCatalogItem(
                        id = row[CatalogItemsTable.id],
                        name = row[CatalogItemRevisionsTable.name],
                        unit = row[CatalogItemRevisionsTable.unit],
                    )
                }

        return CatalogItems(items)
    }

    override suspend fun findById(id: CatalogItemId): CatalogItem = newSuspendedTransaction(db = database) { queryById(id) }

    private fun queryById(id: CatalogItemId): CatalogItem {
        val latestRevs = buildLatestRevs()

        return CatalogItemsTable
            .join(latestRevs.alias, JoinType.INNER, onColumn = CatalogItemsTable.id, otherColumn = latestRevs.catalogItemId)
            .join(CatalogItemRevisionsTable, JoinType.INNER) {
                (CatalogItemRevisionsTable.catalog_item_id eq latestRevs.catalogItemId) and
                    (CatalogItemRevisionsTable.id eq latestRevs.maxId)
            }.selectAll()
            .where { CatalogItemsTable.id eq id() }
            .singleOrNull()
            ?.let { row ->
                hydrateCatalogItem(
                    id = row[CatalogItemsTable.id],
                    name = row[CatalogItemRevisionsTable.name],
                    unit = row[CatalogItemRevisionsTable.unit],
                )
            } ?: throw ResourceNotFoundException("catalog item not found: $id")
    }
}
