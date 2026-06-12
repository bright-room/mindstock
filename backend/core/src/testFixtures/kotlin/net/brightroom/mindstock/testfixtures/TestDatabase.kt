package net.brightroom.mindstock.testfixtures

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * DataSource(DB)統合テスト用の共有 DB ハンドル。
 *
 * testcontainers は使わず、mise が用意する live `mindstock_test`(空 DB)に当てる。
 * 初回アクセスで Flyway が `classpath:db/migration`(V1__init.sql)を流してスキーマを作る。
 * 各テストの先頭で [clean] を呼び、アプリ全テーブルを TRUNCATE して独立させる。
 */
object TestDatabase {
    // 接続先は postgres-init.sh が作る固定フィクスチャ mindstock_test(ローカル docker compose / CI の
    // postgres サービスとも同一)。テスト専用 DB なので接続情報は「設定」ではなく定数で持つ(env 不要)。
    private const val URL = "jdbc:postgresql://localhost:5432/mindstock_test"
    private const val USER = "mindstock"
    private const val PASSWORD = "mindstock"

    // V1__init.sql 時点の全 15 アプリテーブル。migration でテーブルを追加したらここも更新すること
    // (追従漏れは clean() が不完全になりテストの相互汚染を招く)。
    private val truncateSql =
        """
        TRUNCATE TABLE
            stock_movements,
            product_revisions,
            product_wanted_events,
            product_barcodes,
            product_catalog_links,
            products,
            catalog_items,
            invitation_validity_events,
            invitations,
            household_membership_events,
            household_names,
            households,
            resident_display_names,
            resident_auth_identities,
            residents
        RESTART IDENTITY CASCADE
        """.trimIndent()

    val database: Database by lazy {
        val dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    driverClassName = "org.postgresql.Driver"
                    jdbcUrl = URL
                    username = USER
                    password = PASSWORD
                    maximumPoolSize = 4
                    isAutoCommit = false
                },
            )
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        Database.connect(
            datasource = dataSource,
            databaseConfig = DatabaseConfig.invoke { useNestedTransactions = true },
        )
    }

    /** アプリ全テーブルを空にする(flyway_schema_history は残す)。各テスト先頭で呼ぶ。 */
    fun clean() {
        transaction(database) {
            exec(truncateSql)
        }
    }
}
