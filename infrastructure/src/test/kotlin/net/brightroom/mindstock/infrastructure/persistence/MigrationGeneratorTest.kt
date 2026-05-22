package net.brightroom.mindstock.infrastructure.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import net.brightroom.mindstock.infrastructure.TestContainersPostgres
import org.jetbrains.exposed.v1.jdbc.Database
import java.io.File

class MigrationGeneratorTest : FunSpec({
    test("generator emits a migration script when the target schema is empty") {
        TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
            val db = Database.connect(jdbcUrl, "org.postgresql.Driver",
                TestContainersPostgres.username, TestContainersPostgres.password)
            val tempDir = createTempDirectory()
            val script = MigrationGenerator.generate(db, tempDir, "test_init")
            script shouldNotBe null
            (script!!.readText().contains("CREATE TABLE", ignoreCase = true)) shouldBe true
        }
    }

    test("generator emits nothing when the schema already matches") {
        // Second generation against a database where all Migratable tables exist
        // would yield an empty script; we don't test that here yet — covered
        // once Task 13 wires the applier in.
    }
})

private fun createTempDirectory(): File = File.createTempFile("mindstock-migrations-", "").apply {
    delete()
    mkdir()
    deleteOnExit()
}
