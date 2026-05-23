package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.StockConsumption
import net.brightroom.mindstock.domain.model.stock.StockConsumptionCorrection
import net.brightroom.mindstock.domain.model.stock.StockConsumptionId
import net.brightroom.mindstock.domain.model.stock.StockReplenishment
import net.brightroom.mindstock.domain.model.stock.StockReplenishmentCorrection
import net.brightroom.mindstock.domain.model.stock.StockReplenishmentId

interface StockRepository {
    fun findReplenishmentById(id: StockReplenishmentId): StockReplenishment?

    fun findConsumptionById(id: StockConsumptionId): StockConsumption?

    fun listReplenishmentsOf(
        productId: ProductId,
        limit: Int = 50,
    ): List<StockReplenishment>

    fun listConsumptionsOf(
        productId: ProductId,
        limit: Int = 50,
    ): List<StockConsumption>

    fun listCorrectionsOf(replenishmentId: StockReplenishmentId): List<StockReplenishmentCorrection>

    fun listCorrectionsOf(consumptionId: StockConsumptionId): List<StockConsumptionCorrection>
}
