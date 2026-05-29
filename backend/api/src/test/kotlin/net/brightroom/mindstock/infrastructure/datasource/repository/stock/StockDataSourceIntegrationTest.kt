package net.brightroom.mindstock.infrastructure.datasource.stock

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.stock.movement.Note
import net.brightroom.mindstock.domain.model.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.stock.movement.Quantity
import net.brightroom.mindstock.infrastructure.datasource.product.ProductDataSource
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Tags("integration")
class StockDataSourceIntegrationTest :
    FunSpec({

        test("stockOf returns empty StockMovements for a fresh product") {
            withRepositoryTestContext {
                val (_, _, product) = setupUserHouseholdProduct()
                val reader = StockDataSource(ProductDataSource())

                val stock = tx { reader.stockOf(product) }
                stock.movements.list.size shouldBe 0
                stock.currentQuantity() shouldBe 0
            }
        }

        test("movementHistory respects limit and returns DESC by occurred_at") {
            withRepositoryTestContext {
                val (user, _, product) = setupUserHouseholdProduct()
                val register = StockRegisterDataSource()
                val reader = StockDataSource(ProductDataSource())

                val t0 = Clock.System.now().minus(10.seconds)
                tx { register.replenish(product, Quantity(1), OccurredAt(t0), user.userId, Note("a")) }
                tx { register.replenish(product, Quantity(2), OccurredAt(t0.plus(1.seconds)), user.userId, Note("b")) }
                tx { register.replenish(product, Quantity(3), OccurredAt(t0.plus(2.seconds)), user.userId, Note("c")) }

                val history = tx { reader.movementHistory(product, limit = 2) }
                val rows = history.list
                rows shouldHaveSize 2
                rows[0].quantity shouldBe Quantity(3) // most recent first
                rows[1].quantity shouldBe Quantity(2)
            }
        }
    })
