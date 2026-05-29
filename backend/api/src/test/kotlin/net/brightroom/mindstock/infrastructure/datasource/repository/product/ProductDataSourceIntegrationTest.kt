package net.brightroom.mindstock.infrastructure.datasource.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Tags("integration")
class ProductDataSourceIntegrationTest :
    FunSpec({

        test("find throws ResourceNotFoundException when no product matches") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRepo = HouseholdRegisterDataSource()
                val catalogRepo = CatalogItemRegisterDataSource()
                val productReader = ProductDataSource()

                val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
                val household = tx { householdRepo.create(user.userId) }
                val item = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user.userId) }

                shouldThrow<ResourceNotFoundException> {
                    tx { productReader.find(household, item) }
                }.message shouldContain "product not found"
            }
        }

        test("findById throws ResourceNotFoundException when product does not exist") {
            withRepositoryTestContext {
                val productReader = ProductDataSource()
                shouldThrow<ResourceNotFoundException> {
                    tx { productReader.findById(ProductId(Uuid.random())) }
                }.message shouldContain "product not found"
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
                val household = tx { householdRepo.create(user.userId) }
                val milk = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user.userId) }
                val bread = tx { catalogRepo.register(CatalogItemName("Bread"), CatalogItemUnit("loaf"), user.userId) }

                val milkProduct = tx { productRegister.adopt(household, milk) }
                tx { productRegister.adopt(household, bread) }
                tx { productRegister.archive(milkProduct, user.userId) }

                val results = tx { productReader.listOf(household) }
                results.list shouldHaveSize 2
                results.list.single { it.catalogItem.name == CatalogItemName("Milk") }.archived shouldBe true
            }
        }
    })
