package net.brightroom.mindstock.rpc.stock

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface StockRpcService {
    /** 商品単位の変動履歴(UC17)。商品は productId で既知なので StockMovements を直接返す。 */
    suspend fun history(productId: ProductId): RpcResult<StockMovements, RpcError>

    /** 世帯全体の活動履歴(UC24)。商品を添えた ActivityFeed を返す。 */
    suspend fun activity(householdId: HouseholdId): RpcResult<ActivityFeed, RpcError>
}
