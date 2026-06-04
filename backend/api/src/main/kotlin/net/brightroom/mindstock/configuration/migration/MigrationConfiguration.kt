package net.brightroom.mindstock.configuration.migration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.annotations.Property
import net.brightroom.mindstock.configuration.external.exposed.ExposedDataSourceProperties
import org.flywaydb.core.Flyway

fun Application.migrationConfigure(
    @Property("external.datasource.database") properties: ExposedDataSourceProperties,
) {
    val hikariConfig =
        HikariConfig().apply {
            driverClassName = properties.driverClassName
            jdbcUrl = properties.jdbcUrl
            username = properties.username
            password = properties.password
            maximumPoolSize = 2
            isAutoCommit = false
        }

    HikariDataSource(hikariConfig).use { dataSource ->
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
