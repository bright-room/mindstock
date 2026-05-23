package net.brightroom.mindstock.domain.model.stock.movement

/**
 * 在庫変動の種別。
 *
 * - [REPLENISHMENT]: 在庫を増やす事実(補充)
 * - [CONSUMPTION]: 在庫を減らす事実(消費)
 *
 * 「補充の誤りを訂正する」操作は別 type ではなく、単に逆方向の movement を 1 件追加することで表現する。
 */
enum class StockMovementType {
    REPLENISHMENT,
    CONSUMPTION,
}
