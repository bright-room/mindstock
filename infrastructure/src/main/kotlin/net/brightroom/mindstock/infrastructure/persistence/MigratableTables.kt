package net.brightroom.mindstock.infrastructure.persistence

import net.brightroom.mindstock.infrastructure.schema.catalog.CatalogItemNamesTable
import net.brightroom.mindstock.infrastructure.schema.catalog.CatalogItemUnitsTable
import net.brightroom.mindstock.infrastructure.schema.catalog.CatalogItemsTable
import net.brightroom.mindstock.infrastructure.schema.household.HouseholdMembershipRevocationsTable
import net.brightroom.mindstock.infrastructure.schema.household.HouseholdMembershipsTable
import net.brightroom.mindstock.infrastructure.schema.household.HouseholdsTable
import net.brightroom.mindstock.infrastructure.schema.product.ProductArchivesTable
import net.brightroom.mindstock.infrastructure.schema.product.ProductMinimumStocksTable
import net.brightroom.mindstock.infrastructure.schema.product.ProductsTable
import net.brightroom.mindstock.infrastructure.schema.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.Table

/**
 * Canonical list of every [@Migratable] table. Tasks 7–11 add to this list
 * as each domain's schema is introduced. Listing tables explicitly (rather
 * than scanning the classpath at runtime) keeps the registry trivial and
 * test-friendly.
 */
object MigratableTables {
    val all: List<Table>
        get() = listOf(
            UsersTable,
            UserDisplayNamesTable,
            HouseholdsTable,
            HouseholdMembershipsTable,
            HouseholdMembershipRevocationsTable,
            CatalogItemsTable,
            CatalogItemNamesTable,
            CatalogItemUnitsTable,
            ProductsTable,
            ProductMinimumStocksTable,
            ProductArchivesTable,
        )
}
