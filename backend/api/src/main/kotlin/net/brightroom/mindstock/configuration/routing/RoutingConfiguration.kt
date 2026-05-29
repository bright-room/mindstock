@file:OptIn(ExperimentalSerializationApi::class)

package net.brightroom.mindstock.configuration.routing

import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin
import net.brightroom.mindstock.configuration.auth.applicationCall
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.presentation.rpc.catalog.CatalogControllerFactory
import net.brightroom.mindstock.presentation.rpc.household.HouseholdControllerFactory
import net.brightroom.mindstock.presentation.rpc.product.ProductControllerFactory
import net.brightroom.mindstock.presentation.rpc.stock.StockControllerFactory
import net.brightroom.mindstock.presentation.rpc.user.UserControllerFactory
import net.brightroom.mindstock.presentation.rpc.user.UserPublicControllerFactory
import net.brightroom.mindstock.rpc.CatalogRpcService
import net.brightroom.mindstock.rpc.HouseholdRpcService
import net.brightroom.mindstock.rpc.ProductRpcService
import net.brightroom.mindstock.rpc.StockRpcService
import net.brightroom.mindstock.rpc.UserPublicRpcService
import net.brightroom.mindstock.rpc.UserRpcService

fun Application.routingConfigure() {
    install(ContentNegotiation) {
        jsonIo(CustomJson)
    }
    install(Krpc) {
        serialization { json(KrpcJson) }
    }
    install(WsSubprotocolEchoPlugin)

    val userPublicFactory: UserPublicControllerFactory by dependencies
    val userFactory: UserControllerFactory by dependencies
    val householdFactory: HouseholdControllerFactory by dependencies
    val catalogFactory: CatalogControllerFactory by dependencies
    val productFactory: ProductControllerFactory by dependencies
    val stockFactory: StockControllerFactory by dependencies

    routing {
        authenticate("user-public") {
            rpc("/api/v1/user/public") {
                registerService<UserPublicRpcService> { userPublicFactory.create(applicationCall) }
            }
        }
        authenticate("user") {
            rpc("/api/v1/user") {
                registerService<UserRpcService> { userFactory.create(applicationCall) }
            }
            rpc("/api/v1/household") {
                registerService<HouseholdRpcService> { householdFactory.create(applicationCall) }
            }
            rpc("/api/v1/catalog") {
                registerService<CatalogRpcService> { catalogFactory.create(applicationCall) }
            }
            rpc("/api/v1/product") {
                registerService<ProductRpcService> { productFactory.create(applicationCall) }
            }
            rpc("/api/v1/stock") {
                registerService<StockRpcService> { stockFactory.create(applicationCall) }
            }
        }
    }
}
