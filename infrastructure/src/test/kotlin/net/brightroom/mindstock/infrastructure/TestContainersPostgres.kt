package net.brightroom.mindstock.infrastructure

import org.testcontainers.containers.PostgreSQLContainer

/**
 * A lazily-initialised PostgreSQL 18 container shared across integration
 * tests. Each test should connect to a unique database created via
 * [withFreshDatabase] so they don't see each other's schemas.
 */
object TestContainersPostgres {
    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:18").apply {
            withDatabaseName("mindstock_test")
            withUsername("mindstock")
            withPassword("mindstock")
            start()
        }
    }

    val jdbcUrl: String get() = container.jdbcUrl
    val username: String get() = container.username
    val password: String get() = container.password

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
        container.createConnection("").use { conn ->
            conn.createStatement().use { it.execute("CREATE SCHEMA $schema") }
        }
        try {
            val urlWithSchema = container.jdbcUrl + "&currentSchema=$schema"
            return block(urlWithSchema, schema)
        } finally {
            container.createConnection("").use { conn ->
                conn.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }
}
