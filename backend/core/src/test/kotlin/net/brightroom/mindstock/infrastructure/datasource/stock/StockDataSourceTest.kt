package net.brightroom.mindstock.infrastructure.datasource.stock

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentRegisterDataSource
import net.brightroom.mindstock.testfixtures.TestDatabase

@Tags("integration")
class StockDataSourceTest :
    FunSpec({
        val db = TestDatabase.database
        val residentRegister = ResidentRegisterDataSource(db)
        val householdRegister = HouseholdRegisterDataSource(db)
        val productDataSource = ProductDataSource(db)
        val productRegister = ProductRegisterDataSource(db)
        val stockRegister = StockRegisterDataSource(db)
        val stockDataSource = StockDataSource(db, productDataSource)

        beforeTest { TestDatabase.clean() }

        test("findByProduct: 補充+消費で netQuantity と actor displayName が引ける") {
            // actor・世帯・商品を準備
            val actor =
                residentRegister.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-x")),
                    DisplayName("たろう"),
                )
            val household = Household.create(HouseholdName("わが家"), actor)
            householdRegister.registerHousehold(household)
            val product =
                Product.custom(
                    ProductName("牛乳"),
                    Barcode.Unlinked,
                    ProductUnit("個"),
                    MinimumStock(1),
                )
            productRegister.registerCustom(product, household.id)

            // 補充 5 → 消費 2
            stockRegister.appendMovement(
                product.id,
                StockMovement.Replenishment(
                    MovementIdentity.Pending,
                    Quantity(5),
                    OccurredAt.now(),
                    actor,
                    Note("入荷"),
                ),
            )
            stockRegister.appendMovement(
                product.id,
                StockMovement.Consumption(
                    MovementIdentity.Pending,
                    Quantity(2),
                    OccurredAt.now(),
                    actor,
                    Note("消費"),
                ),
            )

            val stock = stockDataSource.findByProduct(product.id)

            // movement 件数・netQuantity・actor displayName を検証
            stock.movements.size() shouldBe 2
            stock.currentQuantity()() shouldBe 3
            // 取得順に依存せず、全 movement の actor が正しく hydrate されていることを確認
            stock.movements.list.all { it.actor.profile.displayName == DisplayName("たろう") } shouldBe true
        }

        test("listByHousehold: 商品 3 つでも各商品の movement が正しく束ねられる") {
            // actor・世帯を準備
            val actor =
                residentRegister.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-y")),
                    DisplayName("はなこ"),
                )
            val household = Household.create(HouseholdName("うちの家"), actor)
            householdRegister.registerHousehold(household)

            // 商品 3 つを登録
            val milk = Product.custom(ProductName("牛乳"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(0))
            val egg = Product.custom(ProductName("卵"), Barcode.Unlinked, ProductUnit("パック"), MinimumStock(0))
            val bread = Product.custom(ProductName("パン"), Barcode.Unlinked, ProductUnit("斤"), MinimumStock(0))
            productRegister.registerCustom(milk, household.id)
            productRegister.registerCustom(egg, household.id)
            productRegister.registerCustom(bread, household.id)

            // 牛乳に Replenishment(4)、卵に Replenishment(10)、パンは movement なし
            stockRegister.appendMovement(
                milk.id,
                StockMovement.Replenishment(MovementIdentity.Pending, Quantity(4), OccurredAt.now(), actor, Note("")),
            )
            stockRegister.appendMovement(
                egg.id,
                StockMovement.Replenishment(MovementIdentity.Pending, Quantity(10), OccurredAt.now(), actor, Note("")),
            )

            val stocks = stockDataSource.listByHousehold(household.id)

            // 全商品が揃っている
            stocks.size() shouldBe 3

            // 各商品の movement と netQuantity を検証
            val milkStock = stocks.list.first { it.product.id == milk.id }
            milkStock.movements.size() shouldBe 1
            milkStock.currentQuantity()() shouldBe 4

            val eggStock = stocks.list.first { it.product.id == egg.id }
            eggStock.movements.size() shouldBe 1
            eggStock.currentQuantity()() shouldBe 10

            val breadStock = stocks.list.first { it.product.id == bread.id }
            breadStock.movements.size() shouldBe 0
            breadStock.currentQuantity()() shouldBe 0
        }

        test("appendMovement: Pending で追記した movement は再 load で Persisted(採番済み id) になり、連続追記でも id が衝突しない") {
            val actor =
                residentRegister.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-identity")),
                    DisplayName("たろう"),
                )
            val household = Household.create(HouseholdName("わが家"), actor)
            householdRegister.registerHousehold(household)
            val product =
                Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
            productRegister.registerCustom(product, household.id)

            // 同一商品へ Pending で 2 回連続追記
            stockRegister.appendMovement(
                product.id,
                StockMovement.Replenishment(MovementIdentity.Pending, Quantity(3), OccurredAt.now(), actor, Note("一回目")),
            )
            stockRegister.appendMovement(
                product.id,
                StockMovement.Replenishment(MovementIdentity.Pending, Quantity(2), OccurredAt.now(), actor, Note("二回目")),
            )

            val stock = stockDataSource.findByProduct(product.id)

            // INSERT → 再 load で全 movement が Persisted に hydrate される(Pending が残らない)
            stock.movements.list.all { it.identity is MovementIdentity.Persisted } shouldBe true
            // 連続追記した 2 件がユニークな Persisted id を持つ(id 衝突なし)
            val ids = stock.movements.list.map { (it.identity as MovementIdentity.Persisted).id }
            ids.toSet().size shouldBe 2
        }

        test("listByHousehold: 商品ごとに別 actor の movement でも actor が取り違わない") {
            // owner(たろう)の世帯に member(はなこ)を追加し、商品ごとに別 actor が操作する
            val owner =
                residentRegister.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-owner-cross")),
                    DisplayName("たろう"),
                )
            val member =
                residentRegister.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-member-cross")),
                    DisplayName("はなこ"),
                )
            val household = Household.create(HouseholdName("混在世帯"), owner)
            householdRegister.registerHousehold(household)
            householdRegister.joinMember(
                household.id,
                member,
                HouseholdMemberRole.メンバー,
            )

            val milk = Product.custom(ProductName("牛乳"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(0))
            val egg = Product.custom(ProductName("卵"), Barcode.Unlinked, ProductUnit("パック"), MinimumStock(0))
            productRegister.registerCustom(milk, household.id)
            productRegister.registerCustom(egg, household.id)

            // 牛乳は owner、卵は member が補充
            stockRegister.appendMovement(
                milk.id,
                StockMovement.Replenishment(MovementIdentity.Pending, Quantity(1), OccurredAt.now(), owner, Note("")),
            )
            stockRegister.appendMovement(
                egg.id,
                StockMovement.Replenishment(MovementIdentity.Pending, Quantity(1), OccurredAt.now(), member, Note("")),
            )

            val stocks = stockDataSource.listByHousehold(household.id)

            // 一括 actor 解決(resolveActors)が product をまたいで actor を取り違えないこと
            stocks.list
                .first { it.product.id == milk.id }
                .movements.list
                .single()
                .actor.profile.displayName shouldBe DisplayName("たろう")
            stocks.list
                .first { it.product.id == egg.id }
                .movements.list
                .single()
                .actor.profile.displayName shouldBe DisplayName("はなこ")
        }
    })
