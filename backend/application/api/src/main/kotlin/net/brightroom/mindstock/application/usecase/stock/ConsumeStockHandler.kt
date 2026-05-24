package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.stock.StockRegisterRepository

class ConsumeStockHandler(
    private val stockRegisterRepository: StockRegisterRepository,
) {
    fun handle(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Consumption = stockRegisterRepository.consume(product, quantity, occurredAt, by, note)
}
