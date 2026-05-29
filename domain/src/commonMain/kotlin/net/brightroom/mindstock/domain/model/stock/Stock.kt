package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

/**
 * 在庫状態。
 *
 * 1 つの Product に対する全 movement (補充・消費) から現在数量・買い物リスト要否を計算する。
 * 訂正は別概念ではなく、単に逆方向の movement を 1 件追加することで表現する。
 */
@Serializable
data class Stock(
    val product: Product,
    val movements: StockMovements,
) {
    fun currentQuantity(): Int = movements.netQuantity()

    fun needsReplenishment(): Boolean = product.minimumStock.isBelow(currentQuantity())

    fun shortage(): Int = product.minimumStock.shortage(currentQuantity())
}
