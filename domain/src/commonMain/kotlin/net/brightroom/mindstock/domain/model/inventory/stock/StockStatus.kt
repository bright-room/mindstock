package net.brightroom.mindstock.domain.model.inventory.stock

import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock

enum class StockStatus {
    在庫切れ,
    残りわずか,
    十分,
    ;

    companion object {
        fun of(
            current: Int,
            minimum: MinimumStock,
        ): StockStatus =
            when {
                current <= 0 -> 在庫切れ
                minimum.isBelow(current) -> 残りわずか
                else -> 十分
            }
    }
}
