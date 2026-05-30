package net.brightroom.mindstock.test

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

/**
 * テスト用 DataSource。
 *
 * 接続先: `TEST_DB_URL` (default `jdbc:postgresql://localhost:5432/mindstock_test`)
 * 認証 : `TEST_DB_USER` / `TEST_DB_PASSWORD` (default `mindstock` / `mindstock`)
 *
 * Postgres は呼び出し側で起動済みであることが前提:
 *   - local: `docker compose up -d postgres`
 *   - CI: GHA `services:` の postgres
 *
 * 接続不可能なら接続検証で例外。テストは skip せずに fail させる。
 */
object TestDataSource {
    val url: String get() = System.getenv("TEST_DB_URL") ?: "jdbc:postgresql://localhost:5432/mindstock_test"
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
                // 単発 DDL(CREATE/DROP SCHEMA)を流すだけなので 1 接続で足りる。
                // テスト並列・連続実行時の Postgres 接続枯渇を避けるためキャップする。
                maximumPoolSize = 1
                isAutoCommit = false
            }
        val ds = HikariDataSource(config)
        ds.connection.use { it.isValid(2) }
        return ds
    }

    /**
     * Runs [block] against a fresh schema. Creates a new schema with a
     * random name, sets the session search_path to it, runs the block,
     * and drops the schema afterward.
     */
    fun <T> withFreshSchema(block: (jdbcUrl: String, schema: String) -> T): T {
        val schema =
            "test_" +
                java.util.UUID
                    .randomUUID()
                    .toString()
                    .replace("-", "")
                    .take(16)

        // Use root DS to create/drop the schema itself
        create().use { ds ->
            ds.connection.use { conn ->
                conn.autoCommit = true
                conn.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            }
        }
        try {
            val urlWithSchema =
                buildString {
                    append(url)
                    if ('?' in url) append("&") else append("?")
                    append("currentSchema=$schema")
                }
            return block(urlWithSchema, schema)
        } finally {
            create().use { ds ->
                ds.connection.use { conn ->
                    conn.autoCommit = true
                    conn.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
                }
            }
        }
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

/**
 * テスト用 HikariDataSource を生成するユーティリティ。
 */
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
            // Flyway は PostgreSQL のセッションロック用に 1 接続を保持したまま
            // マイグレーション実行用にもう 1 接続を要求するため、最低 2 必要
            // (pool=1 だと self-deadlock で 30s タイムアウトする)。
            // default の 10 は接続枯渇の主因なので、必要最小限の 2 に絞る。
            maximumPoolSize = 2
            minimumIdle = 2
        }
    return HikariDataSource(config)
}
