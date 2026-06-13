package net.brightroom.mindstock.configuration.di

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import net.brightroom.mindstock.application.repository.catalog.CatalogRegisterRepository
import net.brightroom.mindstock.application.repository.catalog.CatalogRepository
import net.brightroom.mindstock.application.repository.catalog.ExternalProductRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.invitation.InvitationRegisterRepository
import net.brightroom.mindstock.application.repository.invitation.InvitationRepository
import net.brightroom.mindstock.application.repository.product.ProductImageStorageRepository
import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRegisterRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.application.scenario.household.JoinHouseholdScenario
import net.brightroom.mindstock.application.scenario.invitation.CreateInvitationScenario
import net.brightroom.mindstock.application.scenario.invitation.RevokeInvitationScenario
import net.brightroom.mindstock.application.scenario.product.AdoptProductScenario
import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationRegisterService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.external.storage.StorageBucket
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogDataSource
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.invitation.InvitationDataSource
import net.brightroom.mindstock.infrastructure.datasource.invitation.InvitationRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.stock.StockDataSource
import net.brightroom.mindstock.infrastructure.datasource.stock.StockRegisterDataSource
import net.brightroom.mindstock.infrastructure.receive.catalog.UnconfiguredProductReceive
import net.brightroom.mindstock.infrastructure.transfer.product.ProductImageTransfer

fun Application.dependenciesConfigure() {
    dependencies {
        // Repository(DataSource は Database を注入)
        provide<ResidentRepository> { ResidentDataSource(resolve()) }
        provide<ResidentRegisterRepository> { ResidentRegisterDataSource(resolve()) }
        provide<CatalogRepository> { CatalogDataSource(resolve()) }
        provide<CatalogRegisterRepository> { CatalogRegisterDataSource(resolve()) }
        provide<HouseholdRepository> { HouseholdDataSource(resolve()) }
        provide<HouseholdRegisterRepository> { HouseholdRegisterDataSource(resolve()) }
        provide<InvitationRepository> { InvitationDataSource(resolve()) }
        provide<InvitationRegisterRepository> { InvitationRegisterDataSource(resolve()) }
        provide<ProductRepository> { ProductDataSource(resolve()) }
        provide<ProductRegisterRepository> { ProductRegisterDataSource(resolve()) }
        provide<StockRepository> { StockDataSource(resolve(), resolve()) }
        provide<StockRegisterRepository> { StockRegisterDataSource(resolve()) }
        provide<ProductImageStorageRepository> {
            ProductImageTransfer(resolve(), resolve<StorageBucket>().name)
        }

        // 外部商品 API(受信 = master 未存在の補完)
        provide<ExternalProductRepository> { UnconfiguredProductReceive() }

        // Service
        provide<ResidentService> { ResidentService(resolve()) }
        provide<ResidentRegisterService> { ResidentRegisterService(resolve()) }
        provide<CatalogService> { CatalogService(resolve(), resolve(), resolve()) }
        provide<HouseholdService> { HouseholdService(resolve()) }
        provide<HouseholdRegisterService> { HouseholdRegisterService(resolve(), resolve(), resolve()) }
        provide<InvitationService> { InvitationService(resolve()) }
        provide<InvitationRegisterService> { InvitationRegisterService(resolve()) }
        provide<ProductService> { ProductService(resolve(), resolve(), resolve(), resolve()) }
        provide<ProductRegisterService> { ProductRegisterService(resolve(), resolve(), resolve(), resolve(), resolve()) }
        provide<StockService> { StockService(resolve(), resolve(), resolve()) }
        provide<StockRegisterService> { StockRegisterService(resolve(), resolve(), resolve(), resolve(), resolve(), resolve()) }

        // Scenario
        provide<AdoptProductScenario> { AdoptProductScenario(resolve(), resolve()) }
        provide<JoinHouseholdScenario> { JoinHouseholdScenario(resolve(), resolve(), resolve()) }
        provide<CreateInvitationScenario> { CreateInvitationScenario(resolve(), resolve()) }
        provide<RevokeInvitationScenario> { RevokeInvitationScenario(resolve(), resolve(), resolve()) }
    }
}
