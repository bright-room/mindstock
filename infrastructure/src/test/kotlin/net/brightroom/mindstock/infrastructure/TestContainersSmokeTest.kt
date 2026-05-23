package net.brightroom.mindstock.infrastructure

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TestContainersSmokeTest :
    FunSpec({
        test("the test postgres container reports server_version_num for PG 18") {
            TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
                val user = TestContainersPostgres.username
                val pw = TestContainersPostgres.password
                java.sql.DriverManager.getConnection(jdbcUrl, user, pw).use { conn ->
                    conn.createStatement().use { stmt ->
                        val rs = stmt.executeQuery("SHOW server_version_num")
                        rs.next()
                        val versionNum = rs.getInt(1)
                        (versionNum >= 180000) shouldBe true
                    }
                }
            }
        }
    })
