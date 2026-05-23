package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.StockConsumption
import net.brightroom.mindstock.domain.model.stock.StockConsumptionCorrection
import net.brightroom.mindstock.domain.model.stock.StockConsumptionId
import net.brightroom.mindstock.domain.model.stock.StockReplenishment
import net.brightroom.mindstock.domain.model.stock.StockReplenishmentCorrection
import net.brightroom.mindstock.domain.model.stock.StockReplenishmentId

public interface StockRepository {
    public fun findReplenishmentById(id: StockReplenishmentId): StockReplenishment?
    public fun findConsumptionById(id: StockConsumptionId): StockConsumption?

    public fun listReplenishmentsOf(productId: ProductId, limit: Int = 50): List<StockReplenishment>
    public fun listConsumptionsOf(productId: ProductId, limit: Int = 50): List<StockConsumption>

    public fun listCorrectionsOf(replenishmentId: StockReplenishmentId): List<StockReplenishmentCorrection>
    public fun listCorrectionsOf(consumptionId: StockConsumptionId): List<StockConsumptionCorrection>
}
