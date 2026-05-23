package net.brightroom.mindstock.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int = 10,
)

object DatabaseFactory {
    fun dataSource(config: DatabaseConfig): HikariDataSource {
        val hikari =
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.username
                password = config.password
                maximumPoolSize = config.maximumPoolSize
                isAutoCommit = false
                driverClassName = "org.postgresql.Driver"
            }
        return HikariDataSource(hikari)
    }

    fun exposed(dataSource: DataSource): Database = Database.connect(dataSource)
}
