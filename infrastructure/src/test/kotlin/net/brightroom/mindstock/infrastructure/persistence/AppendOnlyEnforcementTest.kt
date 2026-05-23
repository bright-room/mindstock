package net.brightroom.mindstock.infrastructure.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import net.brightroom.mindstock.infrastructure.TestContainersPostgres
import java.sql.SQLException

class AppendOnlyEnforcementTest :
    FunSpec({
        test("mindstock_app role can INSERT but cannot UPDATE or DELETE") {
            TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
                testHikariDataSource(
                    jdbcUrl = jdbcUrl,
                    username = TestContainersPostgres.username,
                    password = TestContainersPostgres.password,
                ).use { ds ->
                    MigrationRunner.migrate(ds)

                    // Switch to app role for the duration of this test
                    ds.connection.use { conn ->
                        conn.createStatement().use { it.execute("SET ROLE mindstock_app") }

                        // INSERT works
                        conn
                            .prepareStatement(
                                "INSERT INTO users (zitadel_sub) VALUES (?) RETURNING id",
                            ).use { stmt ->
                                stmt.setString(1, "test-sub")
                                val rs = stmt.executeQuery()
                                check(rs.next())
                            }

                        // History tables use BIGSERIAL — exercise sequence USAGE
                        val userId =
                            conn
                                .prepareStatement(
                                    "SELECT id FROM users WHERE zitadel_sub = ?",
                                ).use { stmt ->
                                    stmt.setString(1, "test-sub")
                                    stmt.executeQuery().use { rs ->
                                        check(rs.next())
                                        rs.getObject(1, java.util.UUID::class.java)
                                    }
                                }
                        conn
                            .prepareStatement(
                                "INSERT INTO user_display_names (user_id, display_name) VALUES (?, ?)",
                            ).use { stmt ->
                                stmt.setObject(1, userId)
                                stmt.setString(2, "test display name")
                                stmt.executeUpdate()
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
                }
            }
        }
    })
