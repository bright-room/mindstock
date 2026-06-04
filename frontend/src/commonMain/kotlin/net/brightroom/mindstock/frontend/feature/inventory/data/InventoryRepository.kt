package net.brightroom.mindstock.frontend.feature.inventory.data

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService

/**
 * 在庫まわりの RPC を隠蔽。サービスは「開く関数」を遅延注入(認証後にトークン付きで open される）。
 */
class InventoryRepository(
    private val productService: () -> ProductRpcService,
    private val stockRegisterService: () -> StockRegisterRpcService,
) {
    suspend fun list(householdId: HouseholdId): RpcOutcome<Stocks> = productService().list(householdId).toOutcome()

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
}
