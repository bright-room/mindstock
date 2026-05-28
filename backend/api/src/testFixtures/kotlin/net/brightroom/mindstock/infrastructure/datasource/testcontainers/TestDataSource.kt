package net.brightroom.mindstock.infrastructure.datasource.testcontainers

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

fun testHikariDataSource(
    jdbcUrl: String,
    username: String,
    password: String,
): HikariDataSource {
    val config =
        HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
        }
    return HikariDataSource(config)
}
