package net.brightroom.mindstock.infrastructure.datasource.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class CatalogItemDataSourceIntegrationTest :
    FunSpec({

        test("findById throws ResourceNotFoundException when id does not exist") {
            withRepositoryTestContext {
                val reader = CatalogItemDataSource(database)
                shouldThrow<ResourceNotFoundException> {
                    runBlocking { reader.findById(CatalogItemId(Uuid.random())) }
                }.message shouldContain "catalog item not found"
            }
        }

        test("search finds items by partial name match (ILIKE)") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource(database)
                val register = CatalogItemRegisterDataSource(database)
                val reader = CatalogItemDataSource(database)
                val creator = runBlocking { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("c")), DisplayName("C")) }

                runBlocking { register.register(CatalogItemName("Milk"), CatalogItemUnit("L"), creator.userId) }
                runBlocking { register.register(CatalogItemName("Soy Milk"), CatalogItemUnit("L"), creator.userId) }
                runBlocking { register.register(CatalogItemName("Bread"), CatalogItemUnit("loaf"), creator.userId) }

                val results = runBlocking { reader.search("milk", limit = 10) }
                results.list shouldHaveSize 2
            }
        }
    })
