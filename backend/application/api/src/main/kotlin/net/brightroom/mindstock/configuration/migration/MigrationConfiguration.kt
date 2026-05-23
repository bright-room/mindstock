package net.brightroom.mindstock.configuration.migration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.annotations.Property
import net.brightroom.mindstock.configuration.external.exposed.ExposedDataSourceProperties
import net.brightroom.mindstock.infrastructure.migration.executor.MigrationRunner

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
        MigrationRunner.migrate(dataSource)
    }
}
