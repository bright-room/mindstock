package net.brightroom.mindstock.domain.model.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

/**
 * 在庫状態。
 *
 * 1 つの Product に対する全 movement (補充・消費) から現在数量・買い物リスト要否を計算する。
 * 訂正は別概念ではなく、単に逆方向の movement を 1 件追加することで表現する。
 */
class Stock(
    val product: Product,
    val movements: StockMovements,
) {
    fun currentQuantity(): Int = movements.netQuantity()

    fun needsReplenishment(): Boolean {
        val minimum = product.minimumStock?.let { it() } ?: return false
        return currentQuantity() < minimum
    }

    fun shortage(): Int {
        val minimum = product.minimumStock?.let { it() } ?: 0
        return (minimum - currentQuantity()).coerceAtLeast(0)
    }
}
