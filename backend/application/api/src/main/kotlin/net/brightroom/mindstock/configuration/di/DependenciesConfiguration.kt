package net.brightroom.mindstock.configuration.di

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.annotations.Property
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
import net.brightroom.mindstock.configuration.Environment
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.domain.repository.product.ProductRepository
import net.brightroom.mindstock.domain.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.repository.stock.StockRepository
import net.brightroom.mindstock.domain.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.product.ProductRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.stock.StockRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.stock.StockRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRepositoryImpl

fun Application.dependenciesConfigure(
    @Property("ktor.environment") environment: Environment,
) {
    dependencies {
        // Repository (10)
        provide<UserRepository> { UserRepositoryImpl() }
        provide<UserRegisterRepository> { UserRegisterRepositoryImpl() }

        provide<HouseholdRepository> { HouseholdRepositoryImpl() }
        provide<HouseholdRegisterRepository> { HouseholdRegisterRepositoryImpl() }

        provide<CatalogItemRepository> { CatalogItemRepositoryImpl() }
        provide<CatalogItemRegisterRepository> { CatalogItemRegisterRepositoryImpl() }

        provide<ProductRepository> { ProductRepositoryImpl() }
        provide<ProductRegisterRepository> { ProductRegisterRepositoryImpl() }

        provide<StockRepository> { StockRepositoryImpl(resolve()) }
        provide<StockRegisterRepository> { StockRegisterRepositoryImpl() }

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
