package net.brightroom.mindstock.configuration.di

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.plugins.di.dependencies
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
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
import net.brightroom.mindstock.configuration.Environment
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemDataSource
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.stock.StockDataSource
import net.brightroom.mindstock.infrastructure.datasource.stock.StockRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.user.UserDataSource
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource

fun Application.dependenciesConfigure(
    @Property("ktor.environment") environment: Environment,
) {
    dependencies {
        // Repository (10)
        provide<UserRepository> { UserDataSource() }
        provide<UserRegisterRepository> { UserRegisterDataSource() }

        provide<HouseholdRepository> { HouseholdDataSource() }
        provide<HouseholdRegisterRepository> { HouseholdRegisterDataSource() }

        provide<CatalogItemRepository> { CatalogItemDataSource() }
        provide<CatalogItemRegisterRepository> { CatalogItemRegisterDataSource() }

        provide<ProductRepository> { ProductDataSource() }
        provide<ProductRegisterRepository> { ProductRegisterDataSource() }

        provide<StockRepository> { StockDataSource(resolve()) }
        provide<StockRegisterRepository> { StockRegisterDataSource() }

        // Handler (20)
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
