package net.brightroom.mindstock.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

// Temporary shim: ExposedDataSourceProperties and buildHikariDataSource were
// moved to :backend:api in Task 3. Adding a testImplementation dependency in
// the opposite direction would create a module cycle, so we inline a minimal
// Hikari factory here. Task 8 will move these tests to :backend:infrastructure:migration:executor,
// at which point this file can be deleted.
internal fun testHikariDataSource(
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
