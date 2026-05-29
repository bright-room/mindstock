@file:OptIn(ExperimentalSerializationApi::class)

package net.brightroom.mindstock.configuration.routing

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.configuration.auth.MindstockAuthPlugin
import net.brightroom.mindstock.configuration.auth.MindstockSessionKey
import net.brightroom.mindstock.configuration.auth.RequireRegisteredUserPlugin
import net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin
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
import org.jetbrains.exposed.v1.jdbc.Database
import java.net.URL
import java.util.concurrent.TimeUnit

fun Application.routingConfigure() {
    install(ContentNegotiation) {
        jsonIo(CustomJson)
    }
    install(Krpc) {
        serialization { json(KrpcJson) }
    }
    install(WsSubprotocolEchoPlugin)

    val cfg = environment.config.config("external.auth")
    val authIssuer = cfg.property("issuer").getString()
    val authAudience = cfg.property("audience").getString()
    val jwksUrl = cfg.property("jwks-url").getString()

    val userRepository: UserRepository by dependencies
    val database: Database by dependencies

    install(MindstockAuthPlugin) {
        jwkProvider =
            JwkProviderBuilder(URL(jwksUrl))
                .cached(10, 1, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()
        issuer = authIssuer
        audience = authAudience
        this.userRepository = userRepository
        this.database = database
    }

    val userPublicFactory: UserPublicControllerFactory by dependencies
    val userFactory: UserControllerFactory by dependencies
    val householdFactory: HouseholdControllerFactory by dependencies
    val catalogFactory: CatalogControllerFactory by dependencies
    val productFactory: ProductControllerFactory by dependencies
    val stockFactory: StockControllerFactory by dependencies

    routing {
        route("/api/v1") {
            // JWT 有効ならよい(未登録 OK)
            route("/user/public") {
                rpc {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<UserPublicRpcService> { userPublicFactory.create(session) }
                }
            }
            // 登録済み User 必須
            route("/") {
                install(RequireRegisteredUserPlugin)

                rpc("/user") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<UserRpcService> { userFactory.create(session) }
                }
                rpc("/household") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<HouseholdRpcService> { householdFactory.create(session) }
                }
                rpc("/catalog") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<CatalogRpcService> { catalogFactory.create(session) }
                }
                rpc("/product") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<ProductRpcService> { productFactory.create(session) }
                }
                rpc("/stock") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<StockRpcService> { stockFactory.create(session) }
                }
            }
        }
    }
}
