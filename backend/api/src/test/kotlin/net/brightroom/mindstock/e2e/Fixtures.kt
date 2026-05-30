package net.brightroom.mindstock.e2e

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource
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
private fun shortRandom(): String =
    Uuid
        .random()
        .toString()
        .replace("-", "")
        .take(8)

suspend fun E2eContext.seedUser(
    displayName: String = "User-${shortRandom()}",
    provider: AuthProvider = AuthProvider.ZITADEL,
    subject: String = "sub-${shortRandom()}",
): Profile =
    UserRegisterDataSource(database).register(
        identity = AuthIdentity(provider, AuthSubject(subject)),
        defaultDisplayName = DisplayName(displayName),
    )

suspend fun E2eContext.seedHousehold(owner: Profile): Household = HouseholdRegisterDataSource(database).create(owner.userId)

suspend fun E2eContext.seedCatalogItem(
    createdBy: Profile,
    name: String = "Item-${shortRandom()}",
    unit: String = "個",
): CatalogItem =
    CatalogItemRegisterDataSource(database).register(
        name = CatalogItemName(name),
        unit = CatalogItemUnit(unit),
        createdBy = createdBy.userId,
    )

suspend fun E2eContext.seedProduct(
    household: Household,
    catalogItem: CatalogItem,
): Product = ProductRegisterDataSource(database).adopt(household, catalogItem)
