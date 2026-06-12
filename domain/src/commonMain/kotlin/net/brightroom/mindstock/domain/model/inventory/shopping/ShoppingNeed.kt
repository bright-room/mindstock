package net.brightroom.mindstock.domain.model.inventory.shopping

import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus

enum class ShoppingNeed(
    val onShoppingList: Boolean,
) {
    在庫不足(true),
    手動希望(true),
    不要(false),
    ;

    fun is在庫不足(): Boolean = this == 在庫不足

    fun is手動希望(): Boolean = this == 手動希望

    companion object {
        fun judge(
            status: StockStatus,
            manuallyWanted: Wanted,
        ): ShoppingNeed =
            when {
                status != StockStatus.十分 -> 在庫不足
                manuallyWanted() -> 手動希望
                else -> 不要
            }
    }
}
