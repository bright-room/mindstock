package net.brightroom.mindstock.infrastructure.datasource.repository.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CatalogItemRepositoryImplIntegrationTest :
    FunSpec({

        test("findById returns null when id does not exist") {
            withRepositoryTestContext {
                val reader = CatalogItemRepositoryImpl(database)
                val result = tx { reader.findById(CatalogItemId(Uuid.random())) }
                result.shouldBeNull()
            }
        }

        test("search finds items by partial name match (ILIKE)") {
            withRepositoryTestContext {
                val userRepo = UserRegisterRepositoryImpl(database)
                val register = CatalogItemRegisterRepositoryImpl(database)
                val reader = CatalogItemRepositoryImpl(database)
                val creator = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("c")), DisplayName("C")) }

                tx { register.register(CatalogItemName("Milk"), CatalogItemUnit("L"), creator) }
                tx { register.register(CatalogItemName("Soy Milk"), CatalogItemUnit("L"), creator) }
                tx { register.register(CatalogItemName("Bread"), CatalogItemUnit("loaf"), creator) }

                val results = tx { reader.search("milk", limit = 10) }
                results.asList() shouldHaveSize 2
            }
        }
    })
