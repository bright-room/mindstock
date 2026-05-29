package net.brightroom.mindstock.e2e.stock

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedCatalogItem
import net.brightroom.mindstock.e2e.seedHousehold
import net.brightroom.mindstock.e2e.seedProduct
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.StockRpcService
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Pinned behaviors:
 * 1. replenish creates a Replenishment and increases current quantity.
 * 2. consume reduces current quantity.
 * 3. get returns a Stock with zero quantity for an untouched product.
 * 4. list returns all stocks for the household.
 * 5. movementHistory returns mixed Replenishment + Consumption
 *    (verifies @Serializable sealed interface polymorphic round-trip over kRPC).
 * 6. get with unknown productId returns Err(NotFound).
 * 7. consume with unknown productId returns Err(NotFound).
 * 8. movementHistory limit caps the returned size.
 */
@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class StockRpcServiceE2eTest :
    FunSpec({

        test("replenish creates a Replenishment and increases current quantity") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item = seedCatalogItem(createdBy = owner)
                val product = seedProduct(hh, item)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "stock")
                        .withService<StockRpcService>()

                val r =
                    rpc.replenish(
                        product.id,
                        Quantity(5),
                        OccurredAt(Instant.parse("2026-05-26T10:00:00Z")),
                        Note(""),
                    )
                r.shouldBeInstanceOf<RpcResult.Ok<Replenishment>>()
                r.value.quantity shouldBe Quantity(5)

                val got = rpc.get(product.id)
                got.shouldBeInstanceOf<RpcResult.Ok<Stock>>()
                got.value.currentQuantity() shouldBe 5
            }
        }

        test("consume reduces current quantity") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item = seedCatalogItem(createdBy = owner)
                val product = seedProduct(hh, item)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "stock")
                        .withService<StockRpcService>()
                rpc
                    .replenish(
                        product.id,
                        Quantity(5),
                        OccurredAt(Instant.parse("2026-05-26T10:00:00Z")),
                        Note(""),
                    ).shouldBeInstanceOf<RpcResult.Ok<Replenishment>>()

                rpc
                    .consume(
                        product.id,
                        Quantity(2),
                        OccurredAt(Instant.parse("2026-05-26T11:00:00Z")),
                        Note("breakfast"),
                    ).shouldBeInstanceOf<RpcResult.Ok<Consumption>>()

                val got = rpc.get(product.id)
                got.shouldBeInstanceOf<RpcResult.Ok<Stock>>()
                got.value.currentQuantity() shouldBe 3
            }
        }

        test("get returns a Stock with zero quantity for an untouched product") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item = seedCatalogItem(createdBy = owner)
                val product = seedProduct(hh, item)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "stock")
                        .withService<StockRpcService>()

                val got = rpc.get(product.id)
                got.shouldBeInstanceOf<RpcResult.Ok<Stock>>()
                got.value.currentQuantity() shouldBe 0
            }
        }

        test("list returns all stocks for the household") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item1 = seedCatalogItem(createdBy = owner, name = "A")
                val item2 = seedCatalogItem(createdBy = owner, name = "B")
                seedProduct(hh, item1)
                seedProduct(hh, item2)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "stock")
                        .withService<StockRpcService>()

                val r = rpc.list(hh.id)
                r.shouldBeInstanceOf<RpcResult.Ok<List<Stock>>>()
                r.value shouldHaveSize 2
            }
        }

        test("movementHistory returns mixed Replenishment + Consumption (polymorphic)") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item = seedCatalogItem(createdBy = owner)
                val product = seedProduct(hh, item)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "stock")
                        .withService<StockRpcService>()

                rpc
                    .replenish(
                        product.id,
                        Quantity(10),
                        OccurredAt(Instant.parse("2026-05-26T08:00:00Z")),
                        Note(""),
                    ).shouldBeInstanceOf<RpcResult.Ok<Replenishment>>()
                rpc
                    .consume(
                        product.id,
                        Quantity(3),
                        OccurredAt(Instant.parse("2026-05-26T09:00:00Z")),
                        Note(""),
                    ).shouldBeInstanceOf<RpcResult.Ok<Consumption>>()
                rpc
                    .replenish(
                        product.id,
                        Quantity(2),
                        OccurredAt(Instant.parse("2026-05-26T10:00:00Z")),
                        Note(""),
                    ).shouldBeInstanceOf<RpcResult.Ok<Replenishment>>()

                val r = rpc.movementHistory(product.id, limit = 10)
                r.shouldBeInstanceOf<RpcResult.Ok<StockMovements>>()
                val history = r.value
                history.list shouldHaveSize 3
                // DESC by occurredAt: newest first.
                history.list[0].shouldBeInstanceOf<Replenishment>()
                history.list[1].shouldBeInstanceOf<Consumption>()
                history.list[2].shouldBeInstanceOf<Replenishment>()
                history.list.map { it.occurredAt } shouldBe
                    listOf(
                        OccurredAt(Instant.parse("2026-05-26T10:00:00Z")),
                        OccurredAt(Instant.parse("2026-05-26T09:00:00Z")),
                        OccurredAt(Instant.parse("2026-05-26T08:00:00Z")),
                    )
            }
        }

        test("get with unknown productId returns Err(NotFound)") {
            e2eTest {
                val owner = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "stock")
                        .withService<StockRpcService>()
                val r = rpc.get(ProductId(Uuid.random()))
                r.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                r.error.shouldBeInstanceOf<RpcError.NotFound>()
            }
        }

        test("consume with unknown productId returns Err(NotFound)") {
            e2eTest {
                val owner = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "stock")
                        .withService<StockRpcService>()
                val r =
                    rpc.consume(
                        ProductId(Uuid.random()),
                        Quantity(1),
                        OccurredAt(Instant.parse("2026-05-26T10:00:00Z")),
                        Note(""),
                    )
                r.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                r.error.shouldBeInstanceOf<RpcError.NotFound>()
            }
        }

        test("movementHistory limit caps the returned size") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item = seedCatalogItem(createdBy = owner)
                val product = seedProduct(hh, item)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "stock")
                        .withService<StockRpcService>()
                repeat(5) { i ->
                    val hour = (10 + i).toString().padStart(2, '0')
                    rpc
                        .replenish(
                            product.id,
                            Quantity(1),
                            OccurredAt(Instant.parse("2026-05-26T$hour:00:00Z")),
                            Note(""),
                        ).shouldBeInstanceOf<RpcResult.Ok<Replenishment>>()
                }

                val r = rpc.movementHistory(product.id, limit = 3)
                r.shouldBeInstanceOf<RpcResult.Ok<StockMovements>>()
                r.value.list shouldHaveSize 3
            }
        }
    })
