package net.brightroom.mindstock.infrastructure.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import net.brightroom.mindstock.infrastructure.TestContainersPostgres
import java.sql.SQLException

class AppendOnlyEnforcementTest : FunSpec({
    test("mindstock_app role can INSERT but cannot UPDATE or DELETE") {
        TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
            val ds = DatabaseFactory.dataSource(
                DatabaseConfig(
                    jdbcUrl = jdbcUrl,
                    username = TestContainersPostgres.username,
                    password = TestContainersPostgres.password,
                ),
            )
            MigrationRunner.migrate(ds)

            // Switch to app role for the duration of this test
            ds.connection.use { conn ->
                conn.createStatement().use { it.execute("SET ROLE mindstock_app") }

                // INSERT works
                conn.prepareStatement(
                    "INSERT INTO users (zitadel_sub) VALUES (?) RETURNING id",
                ).use { stmt ->
                    stmt.setString(1, "test-sub")
                    val rs = stmt.executeQuery()
                    check(rs.next())
                }

                // UPDATE is denied
                shouldThrow<SQLException> {
                    conn.createStatement().use { it.execute("UPDATE users SET zitadel_sub = 'x'") }
                }
                // DELETE is denied
                shouldThrow<SQLException> {
                    conn.createStatement().use { it.execute("DELETE FROM users") }
                }
            }
            ds.close()
        }
    }
})
