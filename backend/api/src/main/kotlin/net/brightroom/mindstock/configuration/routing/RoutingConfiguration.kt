@file:OptIn(ExperimentalSerializationApi::class)

package net.brightroom.mindstock.configuration.routing

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.scenario.household.JoinHouseholdScenario
import net.brightroom.mindstock.application.scenario.invitation.CreateInvitationScenario
import net.brightroom.mindstock.application.scenario.invitation.RevokeInvitationScenario
import net.brightroom.mindstock.application.scenario.product.AdoptProductScenario
import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockAuthPlugin
import net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin
import net.brightroom.mindstock.configuration.auth.sessionOf
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.presentation.rpc.catalog.CatalogController
import net.brightroom.mindstock.presentation.rpc.household.HouseholdController
import net.brightroom.mindstock.presentation.rpc.household.HouseholdRegisterController
import net.brightroom.mindstock.presentation.rpc.product.ProductController
import net.brightroom.mindstock.presentation.rpc.product.ProductRegisterController
import net.brightroom.mindstock.presentation.rpc.resident.ResidentRegisterController
import net.brightroom.mindstock.presentation.rpc.session.SessionController
import net.brightroom.mindstock.presentation.rpc.stock.StockController
import net.brightroom.mindstock.presentation.rpc.stock.StockRegisterController
import net.brightroom.mindstock.rpc.catalog.CatalogRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import net.brightroom.mindstock.rpc.session.SessionRpcService
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService
import net.brightroom.mindstock.rpc.stock.StockRpcService
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.routingConfigure() {
    install(ContentNegotiation) { jsonIo(CustomJson) }
    install(Krpc) { serialization { json(KrpcJson) } }
    install(WsSubprotocolEchoPlugin)

    val authConfig = environment.config.config("external.auth")
    val residentRepository: ResidentRepository by dependencies

    install(MindstockAuthPlugin) {
        jwkProvider =
            JwkProviderBuilder(URI(authConfig.property("jwks-url").getString()).toURL())
                .cached(10, 1, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()
        issuer = authConfig.property("issuer").getString()
        audience = authConfig.property("audience").getString()
        this.residentRepository = residentRepository
    }

    // service / scenario を先取り解決(registerService factory は非 suspend のため)
    val residentService: ResidentService by dependencies
    val residentRegisterService: ResidentRegisterService by dependencies
    val catalogService: CatalogService by dependencies
    val householdService: HouseholdService by dependencies
    val householdRegisterService: HouseholdRegisterService by dependencies
    val invitationService: InvitationService by dependencies
    val productService: ProductService by dependencies
    val productRegisterService: ProductRegisterService by dependencies
    val stockService: StockService by dependencies
    val stockRegisterService: StockRegisterService by dependencies
    val adoptProductScenario: AdoptProductScenario by dependencies
    val createInvitationScenario: CreateInvitationScenario by dependencies
    val revokeInvitationScenario: RevokeInvitationScenario by dependencies
    val joinHouseholdScenario: JoinHouseholdScenario by dependencies

    routing {
        // 認証必須の単一エンドポイント。全サービスを 1 接続に相乗りする。
        // 登録要否はルートで分けず、各 Controller が requireRegistered/allowUnregistered で表明する。
        rpc("/api/rpc") {
            registerService<SessionRpcService> { SessionController(residentService, sessionOf(call)) }
            registerService<ResidentRegisterRpcService> {
                ResidentRegisterController(residentRegisterService, sessionOf(call))
            }
            registerService<CatalogRpcService> { CatalogController(catalogService, sessionOf(call)) }
            registerService<HouseholdRpcService> {
                HouseholdController(householdService, invitationService, sessionOf(call))
            }
            registerService<HouseholdRegisterRpcService> {
                HouseholdRegisterController(
                    householdRegisterService,
                    createInvitationScenario,
                    revokeInvitationScenario,
                    joinHouseholdScenario,
                    sessionOf(call),
                )
            }
            registerService<ProductRpcService> { ProductController(productService, sessionOf(call)) }
            registerService<ProductRegisterRpcService> {
                ProductRegisterController(productRegisterService, adoptProductScenario, sessionOf(call))
            }
            registerService<StockRpcService> { StockController(stockService, sessionOf(call)) }
            registerService<StockRegisterRpcService> {
                StockRegisterController(stockRegisterService, sessionOf(call))
            }
        }
    }
}
