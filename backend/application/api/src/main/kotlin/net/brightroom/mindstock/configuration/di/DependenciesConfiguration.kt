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
import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
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
        provide<HouseholdService> { HouseholdService(resolve()) }
        provide<HouseholdRegisterService> { HouseholdRegisterService(resolve()) }

        // CatalogItem
        provide<CatalogItemService> { CatalogItemService(resolve()) }
        provide<CatalogItemRegisterService> { CatalogItemRegisterService(resolve()) }

        // Product
        provide<ProductService> { ProductService(resolve()) }
        provide<ProductRegisterService> { ProductRegisterService(resolve()) }

        // Stock
        provide<StockService> { StockService(resolve()) }
        provide<StockRegisterService> { StockRegisterService(resolve()) }
    }
}
