package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class ProductService(
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
    private val householdRepository: HouseholdRepository,
) {
    /** 在庫一覧(数量+status を見せるため Stock 集合)。 */
    fun list(
        householdId: HouseholdId,
        actor: ResidentId,
    ): Stocks {
        householdRepository.findById(householdId).requireMember(actor)
        return stockRepository.listByHousehold(householdId)
    }

    fun listArchived(
        householdId: HouseholdId,
        actor: ResidentId,
    ): Products {
        householdRepository.findById(householdId).requireMember(actor)
        return productRepository.listArchivedByHousehold(householdId)
    }

    /** 買い物リスト(自動=在庫不足 + 手動希望)。Stock 集合 × 手動希望 を read-model に合成する。 */
    fun shoppingList(
        householdId: HouseholdId,
        actor: ResidentId,
    ): ShoppingList {
        householdRepository.findById(householdId).requireMember(actor)
        val stocks = stockRepository.listByHousehold(householdId)
        val wantedIds =
            productRepository
                .listWanted(householdId)
                .list
                .map { it.id }
                .toSet()
        return ShoppingList(stocks.list.map { ShoppingEntry(it, it.product.id in wantedIds) })
    }
}
