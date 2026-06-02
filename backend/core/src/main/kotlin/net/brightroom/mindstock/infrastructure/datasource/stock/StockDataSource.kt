@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
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

    override fun listByHousehold(householdId: HouseholdId): Stocks =
        transaction(database) {
            val products = productDataSource.listByHousehold(householdId).list
            // NOTE(P5): per-product loadMovements で 2N+1 クエリ。世帯の商品数が多いと効く。
            // P5 で stock_movements を product_id IN (...) で一括取得し product ごとに group + actor も全体一括、に最適化する
            // (実 DB + 結合テストで grouping を検証してから。P4 は機能的に正しい現状で確定)。
            Stocks(products.map { p -> Stock(p, loadMovements(p.id)) })
        }

    override fun historyOf(productId: ProductId): StockMovements = transaction(database) { loadMovements(productId) }

    /** product の movement 全件を occurred 順(= id 昇順)で返す。actor はバッチ解決。 */
    private fun loadMovements(productId: ProductId): StockMovements {
        val rows =
            StockMovementsTable
                .selectAll()
                .where { StockMovementsTable.productId eq productId() }
                .orderBy(StockMovementsTable.id to SortOrder.ASC)
                .toList()
        if (rows.isEmpty()) return StockMovements(emptyList())

        // actor をバッチ解決(actor_resident_id IN (...) × 最新 display_name)
        val actorIds = rows.map { it[StockMovementsTable.actorResidentId] }.toSet()
        val (dnSub, dnRefs) = latestResidentDisplayNames()
        val actors: Map<Uuid, Resident> =
            ResidentsTable
                .join(dnSub, JoinType.INNER, onColumn = ResidentsTable.id, otherColumn = dnSub[ResidentDisplayNamesTable.residentId])
                .selectAll()
                .where { (ResidentsTable.id inList actorIds) and (dnSub[dnRefs.rn] eq 1L) }
                .associate { row ->
                    val rid = row[ResidentsTable.id]
                    rid to Resident(ResidentId(rid), Profile(DisplayName(row[dnSub[ResidentDisplayNamesTable.displayName]])))
                }

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
}
