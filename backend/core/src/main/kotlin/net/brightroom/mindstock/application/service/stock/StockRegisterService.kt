package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.UserId

class StockRegisterService(
    private val stockRegisterRepository: StockRegisterRepository,
) {
    fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    ) = stockRegisterRepository.replenish(product, quantity, occurredAt, by, note)

    fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    ) = stockRegisterRepository.consume(product, quantity, occurredAt, by, note)
}
