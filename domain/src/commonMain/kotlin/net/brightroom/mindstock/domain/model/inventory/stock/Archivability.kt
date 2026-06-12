package net.brightroom.mindstock.domain.model.inventory.stock

import net.brightroom.mindstock.domain.model.inventory.quantity.NetQuantity

enum class Archivability(
    val archivable: Boolean,
) {
    可能(true),
    在庫あり(false),
    ;

    companion object {
        fun of(currentQuantity: NetQuantity): Archivability = if (currentQuantity() == 0) 可能 else 在庫あり
    }
}
