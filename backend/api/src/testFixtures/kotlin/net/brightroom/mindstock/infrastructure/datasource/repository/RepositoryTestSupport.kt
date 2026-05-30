package net.brightroom.mindstock.infrastructure.datasource.repository

import net.brightroom.mindstock.test.TestDataSource
import net.brightroom.mindstock.test.testHikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Repository 結合テスト用のヘルパー。
 *
 * fresh schema を立て、Flyway migrate を流し、Exposed `Database` を渡して [block] を実行する。
 * DataSource は自分で transaction を張る suspend メソッドになったため、Repository 呼び出しは
 * `runBlocking { repo.foo() }` で行う(DataSource のコンストラクタに [RepositoryTestContext.database] を渡す)。
 * `tx { ... }` は生 Exposed クエリ(テストデータの seed 等)を transaction 境界内で実行する用途に限定する。
 */
fun withRepositoryTestContext(block: RepositoryTestContext.() -> Unit) {
    TestDataSource.withFreshSchema { jdbcUrl, _ ->
        val dataSource =
            testHikariDataSource(
                jdbcUrl,
                TestDataSource.user,
                TestDataSource.password,
            )
        try {
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
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
    /** 生 Exposed クエリ(テストデータ seed 等)を transaction 境界内で実行するショートカット。 */
    fun <T> tx(block: () -> T): T = transaction(database) { block() }
}
