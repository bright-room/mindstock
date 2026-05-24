package net.brightroom.mindstock.configuration.di

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
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
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.repositoryConfigure() {
    dependencies {
        provide<UserRepository> { UserRepositoryImpl(resolve()) }
        provide<UserRegisterRepository> { UserRegisterRepositoryImpl(resolve()) }

        provide<HouseholdRepository> { HouseholdRepositoryImpl(resolve()) }
        provide<HouseholdRegisterRepository> { HouseholdRegisterRepositoryImpl(resolve()) }

        provide<CatalogItemRepository> { CatalogItemRepositoryImpl(resolve()) }
        provide<CatalogItemRegisterRepository> { CatalogItemRegisterRepositoryImpl(resolve()) }

        // ProductRepositoryImpl は具体型でも provide(StockRepositoryImpl が依存に取るため)
        provide<ProductRepositoryImpl> { ProductRepositoryImpl(resolve()) }
        provide<ProductRepository> { resolve<ProductRepositoryImpl>() }
        provide<ProductRegisterRepository> { ProductRegisterRepositoryImpl(resolve()) }

        provide<StockRepository> { StockRepositoryImpl(resolve(), resolve()) }
        provide<StockRegisterRepository> { StockRegisterRepositoryImpl(resolve()) }
    }
}
