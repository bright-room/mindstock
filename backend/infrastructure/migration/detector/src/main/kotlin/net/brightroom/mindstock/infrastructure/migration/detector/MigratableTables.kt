package net.brightroom.mindstock.infrastructure.migration.detector

import net.brightroom.mindstock.infrastructure.schemas.catalog.CatalogItemRevisionsTable
import net.brightroom.mindstock.infrastructure.schemas.catalog.CatalogItemsTable
import net.brightroom.mindstock.infrastructure.schemas.household.HouseholdMembershipRevocationsTable
import net.brightroom.mindstock.infrastructure.schemas.household.HouseholdMembershipsTable
import net.brightroom.mindstock.infrastructure.schemas.household.HouseholdsTable
import net.brightroom.mindstock.infrastructure.schemas.product.ProductArchivesTable
import net.brightroom.mindstock.infrastructure.schemas.product.ProductMinimumStocksTable
import net.brightroom.mindstock.infrastructure.schemas.product.ProductsTable
import net.brightroom.mindstock.infrastructure.schemas.stock.StockConsumptionCorrectionsTable
import net.brightroom.mindstock.infrastructure.schemas.stock.StockConsumptionsTable
import net.brightroom.mindstock.infrastructure.schemas.stock.StockReplenishmentCorrectionsTable
import net.brightroom.mindstock.infrastructure.schemas.stock.StockReplenishmentsTable
import net.brightroom.mindstock.infrastructure.schemas.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.Table

/**
 * Canonical list of every [@Migratable] table. Tasks 7–11 add to this list
 * as each domain's schema is introduced. Listing tables explicitly (rather
 * than scanning the classpath at runtime) keeps the registry trivial and
 * test-friendly.
 */
object MigratableTables {
    val all: List<Table>
        get() =
            listOf(
                UsersTable,
                UserDisplayNamesTable,
                HouseholdsTable,
                HouseholdMembershipsTable,
                HouseholdMembershipRevocationsTable,
                CatalogItemsTable,
                CatalogItemRevisionsTable,
                ProductsTable,
                ProductMinimumStocksTable,
                ProductArchivesTable,
                StockReplenishmentsTable,
                StockConsumptionsTable,
                StockReplenishmentCorrectionsTable,
                StockConsumptionCorrectionsTable,
            )
}
