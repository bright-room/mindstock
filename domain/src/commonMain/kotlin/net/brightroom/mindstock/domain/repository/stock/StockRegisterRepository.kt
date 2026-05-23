package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Reason
import net.brightroom.mindstock.domain.model.stock.StockConsumptionId
import net.brightroom.mindstock.domain.model.stock.StockReplenishmentId
import net.brightroom.mindstock.domain.model.user.UserId

interface StockRegisterRepository {
    fun replenish(
        productId: ProductId,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actedBy: UserId,
        note: Note,
    ): StockReplenishmentId

    fun consume(
        productId: ProductId,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actedBy: UserId,
        note: Note,
    ): StockConsumptionId

    fun correct(
        replenishmentId: StockReplenishmentId,
        correctedQuantity: Quantity,
        reason: Reason,
        correctedBy: UserId,
    )

    fun correct(
        consumptionId: StockConsumptionId,
        correctedQuantity: Quantity,
        reason: Reason,
        correctedBy: UserId,
    )
}
