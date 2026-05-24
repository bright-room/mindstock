package net.brightroom.mindstock.configuration.di

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import net.brightroom.mindstock.application.usecase.catalog.FindCatalogItemByIdHandler
import net.brightroom.mindstock.application.usecase.catalog.RegisterCatalogItemHandler
import net.brightroom.mindstock.application.usecase.catalog.ReviseCatalogItemHandler
import net.brightroom.mindstock.application.usecase.catalog.SearchCatalogItemsHandler
import net.brightroom.mindstock.application.usecase.household.CreateHouseholdHandler
import net.brightroom.mindstock.application.usecase.household.FindHouseholdOfUserHandler
import net.brightroom.mindstock.application.usecase.household.InviteMemberHandler
import net.brightroom.mindstock.application.usecase.household.RevokeMembershipHandler
import net.brightroom.mindstock.application.usecase.product.AdoptProductHandler
import net.brightroom.mindstock.application.usecase.product.ArchiveProductHandler
import net.brightroom.mindstock.application.usecase.product.FindProductHandler
import net.brightroom.mindstock.application.usecase.product.ListProductsOfHouseholdHandler
import net.brightroom.mindstock.application.usecase.product.SetMinimumStockHandler
import net.brightroom.mindstock.application.usecase.stock.ConsumeStockHandler
import net.brightroom.mindstock.application.usecase.stock.GetMovementHistoryHandler
import net.brightroom.mindstock.application.usecase.stock.GetStockHandler
import net.brightroom.mindstock.application.usecase.stock.ListStocksHandler
import net.brightroom.mindstock.application.usecase.stock.ReplenishStockHandler
import net.brightroom.mindstock.application.usecase.user.RegisterUserHandler
import net.brightroom.mindstock.application.usecase.user.RenameUserHandler

fun Application.usecaseConfigure() {
    dependencies {
        // User
        provide<RegisterUserHandler> { RegisterUserHandler(resolve()) }
        provide<RenameUserHandler> { RenameUserHandler(resolve()) }

        // Household
        provide<CreateHouseholdHandler> { CreateHouseholdHandler(resolve()) }
        provide<InviteMemberHandler> { InviteMemberHandler(resolve()) }
        provide<RevokeMembershipHandler> { RevokeMembershipHandler(resolve()) }
        provide<FindHouseholdOfUserHandler> { FindHouseholdOfUserHandler(resolve()) }

        // CatalogItem
        provide<RegisterCatalogItemHandler> { RegisterCatalogItemHandler(resolve()) }
        provide<ReviseCatalogItemHandler> { ReviseCatalogItemHandler(resolve()) }
        provide<SearchCatalogItemsHandler> { SearchCatalogItemsHandler(resolve()) }
        provide<FindCatalogItemByIdHandler> { FindCatalogItemByIdHandler(resolve()) }

        // Product
        provide<AdoptProductHandler> { AdoptProductHandler(resolve()) }
        provide<SetMinimumStockHandler> { SetMinimumStockHandler(resolve()) }
        provide<ArchiveProductHandler> { ArchiveProductHandler(resolve()) }
        provide<ListProductsOfHouseholdHandler> { ListProductsOfHouseholdHandler(resolve()) }
        provide<FindProductHandler> { FindProductHandler(resolve()) }

        // Stock
        provide<ReplenishStockHandler> { ReplenishStockHandler(resolve()) }
        provide<ConsumeStockHandler> { ConsumeStockHandler(resolve()) }
        provide<GetStockHandler> { GetStockHandler(resolve()) }
        provide<ListStocksHandler> { ListStocksHandler(resolve()) }
        provide<GetMovementHistoryHandler> { GetMovementHistoryHandler(resolve()) }
    }
}
