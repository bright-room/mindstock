package net.brightroom.mindstock.configuration.external.exposed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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