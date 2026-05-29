package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.Stocks
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.infrastructure.datasource.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
import net.brightroom.mindstock.infrastructure.datasource.user.latestDisplayNames
import net.brightroom.mindstock.infrastructure.datasource.user.toProfile
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
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

    override fun stocksOf(household: Household): Stocks {
        val products = productRepository.listOf(household).list
        if (products.isEmpty()) return Stocks(emptyList())
        val byProductId = loadMovementsFor(products)
        return Stocks(
            products.map { p ->
                Stock(p, StockMovements(byProductId[p.id()] ?: emptyList()))
            },
        )
    }

    override fun movementHistory(
        product: Product,
        limit: Int,
    ): StockMovements {
        require(limit > 0) { "limit must be > 0" }
        val latest = latestDisplayNames()

        val rows =
            StockMovementsTable
                .join(UsersTable, JoinType.INNER, onColumn = StockMovementsTable.acted_by, otherColumn = UsersTable.id)
                .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
                .join(UserDisplayNamesTable, JoinType.INNER) {
                    (UserDisplayNamesTable.user_id eq latest.userId) and
                        (UserDisplayNamesTable.id eq latest.maxId)
                }.selectAll()
                .where { StockMovementsTable.product_id eq product.id() }
                .orderBy(
                    StockMovementsTable.occurred_at to SortOrder.DESC,
                    StockMovementsTable.id to SortOrder.DESC,
                ).limit(limit)
                .map { row ->
                    toStockMovement(
                        actor = row.toProfile(),
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

        val latest = latestDisplayNames()

        val pairs =
            StockMovementsTable
                .join(UsersTable, JoinType.INNER, onColumn = StockMovementsTable.acted_by, otherColumn = UsersTable.id)
                .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
                .join(UserDisplayNamesTable, JoinType.INNER) {
                    (UserDisplayNamesTable.user_id eq latest.userId) and
                        (UserDisplayNamesTable.id eq latest.maxId)
                }.selectAll()
                .where { StockMovementsTable.product_id inList productUuids }
                .orderBy(
                    StockMovementsTable.occurred_at to SortOrder.ASC,
                    StockMovementsTable.id to SortOrder.ASC,
                ).map { row ->
                    val productUuid = row[StockMovementsTable.product_id]
                    productUuid to
                        toStockMovement(
                            actor = row.toProfile(),
                            type = row[StockMovementsTable.type],
                            quantity = row[StockMovementsTable.quantity],
                            occurredAt = row[StockMovementsTable.occurred_at].toInstant().toKotlinInstant(),
                            note = row[StockMovementsTable.note],
                        )
                }
        return pairs.groupBy({ it.first }, { it.second })
    }
}
