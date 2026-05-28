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
import net.brightroom.mindstock.configuration.auth.applicationCall
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.repository.product.ProductRepository
import net.brightroom.mindstock.domain.repository.user.UserRepository
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

    val findHouseholdOfUserHandler: FindHouseholdOfUserHandler by dependencies
    val createHouseholdHandler: CreateHouseholdHandler by dependencies
    val inviteMemberHandler: InviteMemberHandler by dependencies
    val revokeMembershipHandler: RevokeMembershipHandler by dependencies

    val searchCatalogItemsHandler: SearchCatalogItemsHandler by dependencies
    val findCatalogItemByIdHandler: FindCatalogItemByIdHandler by dependencies
    val registerCatalogItemHandler: RegisterCatalogItemHandler by dependencies
    val reviseCatalogItemHandler: ReviseCatalogItemHandler by dependencies

    val listProductsOfHouseholdHandler: ListProductsOfHouseholdHandler by dependencies
    val findProductHandler: FindProductHandler by dependencies
    val adoptProductHandler: AdoptProductHandler by dependencies
    val setMinimumStockHandler: SetMinimumStockHandler by dependencies
    val archiveProductHandler: ArchiveProductHandler by dependencies

    val getStockHandler: GetStockHandler by dependencies
    val listStocksHandler: ListStocksHandler by dependencies
    val getMovementHistoryHandler: GetMovementHistoryHandler by dependencies
    val replenishStockHandler: ReplenishStockHandler by dependencies
    val consumeStockHandler: ConsumeStockHandler by dependencies

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
                        findHouseholdOfUserHandler,
                        createHouseholdHandler,
                        inviteMemberHandler,
                        revokeMembershipHandler,
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
                        searchCatalogItemsHandler,
                        findCatalogItemByIdHandler,
                        registerCatalogItemHandler,
                        reviseCatalogItemHandler,
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
                        listProductsOfHouseholdHandler,
                        findProductHandler,
                        adoptProductHandler,
                        setMinimumStockHandler,
                        archiveProductHandler,
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
                        getStockHandler,
                        listStocksHandler,
                        getMovementHistoryHandler,
                        replenishStockHandler,
                        consumeStockHandler,
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
