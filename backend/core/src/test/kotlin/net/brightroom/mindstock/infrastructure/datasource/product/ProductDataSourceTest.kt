package net.brightroom.mindstock.infrastructure.datasource.product

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.shopping.Wanted
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentRegisterDataSource
import net.brightroom.mindstock.testfixtures.TestDatabase

@Tags("integration")
class ProductDataSourceTest :
    FunSpec({
        val db = TestDatabase.database
        val residentRegister = ResidentRegisterDataSource(db)
        val householdRegister = HouseholdRegisterDataSource(db)
        val productRegister = ProductRegisterDataSource(db)
        val productDataSource = ProductDataSource(db)

        beforeTest { TestDatabase.clean() }

        fun createHousehold(authSubject: String): Household {
            val owner =
                residentRegister.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject(authSubject)),
                    DisplayName("テストオーナー"),
                )
            val household = Household.create(HouseholdName("テスト世帯"), owner)
            householdRegister.registerHousehold(household)
            return household
        }

        test("findById: custom 商品が最新 revision(unit/minimum)付きで引ける") {
            val household = createHousehold("sub-product-1")
            val product =
                Product.custom(
                    ProductName("牛乳"),
                    Barcode.Unlinked,
                    ProductUnit("本"),
                    MinimumStock(2),
                )
            productRegister.registerCustom(product, household.id)

            val loaded = productDataSource.findById(product.id)

            loaded.name() shouldBe "牛乳"
            loaded.setting.unit() shouldBe "本"
            loaded.setting.minimumStock() shouldBe 2
        }

        test("listByHousehold は採用中のみ、setWanted した商品は listWanted に出る") {
            val household = createHousehold("sub-product-2")
            val egg =
                Product.custom(
                    ProductName("卵"),
                    Barcode.Unlinked,
                    ProductUnit("パック"),
                    MinimumStock(1),
                )
            val bread =
                Product.custom(
                    ProductName("パン"),
                    Barcode.Unlinked,
                    ProductUnit("斤"),
                    MinimumStock(0),
                )
            productRegister.registerCustom(egg, household.id)
            productRegister.registerCustom(bread, household.id)
            productRegister.setWanted(egg.id, Wanted(true))

            val all = productDataSource.listByHousehold(household.id)
            val wanted = productDataSource.listWanted(household.id)

            all.size() shouldBe 2
            wanted.list.map { it.id }.toSet() shouldBe setOf(egg.id)
        }
    })
