package net.brightroom.mindstock.infrastructure.datasource.repository

import net.brightroom.mindstock.infrastructure.migration.executor.MigrationRunner
import net.brightroom.mindstock.infrastructure.migration.executor.TestContainersPostgres
import net.brightroom.mindstock.infrastructure.migration.executor.testHikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Repository 結合テスト用のヘルパー。
 *
 * fresh schema を立て、Flyway migrate を流し、Exposed `Database` を渡して [block] を実行する。
 * block 内は `tx { ... }` で囲んで Repository を呼ぶこと(Exposed のクエリは transaction 内でのみ動作する)。
 */
fun withRepositoryTestContext(block: RepositoryTestContext.() -> Unit) {
    TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
        val dataSource =
            testHikariDataSource(
                jdbcUrl,
                TestContainersPostgres.username,
                TestContainersPostgres.password,
            )
        try {
            MigrationRunner.migrate(dataSource)
            val database = Database.connect(dataSource)
            RepositoryTestContext(database).block()
        } finally {
            dataSource.close()
        }
    }
}

class RepositoryTestContext(
    val database: Database,
) {
    /** Repository コードを transaction 境界内で実行するショートカット。 */
    fun <T> tx(block: () -> T): T = transaction(database) { block() }
}
