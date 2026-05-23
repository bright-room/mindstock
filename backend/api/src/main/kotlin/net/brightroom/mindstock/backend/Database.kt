package net.brightroom.mindstock.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database

@Serializable
data class ExposedDataSourceProperties(
    @SerialName("driver-class-name") val driverClassName: String,
    @SerialName("jdbc-url") val jdbcUrl: String,
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("maximum-pool-size") val maximumPoolSize: Int = 10,
    @SerialName("auto-commit") val autoCommit: Boolean = false,
    @SerialName("transaction-isolation") val transactionIsolation: String = "TRANSACTION_REPEATABLE_READ",
) {
    override fun toString(): String =
        "ExposedDataSourceProperties(driverClassName=$driverClassName, jdbcUrl=$jdbcUrl, " +
            "username=$username, password=***, maximumPoolSize=$maximumPoolSize, " +
            "autoCommit=$autoCommit, transactionIsolation=$transactionIsolation)"
}

fun buildHikariDataSource(properties: ExposedDataSourceProperties): HikariDataSource {
    val hikari =
        HikariConfig().apply {
            driverClassName = properties.driverClassName
            jdbcUrl = properties.jdbcUrl
            username = properties.username
            password = properties.password
            maximumPoolSize = properties.maximumPoolSize
            isAutoCommit = properties.autoCommit
            transactionIsolation = properties.transactionIsolation
        }
    return HikariDataSource(hikari)
}

fun connectExposed(dataSource: HikariDataSource): Database = Database.connect(dataSource)
