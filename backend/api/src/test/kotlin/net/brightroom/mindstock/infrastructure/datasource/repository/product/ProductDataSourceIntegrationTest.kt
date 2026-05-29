package net.brightroom.mindstock.infrastructure.datasource.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource
import io.kotest.core.annotation.Tags

@Tags("integration")
class ProductDataSourceIntegrationTest :
    FunSpec({

        test("find returns null when no product matches") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRepo = HouseholdRegisterDataSource()
                val catalogRepo = CatalogItemRegisterDataSource()
                val productReader = ProductDataSource()

                val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
                val household = tx { householdRepo.create(user) }
                val item = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user) }

                val result = tx { productReader.find(household, item) }
                result.shouldBeNull()
            }
        }

        test("listOf returns all products of household including archived") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRepo = HouseholdRegisterDataSource()
                val catalogRepo = CatalogItemRegisterDataSource()
                val productRegister = ProductRegisterDataSource()
                val productReader = ProductDataSource()

                val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
                val household = tx { householdRepo.create(user) }
                val milk = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user) }
                val bread = tx { catalogRepo.register(CatalogItemName("Bread"), CatalogItemUnit("loaf"), user) }

                val milkProduct = tx { productRegister.adopt(household, milk) }
                tx { productRegister.adopt(household, bread) }
                tx { productRegister.archive(milkProduct, user) }

                val results = tx { productReader.listOf(household) }
                results.asList() shouldHaveSize 2
                results.asList().single { it.catalogItem.name == CatalogItemName("Milk") }.archived shouldBe true
            }
        }
    })
