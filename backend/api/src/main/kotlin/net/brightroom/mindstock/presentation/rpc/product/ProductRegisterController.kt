package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.application.scenario.product.AdoptProductScenario
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.rpc.product.AddCustomProductRequest
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class ProductRegisterController(
    private val productRegisterService: ProductRegisterService,
    private val adoptProductScenario: AdoptProductScenario,
    private val session: MindstockSession,
) : ProductRegisterRpcService {
    override suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): RpcResult<Product, RpcError> =
        guarded(session) {
            RpcResult.Ok(adoptProductScenario.run(householdId, catalogItemId, unit, minimumStock, session.requireResidentId()))
        }

    override suspend fun addCustom(
        householdId: HouseholdId,
        request: AddCustomProductRequest,
    ): RpcResult<Product, RpcError> =
        guarded(session) {
            RpcResult.Ok(
                productRegisterService.addCustom(
                    householdId,
                    request.name,
                    request.barcode,
                    request.unit,
                    request.minimumStock,
                    session.requireResidentId(),
                ),
            )
        }

    override suspend fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.changeUnit(productId, unit, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun changeImage(
        productId: ProductId,
        image: ProductImage,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.changeImage(productId, image, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.changeMinimum(productId, minimumStock, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun archive(productId: ProductId): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.archive(productId, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun unarchive(productId: ProductId): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.unarchive(productId, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.setWanted(productId, wanted, session.requireResidentId())
            RpcResult.Ok(Unit)
        }
}
