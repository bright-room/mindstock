package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService

class StockRegisterController(
    private val stockRegisterService: StockRegisterService,
    private val session: MindstockSession,
) : StockRegisterRpcService {
    override suspend fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            stockRegisterService.replenish(productId, quantity, note, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun consume(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            stockRegisterService.consume(productId, quantity, note, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            stockRegisterService.correct(target, correctedQuantity, reason, session.requireResidentId())
            RpcResult.Ok(Unit)
        }
}
