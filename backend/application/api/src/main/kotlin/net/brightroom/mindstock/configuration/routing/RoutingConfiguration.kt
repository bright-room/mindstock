@file:OptIn(ExperimentalSerializationApi::class)

package net.brightroom.mindstock.configuration.routing

import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
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
import net.brightroom.mindstock.configuration.auth.applicationCall
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.presentation.rpc.CatalogRpcService
import net.brightroom.mindstock.presentation.rpc.HouseholdRpcService
import net.brightroom.mindstock.presentation.rpc.ProductRpcService
import net.brightroom.mindstock.presentation.rpc.StockRpcService
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService
import net.brightroom.mindstock.presentation.rpc.UserRpcService
import net.brightroom.mindstock.presentation.rpc.catalog.CatalogRpcServiceImpl
import net.brightroom.mindstock.presentation.rpc.household.HouseholdRpcServiceImpl
import net.brightroom.mindstock.presentation.rpc.product.ProductRpcServiceImpl
import net.brightroom.mindstock.presentation.rpc.stock.StockRpcServiceImpl
import net.brightroom.mindstock.presentation.rpc.user.UserPublicRpcServiceImpl
import net.brightroom.mindstock.presentation.rpc.user.UserRpcServiceImpl
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.routingConfigure(
    @Property("ktor.environment") environment: Environment,
) {
    install(ContentNegotiation) {
        jsonIo(CustomJson)
    }
    install(Krpc) {
        serialization { json(KrpcJson) }
    }

    // Pre-resolve Handlers and Repositories at module-init time.
    // The factory passed to `registerService<T> { ... }` is non-suspend, so we
    // cannot call the suspend `resolve<T>()` inside it. Capture references here
    // and reuse them across per-connection Service Impl instantiations.
    val registerUserHandler: RegisterUserHandler by dependencies
    val renameUserHandler: RenameUserHandler by dependencies

    val householdService: HouseholdService by dependencies
    val householdRegisterService: HouseholdRegisterService by dependencies

    val catalogItemService: CatalogItemService by dependencies
    val catalogItemRegisterService: CatalogItemRegisterService by dependencies

    val productService: ProductService by dependencies
    val productRegisterService: ProductRegisterService by dependencies

    val stockService: StockService by dependencies
    val stockRegisterService: StockRegisterService by dependencies

    val userRepository: UserRepository by dependencies
    val householdRepository: HouseholdRepository by dependencies
    val catalogItemRepository: CatalogItemRepository by dependencies
    val productRepository: ProductRepository by dependencies

    val database: Database by dependencies

    install(net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin)

    routing {
        // JWT 検証は通すが User 未登録でも通る (register 専用)
        authenticate("user-public") {
            rpc("/api/v1/user/public") {
                val call = this.applicationCall
                registerService<UserPublicRpcService> {
                    UserPublicRpcServiceImpl(registerUserHandler, call, database)
                }
            }
        }
        // JWT 検証 + User 登録チェック
        authenticate("user") {
            rpc("/api/v1/user") {
                val call = this.applicationCall
                registerService<UserRpcService> {
                    UserRpcServiceImpl(
                        renameUserHandler,
                        userRepository,
                        call,
                        database,
                    )
                }
            }
            rpc("/api/v1/household") {
                val call = this.applicationCall
                registerService<HouseholdRpcService> {
                    HouseholdRpcServiceImpl(
                        householdService,
                        householdRegisterService,
                        householdRepository,
                        userRepository,
                        call,
                        database,
                    )
                }
            }
            rpc("/api/v1/catalog") {
                val call = this.applicationCall
                registerService<CatalogRpcService> {
                    CatalogRpcServiceImpl(
                        catalogItemService,
                        catalogItemRegisterService,
                        catalogItemRepository,
                        userRepository,
                        call,
                        database,
                    )
                }
            }
            rpc("/api/v1/product") {
                val call = this.applicationCall
                registerService<ProductRpcService> {
                    ProductRpcServiceImpl(
                        productService,
                        productRegisterService,
                        householdRepository,
                        catalogItemRepository,
                        productRepository,
                        userRepository,
                        call,
                        database,
                    )
                }
            }
            rpc("/api/v1/stock") {
                val call = this.applicationCall
                registerService<StockRpcService> {
                    StockRpcServiceImpl(
                        stockService,
                        stockRegisterService,
                        productRepository,
                        householdRepository,
                        userRepository,
                        call,
                        database,
                    )
                }
            }
        }
    }
}
