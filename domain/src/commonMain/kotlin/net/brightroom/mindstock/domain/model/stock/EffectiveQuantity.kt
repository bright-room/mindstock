package net.brightroom.mindstock.domain.model.stock

/**
 * 補充/消費イベントに訂正を適用した「実効数量」。
 *
 * 訂正があれば最新の correctedQuantity、なければ元の quantity。
 */
class EffectiveQuantity(
    private val originalQuantity: Quantity,
    private val latestCorrectedQuantity: Quantity?,
) {
    fun value(): Int = (latestCorrectedQuantity ?: originalQuantity)()
}
