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
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.application.service.user.UserService
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
import net.brightroom.mindstock.presentation.rpc.catalog.CatalogController
import net.brightroom.mindstock.presentation.rpc.catalog.CatalogControllerFactory
import net.brightroom.mindstock.presentation.rpc.household.HouseholdController
import net.brightroom.mindstock.presentation.rpc.household.HouseholdControllerFactory
import net.brightroom.mindstock.presentation.rpc.product.ProductController
import net.brightroom.mindstock.presentation.rpc.product.ProductControllerFactory
import net.brightroom.mindstock.presentation.rpc.stock.StockController
import net.brightroom.mindstock.presentation.rpc.stock.StockControllerFactory
import net.brightroom.mindstock.presentation.rpc.user.UserController
import net.brightroom.mindstock.presentation.rpc.user.UserControllerFactory
import net.brightroom.mindstock.presentation.rpc.user.UserPublicController
import net.brightroom.mindstock.presentation.rpc.user.UserPublicControllerFactory
import org.jetbrains.exposed.v1.jdbc.Database

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
        provide<UserService> { UserService(resolve()) }
        provide<UserRegisterService> { UserRegisterService(resolve()) }

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

        // Controller Factory (30) — per-WS-connection 単位で Controller を組み立てる
        provide<UserPublicControllerFactory> {
            val urs = resolve<UserRegisterService>()
            val db = resolve<Database>()
            UserPublicControllerFactory { session -> UserPublicController(urs, session, db) }
        }
        provide<UserControllerFactory> {
            val urs = resolve<UserRegisterService>()
            val db = resolve<Database>()
            UserControllerFactory { session -> UserController(urs, session, db) }
        }
        provide<HouseholdControllerFactory> {
            val hs = resolve<HouseholdService>()
            val hrs = resolve<HouseholdRegisterService>()
            val us = resolve<UserService>()
            val db = resolve<Database>()
            HouseholdControllerFactory { session -> HouseholdController(hs, hrs, us, session, db) }
        }
        provide<CatalogControllerFactory> {
            val cs = resolve<CatalogItemService>()
            val crs = resolve<CatalogItemRegisterService>()
            val db = resolve<Database>()
            CatalogControllerFactory { session -> CatalogController(cs, crs, session, db) }
        }
        provide<ProductControllerFactory> {
            val ps = resolve<ProductService>()
            val prs = resolve<ProductRegisterService>()
            val hs = resolve<HouseholdService>()
            val cs = resolve<CatalogItemService>()
            val db = resolve<Database>()
            ProductControllerFactory { session -> ProductController(ps, prs, hs, cs, session, db) }
        }
        provide<StockControllerFactory> {
            val ss = resolve<StockService>()
            val srs = resolve<StockRegisterService>()
            val ps = resolve<ProductService>()
            val hs = resolve<HouseholdService>()
            val db = resolve<Database>()
            StockControllerFactory { session -> StockController(ss, srs, ps, hs, session, db) }
        }
    }
}
