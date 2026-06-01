package net.brightroom.mindstock.domain.model.inventory.stock

enum class Archivability(
    val archivable: Boolean,
) {
    可能(true),
    在庫あり(false),
    ;

    companion object {
        fun of(currentQuantity: Int): Archivability = if (currentQuantity == 0) 可能 else 在庫あり
    }
}
