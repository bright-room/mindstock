package net.brightroom.mindstock.infrastructure.datasource.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.domain.repository.product.ProductRepository
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.hydrateCatalogItem
import net.brightroom.mindstock.infrastructure.datasource.schemas.catalog.CatalogItemRevisionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.catalog.CatalogItemsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductArchivesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductMinimumStocksTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductsTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class ProductRepositoryImpl : ProductRepository {
    private data class LatestRevsAlias(
        val alias: QueryAlias,
        val catalogItemId: Column<Uuid>,
        val maxId: ExpressionWithColumnType<Long?>,
    )

    private data class LatestMinStocksAlias(
        val alias: QueryAlias,
        val productId: Column<Uuid>,
        val maxId: ExpressionWithColumnType<Long?>,
    )

    private data class LatestArchivesAlias(
        val alias: QueryAlias,
        val productId: Column<Uuid>,
        val maxId: ExpressionWithColumnType<Long?>,
    )

    private fun buildLatestRevs(): LatestRevsAlias {
        val maxRevIdAlias = CatalogItemRevisionsTable.id.max().alias("max_rev_id")
        val alias =
            CatalogItemRevisionsTable
                .select(CatalogItemRevisionsTable.catalog_item_id, maxRevIdAlias)
                .groupBy(CatalogItemRevisionsTable.catalog_item_id)
                .alias("latest_revs")
        return LatestRevsAlias(alias, alias[CatalogItemRevisionsTable.catalog_item_id], alias[maxRevIdAlias])
    }

    private fun buildLatestMinStocks(): LatestMinStocksAlias {
        val maxMinStockIdAlias = ProductMinimumStocksTable.id.max().alias("max_min_stock_id")
        val alias =
            ProductMinimumStocksTable
                .select(ProductMinimumStocksTable.product_id, maxMinStockIdAlias)
                .groupBy(ProductMinimumStocksTable.product_id)
                .alias("latest_min_stocks")
        return LatestMinStocksAlias(alias, alias[ProductMinimumStocksTable.product_id], alias[maxMinStockIdAlias])
    }

    private fun buildLatestArchives(): LatestArchivesAlias {
        val maxArchiveIdAlias = ProductArchivesTable.id.max().alias("max_archive_id")
        val alias =
            ProductArchivesTable
                .select(ProductArchivesTable.product_id, maxArchiveIdAlias)
                .groupBy(ProductArchivesTable.product_id)
                .alias("latest_archives")
        return LatestArchivesAlias(alias, alias[ProductArchivesTable.product_id], alias[maxArchiveIdAlias])
    }

    private fun buildJoinedQuery() =
        run {
            val revs = buildLatestRevs()
            val minStocks = buildLatestMinStocks()
            val archives = buildLatestArchives()

            ProductsTable
                .join(CatalogItemsTable, JoinType.INNER, onColumn = ProductsTable.catalog_item_id, otherColumn = CatalogItemsTable.id)
                .join(revs.alias, JoinType.INNER, onColumn = CatalogItemsTable.id, otherColumn = revs.catalogItemId)
                .join(CatalogItemRevisionsTable, JoinType.INNER) {
                    (CatalogItemRevisionsTable.catalog_item_id eq revs.catalogItemId) and
                        (CatalogItemRevisionsTable.id eq revs.maxId)
                }.join(minStocks.alias, JoinType.LEFT, onColumn = ProductsTable.id, otherColumn = minStocks.productId)
                .join(ProductMinimumStocksTable, JoinType.LEFT) {
                    (ProductMinimumStocksTable.product_id eq minStocks.productId) and
                        (ProductMinimumStocksTable.id eq minStocks.maxId)
                }.join(archives.alias, JoinType.LEFT, onColumn = ProductsTable.id, otherColumn = archives.productId)
                .join(ProductArchivesTable, JoinType.LEFT) {
                    (ProductArchivesTable.product_id eq archives.productId) and
                        (ProductArchivesTable.id eq archives.maxId)
                }
        }

    override fun listOf(household: Household): Products {
        val results =
            buildJoinedQuery()
                .selectAll()
                .where { ProductsTable.household_id eq household.id() }
                .map { it.toProduct() }
        return Products(results)
    }

    override fun find(
        household: Household,
        catalogItem: CatalogItem,
    ): Product? =
        buildJoinedQuery()
            .selectAll()
            .where {
                (ProductsTable.household_id eq household.id()) and
                    (ProductsTable.catalog_item_id eq catalogItem.id())
            }.singleOrNull()
            ?.toProduct()

    override fun findById(id: ProductId): Product? =
        buildJoinedQuery()
            .selectAll()
            .where { ProductsTable.id eq id() }
            .singleOrNull()
            ?.toProduct()

    private fun ResultRow.toProduct(): Product =
        hydrateProduct(
            id = this[ProductsTable.id],
            catalogItem =
                hydrateCatalogItem(
                    id = this[CatalogItemsTable.id],
                    name = this[CatalogItemRevisionsTable.name],
                    unit = this[CatalogItemRevisionsTable.unit],
                ),
            minimumStock = this.getOrNull(ProductMinimumStocksTable.minimum_stock),
            archived = this.getOrNull(ProductArchivesTable.id) != null,
        )
}
