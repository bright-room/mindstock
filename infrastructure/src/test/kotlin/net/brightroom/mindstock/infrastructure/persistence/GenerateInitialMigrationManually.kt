package net.brightroom.mindstock.infrastructure.persistence

import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import net.brightroom.mindstock.infrastructure.TestContainersPostgres
import org.jetbrains.exposed.v1.jdbc.Database
import java.io.File

/**
 * Run with:
 * ./gradlew :infrastructure:test --tests "*GenerateInitialMigrationManually" \
 *   -Pkotest.framework.runtimeTags=manual
 *
 * Writes a fresh init.sql under src/main/resources/db/migration/.
 *
 * @Ignored prevents accidental runs in normal CI.
 */
@Ignored
class GenerateInitialMigrationManually :
    FunSpec({
        test("write init migration to resources") {
            val outDir = File("src/main/resources/db/migration").absoluteFile
            outDir.mkdirs()
            TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
                val db =
                    Database.connect(
                        jdbcUrl,
                        "org.postgresql.Driver",
                        TestContainersPostgres.username,
                        TestContainersPostgres.password,
                    )
                val script = MigrationGenerator.generate(db, outDir, "init")
                requireNotNull(script) { "Generator emitted no script — schema must already match" }
                println("Wrote ${script.absolutePath} (${script.length()} bytes)")
            }
        }
    })
