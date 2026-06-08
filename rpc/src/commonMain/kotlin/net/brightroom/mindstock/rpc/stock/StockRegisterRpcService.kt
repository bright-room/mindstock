package net.brightroom.mindstock.rpc.stock

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface StockRegisterRpcService {
    /** 在庫を補充する(UC14)。Stock は productId で特定。actor は session 由来、occurredAt はクライアント指定(バックデート可)。 */
    suspend fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
        occurredAt: OccurredAt,
    ): RpcResult<Unit, RpcError>

    /** 在庫を消費する(UC15)。occurredAt はクライアント指定(バックデート可)。 */
    suspend fun consume(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
        occurredAt: OccurredAt,
    ): RpcResult<Unit, RpcError>

    /** 記録を訂正する(UC21。append-only。対象 movement を打ち消す訂正 movement を追記)。 */
    suspend fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
    ): RpcResult<Unit, RpcError>
}
