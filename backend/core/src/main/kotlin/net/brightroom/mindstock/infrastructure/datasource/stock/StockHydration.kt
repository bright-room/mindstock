package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun toStockMovement(
    product: Product,
    actorId: Uuid,
    type: StockMovementType,
    quantity: Int,
    occurredAt: Instant,
    note: String,
): StockMovement {
    val q = Quantity(quantity)
    val occurred = OccurredAt(occurredAt)
    val n = Note(note)
    // actor は id のみ正、subject / displayName は暫定。Plan 6 までに JOIN 拡張する想定。
    val actor =
        User(
            id = UserId(actorId),
            authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("(unknown)")),
            displayName = DisplayName("(unknown)"),
        )
    return when (type) {
        StockMovementType.REPLENISHMENT -> Replenishment(product, q, occurred, actor, n)
        StockMovementType.CONSUMPTION -> Consumption(product, q, occurred, actor, n)
    }
}
