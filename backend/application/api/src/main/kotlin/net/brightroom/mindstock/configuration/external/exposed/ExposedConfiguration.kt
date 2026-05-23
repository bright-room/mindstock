package net.brightroom.mindstock.configuration.external.exposed

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.plugins.di.dependencies
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.exposedConfigure(
    @Property("external.datasource.database") properties: ExposedDataSourceProperties,
) {
    val hikariConfig =
        HikariConfig().apply {
            driverClassName = properties.driverClassName
            jdbcUrl = properties.jdbcUrl
            username = properties.username
            password = properties.password
            maximumPoolSize = properties.maximumPoolSize
            isAutoCommit = properties.autoCommit
            transactionIsolation = properties.transactionIsolation
        }

    val dataSource = HikariDataSource(hikariConfig)

    monitor.subscribe(ApplicationStopped) {
        dataSource.close()
    }

    dependencies {
        provide<Database> {
            Database.connect(
                datasource = dataSource,
                databaseConfig = DatabaseConfig.invoke { useNestedTransactions = true },
            )
        }
    }
}
