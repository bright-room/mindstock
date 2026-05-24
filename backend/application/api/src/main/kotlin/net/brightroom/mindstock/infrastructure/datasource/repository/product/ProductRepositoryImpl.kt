package net.brightroom.mindstock.infrastructure.datasource.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.domain.repository.product.ProductRepository
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.hydrateCatalogItem
import org.jetbrains.exposed.v1.core.UUIDColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class ProductRepositoryImpl(
    private val database: Database,
) : ProductRepository {
    private val baseSql =
        """
        SELECT p.id            AS p_id,
               ci.id           AS ci_id,
               r.name          AS ci_name,
               r.unit          AS ci_unit,
               m.minimum_stock AS min_stock,
               (a.id IS NOT NULL) AS archived
        FROM products p
        INNER JOIN catalog_items ci ON ci.id = p.catalog_item_id
        INNER JOIN (
            SELECT DISTINCT ON (catalog_item_id) catalog_item_id, name, unit, id
            FROM catalog_item_revisions
            ORDER BY catalog_item_id, id DESC
        ) r ON r.catalog_item_id = ci.id
        LEFT JOIN (
            SELECT DISTINCT ON (product_id) product_id, minimum_stock, id
            FROM product_minimum_stocks
            ORDER BY product_id, id DESC
        ) m ON m.product_id = p.id
        LEFT JOIN (
            SELECT DISTINCT ON (product_id) product_id, id
            FROM product_archives
            ORDER BY product_id, id DESC
        ) a ON a.product_id = p.id
        """.trimIndent()

    override fun listOf(household: Household): Products {
        val sql = "$baseSql\nWHERE p.household_id = ?"
        val results = mutableListOf<Product>()
        TransactionManager.current().exec(
            sql,
            args = listOf(UUIDColumnType() to household.id().toJavaUuid()),
        ) { rs ->
            while (rs.next()) {
                results.add(buildProduct(rs))
            }
        }
        return Products(results)
    }

    override fun find(
        household: Household,
        catalogItem: CatalogItem,
    ): Product? {
        val sql = "$baseSql\nWHERE p.household_id = ? AND p.catalog_item_id = ?"
        var result: Product? = null
        TransactionManager.current().exec(
            sql,
            args =
                listOf(
                    UUIDColumnType() to household.id().toJavaUuid(),
                    UUIDColumnType() to catalogItem.id().toJavaUuid(),
                ),
        ) { rs ->
            if (rs.next()) {
                result = buildProduct(rs)
            }
        }
        return result
    }

    private fun buildProduct(rs: java.sql.ResultSet): Product {
        val minStockObj = rs.getObject("min_stock")
        return hydrateProduct(
            id = rs.getObject("p_id", UUID::class.java).toKotlinUuid(),
            catalogItem =
                hydrateCatalogItem(
                    id = rs.getObject("ci_id", UUID::class.java).toKotlinUuid(),
                    name = rs.getString("ci_name"),
                    unit = rs.getString("ci_unit"),
                ),
            minimumStock = (minStockObj as? Number)?.toInt(),
            archived = rs.getBoolean("archived"),
        )
    }
}
