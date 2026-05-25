package net.brightroom.mindstock.e2e

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Seed helpers for e2e tests.
 *
 * Each helper writes domain entities directly through the Repository layer (no RPC),
 * giving tests a clean way to arrange state before exercising the public API.
 *
 * Helpers are [E2eContext] extensions so tests call them as `seedUser()`,
 * `seedHousehold(owner)`, etc.
 */

@OptIn(ExperimentalUuidApi::class)
private fun shortRandom(): String = Uuid.random().toString().replace("-", "").take(8)

fun E2eContext.seedUser(
    displayName: String = "User-${shortRandom()}",
    provider: AuthProvider = AuthProvider.ZITADEL,
    subject: String = "sub-${shortRandom()}",
): User =
    transaction(database) {
        UserRegisterRepositoryImpl().register(
            identity = AuthIdentity(provider, AuthSubject(subject)),
            defaultDisplayName = DisplayName(displayName),
        )
    }

fun E2eContext.seedHousehold(owner: User): Household =
    transaction(database) {
        HouseholdRegisterRepositoryImpl().create(owner)
    }

fun E2eContext.seedCatalogItem(
    createdBy: User,
    name: String = "Item-${shortRandom()}",
    unit: String = "個",
): CatalogItem =
    transaction(database) {
        CatalogItemRegisterRepositoryImpl().register(
            name = CatalogItemName(name),
            unit = CatalogItemUnit(unit),
            createdBy = createdBy,
        )
    }

fun E2eContext.seedProduct(
    household: Household,
    catalogItem: CatalogItem,
): Product =
    transaction(database) {
        ProductRegisterRepositoryImpl().adopt(household, catalogItem)
    }
