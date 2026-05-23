package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Reason
import net.brightroom.mindstock.domain.model.stock.consumption.Consumption
import net.brightroom.mindstock.domain.model.stock.consumption.ConsumptionCorrection
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishment
import net.brightroom.mindstock.domain.model.stock.replenishment.ReplenishmentCorrection
import net.brightroom.mindstock.domain.model.user.User

interface StockRegisterRepository {
    fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Replenishment

    fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Consumption

    fun correct(
        replenishment: Replenishment,
        correctedQuantity: Quantity,
        reason: Reason,
        by: User,
    ): ReplenishmentCorrection

    fun correct(
        consumption: Consumption,
        correctedQuantity: Quantity,
        reason: Reason,
        by: User,
    ): ConsumptionCorrection
}
