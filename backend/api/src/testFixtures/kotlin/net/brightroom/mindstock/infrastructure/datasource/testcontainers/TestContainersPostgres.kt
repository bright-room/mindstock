package net.brightroom.mindstock.infrastructure.datasource.testcontainers

import net.brightroom.mindstock.test.TestDataSource

/**
 * テスト用 Postgres ヘルパー。
 *
 * 以前は Testcontainers でコンテナを起動していたが、外部 Postgres に切り替えた。
 * 接続先は [TestDataSource] が管理し、`TEST_DB_URL` 環境変数で上書き可能。
 *
 * [withFreshSchema] はスキーマ分離を維持する:
 *   - テストごとに UUID ベースのスキーマを作成
 *   - `currentSchema=<schema>` を JDBC URL に付与して分離を保証
 *   - テスト終了後にスキーマを DROP
 *
 * Postgres は呼び出し側で起動済みであることが前提:
 *   - local: `docker compose up -d postgres-test`
 *   - CI: GHA `services:` の postgres
 */
object TestContainersPostgres {
    val jdbcUrl: String get() = TestDataSource.url
    val username: String get() = TestDataSource.user
    val password: String get() = TestDataSource.password

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
        val rootDs = TestDataSource.create()
        rootDs.use { ds ->
            ds.connection.use { conn ->
                conn.autoCommit = true
                conn.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            }
        }
        try {
            val urlWithSchema =
                buildString {
                    append(TestDataSource.url)
                    if ('?' in TestDataSource.url) append("&") else append("?")
                    append("currentSchema=$schema")
                }
            return block(urlWithSchema, schema)
        } finally {
            val rootDs2 = TestDataSource.create()
            rootDs2.use { ds ->
                ds.connection.use { conn ->
                    conn.autoCommit = true
                    conn.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
                }
            }
        }
    }
}
