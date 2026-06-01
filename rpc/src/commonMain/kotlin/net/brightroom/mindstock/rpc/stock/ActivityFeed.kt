package net.brightroom.mindstock.rpc.stock

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement

/**
 * 世帯全体の活動履歴(UC24)の射影。
 * `StockMovement` は商品参照を持たないため、世帯横断フィードでは各 movement に
 * 商品(`Product`)を添えて「誰が・何の商品を・いくつ」を描けるようにする。
 */
@Serializable
data class ActivityEntry(
    val product: Product,
    val movement: StockMovement,
)

@Serializable
data class ActivityFeed(
    val list: List<ActivityEntry>,
) {
    fun size(): Int = list.size
}
