package net.brightroom.mindstock.frontend.feature.inventory.data

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService
import net.brightroom.mindstock.rpc.stock.StockRpcService

/**
 * 在庫まわりの RPC を隠蔽。サービスは「開く関数」を遅延注入（認証後にトークン付きで open される）。
 */
class InventoryRepository(
    private val productService: () -> ProductRpcService,
    private val stockService: () -> StockRpcService,
    private val stockRegisterService: () -> StockRegisterRpcService,
    private val productRegisterService: () -> ProductRegisterRpcService,
) {
    suspend fun list(householdId: HouseholdId): RpcOutcome<Stocks> = productService().list(householdId).toOutcome()

    suspend fun shoppingList(householdId: HouseholdId): RpcOutcome<ShoppingList> = productService().shoppingList(householdId).toOutcome()

    suspend fun activity(householdId: HouseholdId): RpcOutcome<ActivityFeed> = stockService().activity(householdId).toOutcome()

    suspend fun history(productId: ProductId): RpcOutcome<StockMovements> = stockService().history(productId).toOutcome()

    suspend fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ): RpcOutcome<Unit> = stockRegisterService().replenish(productId, quantity, note).toOutcome()

    suspend fun consume(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ): RpcOutcome<Unit> = stockRegisterService().consume(productId, quantity, note).toOutcome()

    suspend fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
    ): RpcOutcome<Unit> = stockRegisterService().correct(target, correctedQuantity, reason).toOutcome()

    suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ): RpcOutcome<Unit> = productRegisterService().setWanted(productId, wanted).toOutcome()
}
