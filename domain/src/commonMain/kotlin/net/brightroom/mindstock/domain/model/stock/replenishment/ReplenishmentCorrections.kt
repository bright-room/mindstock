package net.brightroom.mindstock.domain.model.stock.replenishment

/**
 * 単一 Replenishment への訂正の集合。
 */
class ReplenishmentCorrections(private val list: List<ReplenishmentCorrection>) {
    /** 訂正日時の最新を返す。なければ null。 */
    fun latest(): ReplenishmentCorrection? = list.maxByOrNull { it.correctedAt() }

    fun asList(): List<ReplenishmentCorrection> = list.toList()
}
