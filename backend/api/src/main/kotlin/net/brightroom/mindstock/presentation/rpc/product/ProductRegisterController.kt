@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.application.scenario.product.AdoptProductScenario
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.requireRegistered
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.rpc.product.AddCustomProductRequest
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.product.UploadImageRequest
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class ProductRegisterController(
    private val productRegisterService: ProductRegisterService,
    private val adoptProductScenario: AdoptProductScenario,
    private val session: MindstockSession,
) : ProductRegisterRpcService {
    private companion object {
        const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024

        // 8MiB を base64 で運ぶときの最大文字数(4/3 + padding 余裕)。
        const val MAX_BASE64_CHARS = (MAX_UPLOAD_BYTES / 3 + 1) * 4L
    }

    override suspend fun uploadImage(
        productId: ProductId,
        request: UploadImageRequest,
    ): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            // base64 は 4 文字 ≒ 3 バイト。decode で全量をメモリ確保する前に、文字列長から
            // 上限超過を弾く(巨大ペイロードの decode 自体を避ける)。境界値は decode 後に厳密判定する。
            if (request.base64.length.toLong() > MAX_BASE64_CHARS) {
                return@requireRegistered RpcResult.Err(
                    RpcError.BadRequest(field = "base64", reason = "image too large"),
                )
            }
            val raw =
                try {
                    kotlin.io.encoding.Base64
                        .decode(request.base64)
                } catch (e: IllegalArgumentException) {
                    return@requireRegistered RpcResult.Err(
                        RpcError.BadRequest(field = "base64", reason = e.message ?: "invalid base64"),
                    )
                }
            if (raw.size > MAX_UPLOAD_BYTES) {
                return@requireRegistered RpcResult.Err(
                    RpcError.BadRequest(field = "base64", reason = "image too large: ${raw.size} bytes"),
                )
            }
            productRegisterService.uploadImage(productId, RawImageUpload(raw), residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): RpcResult<Product, RpcError> =
        requireRegistered(session) { residentId ->
            RpcResult.Ok(adoptProductScenario.run(householdId, catalogItemId, unit, minimumStock, residentId))
        }

    override suspend fun addCustom(
        householdId: HouseholdId,
        request: AddCustomProductRequest,
    ): RpcResult<Product, RpcError> =
        requireRegistered(session) { residentId ->
            RpcResult.Ok(
                productRegisterService.addCustom(
                    householdId,
                    request.name,
                    request.barcode,
                    request.unit,
                    request.minimumStock,
                    residentId,
                ),
            )
        }

    override suspend fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
    ): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            productRegisterService.changeUnit(productId, unit, residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun removeImage(productId: ProductId): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            productRegisterService.removeImage(productId, residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            productRegisterService.changeMinimum(productId, minimumStock, residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun archive(productId: ProductId): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            productRegisterService.archive(productId, residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun unarchive(productId: ProductId): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            productRegisterService.unarchive(productId, residentId)
            RpcResult.Ok(Unit)
        }

    override suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            productRegisterService.setWanted(productId, wanted, residentId)
            RpcResult.Ok(Unit)
        }
}
