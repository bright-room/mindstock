@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import net.brightroom.mindstock.infrastructure.datasource.resident.latestResidentDisplayNames
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.StockMovementsTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class StockDataSource(
    private val database: Database,
    private val productDataSource: ProductRepository,
) : StockRepository {
    override fun findByProduct(productId: ProductId): Stock =
        transaction(database) {
            val product = productDataSource.findById(productId)
            Stock(product, loadMovements(productId))
        }

    override fun findByMovement(movementId: MovementId): Stock =
        transaction(database) {
            val productUuid =
                StockMovementsTable
                    .select(StockMovementsTable.productId)
                    .where { StockMovementsTable.id eq movementId() }
                    .firstOrNull()
                    ?.get(StockMovementsTable.productId)
                    ?: throw ResourceNotFoundException("movement not found: $movementId")
            val productId = ProductId(productUuid)
            Stock(productDataSource.findById(productId), loadMovements(productId))
        }

    override fun listByHousehold(householdId: HouseholdId): Stocks =
        transaction(database) {
            val products = productDataSource.listByHousehold(householdId).list
            val movementsByProduct = loadMovementsByProducts(products.map { it.id })
            Stocks(products.map { p -> Stock(p, movementsByProduct[p.id] ?: StockMovements(emptyList())) })
        }

    override fun historyOf(productId: ProductId): StockMovements = transaction(database) { loadMovements(productId) }

    /** product の movement 全件を occurred 順(= id 昇順)で返す。actor はバッチ解決。 */
    private fun loadMovements(productId: ProductId): StockMovements {
        val rows =
            StockMovementsTable
                .selectAll()
                .where { StockMovementsTable.productId eq productId() }
                // id 昇順 = 追記順。netQuantity の訂正畳み込みは順序非依存だが履歴表示は追記順で安定。
                .orderBy(StockMovementsTable.id to SortOrder.ASC)
                .toList()
        if (rows.isEmpty()) return StockMovements(emptyList())

        val actors = resolveActors(rows.map { it[StockMovementsTable.actorResidentId] }.toSet())
        val movements =
            rows.map { row ->
                val residentId = row[StockMovementsTable.actorResidentId]
                val actor =
                    actors[residentId]
                        ?: throw ResourceNotFoundException("display name not found for resident: $residentId")
                row.toStockMovement(actor)
            }
        return StockMovements(movements)
    }

    /**
     * 複数 product の movement を一括取得する。product_id IN (...) で 1 クエリ発行し、
     * product ごとに groupBy して返す。movement の無い product はキーに現れない。
     */
    private fun loadMovementsByProducts(productIds: List<ProductId>): Map<ProductId, StockMovements> {
        if (productIds.isEmpty()) return emptyMap()

        val rows =
            StockMovementsTable
                .selectAll()
                .where { StockMovementsTable.productId inList productIds.map { it() } }
                // product_id ASC, id ASC = product ごとに追記順を保つ
                .orderBy(StockMovementsTable.productId to SortOrder.ASC, StockMovementsTable.id to SortOrder.ASC)
                .toList()
        if (rows.isEmpty()) return emptyMap()

        val actors = resolveActors(rows.map { it[StockMovementsTable.actorResidentId] }.toSet())
        return rows
            .groupBy { ProductId(it[StockMovementsTable.productId]) }
            .mapValues { (_, productRows) ->
                StockMovements(
                    productRows.map { row ->
                        val residentId = row[StockMovementsTable.actorResidentId]
                        val actor =
                            actors[residentId]
                                ?: throw ResourceNotFoundException("display name not found for resident: $residentId")
                        row.toStockMovement(actor)
                    },
                )
            }
    }

    /**
     * actor_resident_id のセットから Resident を一括解決する(最新 display_name JOIN)。
     * 空セットは早期リターンで空 Map を返す。
     */
    private fun resolveActors(actorIds: Set<Uuid>): Map<Uuid, Resident> {
        if (actorIds.isEmpty()) return emptyMap()

        val (dnSub, dnRefs) = latestResidentDisplayNames()
        return ResidentsTable
            .join(dnSub, JoinType.INNER, onColumn = ResidentsTable.id, otherColumn = dnSub[ResidentDisplayNamesTable.residentId])
            .selectAll()
            .where { (ResidentsTable.id inList actorIds) and (dnSub[dnRefs.rn] eq 1L) }
            .associate { row ->
                val rid = row[ResidentsTable.id]
                rid to Resident(ResidentId(rid), ResidentProfile(DisplayName(row[dnSub[ResidentDisplayNamesTable.displayName]])))
            }
    }
}
