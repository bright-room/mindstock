package net.brightroom.mindstock.infrastructure.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import net.brightroom.mindstock.infrastructure.TestContainersPostgres
import javax.sql.DataSource

class MigrationRunnerTest :
    FunSpec({
        test("migrate creates every table in MigratableTables") {
            TestContainersPostgres.withFreshSchema { jdbcUrl, schema ->
                testHikariDataSource(
                    jdbcUrl = jdbcUrl,
                    username = TestContainersPostgres.username,
                    password = TestContainersPostgres.password,
                ).use { ds ->
                    MigrationRunner.migrate(ds)
                    val actual = listTables(ds, schema)
                    val expected = MigratableTables.all.map { it.tableName }
                    actual shouldContainAll expected
                }
            }
        }
    })

private fun listTables(
    ds: DataSource,
    schema: String,
): List<String> =
    ds.connection.use { conn ->
        conn
            .prepareStatement(
                "SELECT tablename FROM pg_tables WHERE schemaname = ? ORDER BY tablename",
            ).apply { setString(1, schema) }
            .use { stmt ->
                val rs = stmt.executeQuery()
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
    }
