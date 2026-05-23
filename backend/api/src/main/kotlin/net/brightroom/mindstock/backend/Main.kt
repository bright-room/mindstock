package net.brightroom.mindstock.backend

import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.cio.EngineMain
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.brightroom.mindstock.infrastructure.persistence.MigrationRunner

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    install(DI)

    val appConfig = environment.config
    dependencies {
        provide<ExposedDataSourceProperties> {
            val cfg = appConfig.config("external.datasource.database")
            ExposedDataSourceProperties(
                driverClassName = cfg.property("driver-class-name").getString(),
                jdbcUrl = cfg.property("jdbc-url").getString(),
                username = cfg.property("username").getString(),
                password = cfg.property("password").getString(),
                maximumPoolSize = cfg.propertyOrNull("maximum-pool-size")?.getString()?.toInt() ?: 10,
                autoCommit = cfg.propertyOrNull("auto-commit")?.getString()?.toBoolean() ?: false,
                transactionIsolation =
                    cfg.propertyOrNull("transaction-isolation")?.getString()
                        ?: "TRANSACTION_REPEATABLE_READ",
            )
        }
    }

    exposedConfigure()

    routing {
        get("/health") {
            call.respondText("OK")
        }
    }
}

fun Application.exposedConfigure() {
    val properties: ExposedDataSourceProperties by dependencies
    val dataSource: HikariDataSource = buildHikariDataSource(properties)
    MigrationRunner.migrate(dataSource)
    connectExposed(dataSource)

    monitor.subscribe(ApplicationStopped) {
        dataSource.close()
    }
}
