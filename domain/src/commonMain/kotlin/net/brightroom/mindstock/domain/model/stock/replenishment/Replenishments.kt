package net.brightroom.mindstock.domain.model.stock.replenishment

/**
 * 補充イベントの集合。1 つの Product に紐付く履歴等で使う。
 */
class Replenishments(private val list: List<Replenishment>) {
    fun asList(): List<Replenishment> = list.toList()

    val size: Int get() = list.size
}
