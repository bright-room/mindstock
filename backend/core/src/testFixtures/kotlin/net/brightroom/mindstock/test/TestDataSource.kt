package net.brightroom.mindstock.test

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

/**
 * テスト用 DataSource。
 *
 * 接続先: `TEST_DB_URL` (default `jdbc:postgresql://localhost:5433/mindstock_test`)
 * 認証 : `TEST_DB_USER` / `TEST_DB_PASSWORD` (default `mindstock` / `mindstock`)
 *
 * Postgres は呼び出し側で起動済みであることが前提:
 *   - local: `docker compose up -d postgres-test`
 *   - CI: GHA `services:` の postgres
 *
 * 接続不可能なら接続検証で例外。テストは skip せずに fail させる。
 */
object TestDataSource {
    val url: String get() = System.getenv("TEST_DB_URL") ?: "jdbc:postgresql://localhost:5433/mindstock_test"
    val user: String get() = System.getenv("TEST_DB_USER") ?: "mindstock"
    val password: String get() = System.getenv("TEST_DB_PASSWORD") ?: "mindstock"

    fun create(): HikariDataSource {
        val dbUrl = url
        val dbUser = user
        val dbPassword = password
        val config =
            HikariConfig().apply {
                driverClassName = "org.postgresql.Driver"
                jdbcUrl = dbUrl
                username = dbUser
                password = dbPassword
                maximumPoolSize = 4
                isAutoCommit = false
            }
        val ds = HikariDataSource(config)
        ds.connection.use { it.isValid(2) }
        return ds
    }

    /** Flyway clean + migrate。テスト前に呼ぶ。 */
    fun migrate(dataSource: DataSource) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()
            .also { it.clean() }
            .migrate()
    }
}
