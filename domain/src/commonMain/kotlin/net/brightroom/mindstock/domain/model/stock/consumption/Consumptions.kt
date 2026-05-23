package net.brightroom.mindstock.domain.model.stock.consumption

/**
 * 消費イベントの集合。
 */
class Consumptions(private val list: List<Consumption>) {
    fun asList(): List<Consumption> = list.toList()

    val size: Int get() = list.size
}
