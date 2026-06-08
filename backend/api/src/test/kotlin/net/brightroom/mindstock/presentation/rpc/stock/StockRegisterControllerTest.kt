@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.stock

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDateTime
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class StockRegisterControllerTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val residentId = ResidentId.create()
        val session =
            MindstockSession.Registered(
                identity,
                residentId,
                Clock.System.now().plus(1.hours),
                Uuid.random(),
            )

        val stockRegisterService = mockk<StockRegisterService>(relaxed = true)
        val controller = StockRegisterController(stockRegisterService, session)

        test("replenish は StockRegisterService.replenish を occurredAt 付きで呼び Ok(Unit) を返す") {
            val productId = ProductId.create()
            val occurredAt = OccurredAt(LocalDateTime(2026, 6, 1, 9, 0))

            controller.replenish(productId, Quantity(3), Note("補充"), occurredAt) shouldBe RpcResult.Ok(Unit)
            verify { stockRegisterService.replenish(productId, Quantity(3), Note("補充"), occurredAt, residentId) }
        }

        test("consume は StockRegisterService.consume を occurredAt 付きで呼び Ok(Unit) を返す") {
            val productId = ProductId.create()
            val occurredAt = OccurredAt(LocalDateTime(2026, 6, 1, 9, 0))

            controller.consume(productId, Quantity(1), Note("消費"), occurredAt) shouldBe RpcResult.Ok(Unit)
            verify { stockRegisterService.consume(productId, Quantity(1), Note("消費"), occurredAt, residentId) }
        }
    })
