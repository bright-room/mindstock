package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.infrastructure.datasource.stock.StockMovementsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.toKotlinInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StockDataSource(
    private val productRepository: ProductRepository,
) : StockRepository {
    override fun stockOf(product: Product): Stock {
        val movements = loadMovementsFor(listOf(product))[product.id()] ?: emptyList()
        return Stock(product, StockMovements(movements))
    }

    override fun stocksOf(household: Household): List<Stock> {
        val products = productRepository.listOf(household).asList()
        if (products.isEmpty()) return emptyList()
        val byProductId = loadMovementsFor(products)
        return products.map { p ->
            Stock(p, StockMovements(byProductId[p.id()] ?: emptyList()))
        }
    }

    override fun movementHistory(
        product: Product,
        limit: Int,
    ): StockMovements {
        require(limit > 0) { "limit must be > 0" }
        val rows =
            StockMovementsTable
                .selectAll()
                .where { StockMovementsTable.product_id eq product.id() }
                .orderBy(
                    StockMovementsTable.occurred_at to SortOrder.DESC,
                    StockMovementsTable.id to SortOrder.DESC,
                ).limit(limit)
                .map { row ->
                    toStockMovement(
                        product = product,
                        actorId = row[StockMovementsTable.acted_by],
                        type = row[StockMovementsTable.type],
                        quantity = row[StockMovementsTable.quantity],
                        occurredAt = row[StockMovementsTable.occurred_at].toInstant().toKotlinInstant(),
                        note = row[StockMovementsTable.note],
                    )
                }
        return StockMovements(rows)
    }

    /** product 群に対する全 movement を 1 クエリで取得し、productId UUID ごとにグルーピング。 */
    private fun loadMovementsFor(products: List<Product>): Map<Uuid, List<StockMovement>> {
        if (products.isEmpty()) return emptyMap()
        val productUuids = products.map { it.id() }
        val productByUuid = products.associateBy { it.id() }

        val pairs =
            StockMovementsTable
                .selectAll()
                .where { StockMovementsTable.product_id inList productUuids }
                .orderBy(
                    StockMovementsTable.occurred_at to SortOrder.ASC,
                    StockMovementsTable.id to SortOrder.ASC,
                ).map { row ->
                    val productUuid = row[StockMovementsTable.product_id]
                    val product =
                        productByUuid[productUuid] ?: error("product not found for movement: $productUuid")
                    productUuid to
                        toStockMovement(
                            product = product,
                            actorId = row[StockMovementsTable.acted_by],
                            type = row[StockMovementsTable.type],
                            quantity = row[StockMovementsTable.quantity],
                            occurredAt = row[StockMovementsTable.occurred_at].toInstant().toKotlinInstant(),
                            note = row[StockMovementsTable.note],
                        )
                }
        return pairs.groupBy({ it.first }, { it.second })
    }
}
