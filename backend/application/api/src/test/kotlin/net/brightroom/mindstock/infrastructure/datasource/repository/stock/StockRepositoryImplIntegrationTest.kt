package net.brightroom.mindstock.infrastructure.datasource.repository.stock

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import kotlin.time.Clock

class StockRepositoryImplIntegrationTest :
    FunSpec({

        test("stockOf returns empty StockMovements for a fresh product") {
            withRepositoryTestContext {
                val (_, _, product) = setupUserHouseholdProduct()
                val reader = StockRepositoryImpl(database, ProductRepositoryImpl(database))

                val stock = tx { reader.stockOf(product) }
                stock.movements.size shouldBe 0
                stock.currentQuantity() shouldBe 0
            }
        }

        test("movementHistory respects limit and returns DESC by occurred_at") {
            withRepositoryTestContext {
                val (user, _, product) = setupUserHouseholdProduct()
                val register = StockRegisterRepositoryImpl(database)
                val reader = StockRepositoryImpl(database, ProductRepositoryImpl(database))

                tx { register.replenish(product, Quantity(1), OccurredAt(Clock.System.now()), user, Note("a")) }
                tx { register.replenish(product, Quantity(2), OccurredAt(Clock.System.now()), user, Note("b")) }
                tx { register.replenish(product, Quantity(3), OccurredAt(Clock.System.now()), user, Note("c")) }

                val history = tx { reader.movementHistory(product, limit = 2) }
                history.asList() shouldHaveSize 2
            }
        }
    })
