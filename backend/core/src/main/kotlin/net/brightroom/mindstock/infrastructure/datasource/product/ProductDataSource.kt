@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.product

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductBarcodesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductRevisionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductWantedEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductsTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class ProductDataSource(
    private val database: Database,
) : ProductRepository {
    override fun findById(id: ProductId): Product =
        transaction(database) {
            productRows { _ -> (ProductsTable.id eq id()) }
                .firstOrNull()
                ?: throw ResourceNotFoundException("product not found: $id")
        }

    override fun listByHousehold(householdId: HouseholdId): Products =
        transaction(database) {
            Products(
                productRows { rev ->
                    (ProductsTable.householdId eq householdId()) and
                        (rev[ProductRevisionsTable.status] eq ProductStatus.採用中)
                },
            )
        }

    override fun listArchivedByHousehold(householdId: HouseholdId): Products =
        transaction(database) {
            Products(
                productRows { rev ->
                    (ProductsTable.householdId eq householdId()) and
                        (rev[ProductRevisionsTable.status] eq ProductStatus.アーカイブ済)
                },
            )
        }

    override fun existsByJan(
        householdId: HouseholdId,
        jan: Jan,
    ): Boolean =
        transaction(database) {
            ProductsTable
                .join(
                    ProductBarcodesTable,
                    JoinType.INNER,
                    onColumn = ProductsTable.id,
                    otherColumn = ProductBarcodesTable.productId,
                ).selectAll()
                .where { (ProductsTable.householdId eq householdId()) and (ProductBarcodesTable.jan eq jan()) }
                .empty()
                .not()
        }

    override fun listWanted(householdId: HouseholdId): Products =
        transaction(database) {
            val wantedIds = latestWantedProductIds(householdId)
            if (wantedIds.isEmpty()) {
                Products(emptyList())
            } else {
                Products(
                    productRows { _ ->
                        (ProductsTable.householdId eq householdId()) and (ProductsTable.id inList wantedIds)
                    },
                )
            }
        }

    /** 世帯内で最新 wanted イベントが true の product_id 集合。 */
    private fun latestWantedProductIds(householdId: HouseholdId): Set<Uuid> {
        val rn =
            rowNumber()
                .over()
                .partitionBy(ProductWantedEventsTable.productId)
                .orderBy(ProductWantedEventsTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("wrn")
        val sub =
            ProductWantedEventsTable
                .select(ProductWantedEventsTable.productId, ProductWantedEventsTable.wanted, rnAlias)
                .alias("latest_wanted")
        return ProductsTable
            .join(sub, JoinType.INNER, onColumn = ProductsTable.id, otherColumn = sub[ProductWantedEventsTable.productId])
            .selectAll()
            .where {
                (ProductsTable.householdId eq householdId()) and
                    (sub[rnAlias] eq 1L) and
                    (sub[ProductWantedEventsTable.wanted] eq true)
            }.map { it[ProductsTable.id] }
            .toSet()
    }

    /**
     * products × 最新 product_revisions を join し、where で絞って Product 群を返す。
     * 最新 revision の alias を predicate に渡す(alias 列で status 等を比較するため)。
     */
    private fun productRows(where: (QueryAlias) -> Op<Boolean>): List<Product> {
        val rn =
            rowNumber()
                .over()
                .partitionBy(ProductRevisionsTable.productId)
                .orderBy(ProductRevisionsTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val revSub =
            ProductRevisionsTable
                .select(
                    ProductRevisionsTable.productId,
                    ProductRevisionsTable.unit,
                    ProductRevisionsTable.minimumStock,
                    ProductRevisionsTable.imageRef,
                    ProductRevisionsTable.status,
                    rnAlias,
                ).alias("latest_revision")

        return ProductsTable
            .join(revSub, JoinType.INNER, onColumn = ProductsTable.id, otherColumn = revSub[ProductRevisionsTable.productId])
            .join(ProductBarcodesTable, JoinType.LEFT, onColumn = ProductsTable.id, otherColumn = ProductBarcodesTable.productId)
            .selectAll()
            .where { (revSub[rnAlias] eq 1L) and where(revSub) }
            .map { row -> row.toProduct(revSub) }
    }
}
