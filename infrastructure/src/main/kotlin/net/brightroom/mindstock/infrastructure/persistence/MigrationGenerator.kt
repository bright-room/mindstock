package net.brightroom.mindstock.infrastructure.persistence

import net.brightroom.mindstock.infrastructure.migration.detector.MigratableTables
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.MigrationUtils
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Generates a Flyway-format migration script that brings the connected
 * database in sync with the [MigratableTables.all] schema.
 *
 * The generated file is written to [outputDirectory] and named
 * `V{timestamp}__{description}.sql`. If no changes are required the call
 * is a no-op (an empty file is not written).
 *
 * Typical invocation: from a JUnit test against an empty Testcontainer
 * Postgres, save the result into `src/main/resources/db/migration/`.
 */
object MigrationGenerator {
    private val TIMESTAMP_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)

    @OptIn(ExperimentalDatabaseMigrationApi::class)
    fun generate(
        database: Database,
        outputDirectory: File,
        description: String,
    ): File? {
        outputDirectory.mkdirs()
        val timestamp = TIMESTAMP_FORMAT.format(Instant.now())
        val scriptName = "V${timestamp}__$description"
        transaction(database) {
            MigrationUtils.generateMigrationScript(
                *MigratableTables.all.toTypedArray(),
                scriptDirectory = outputDirectory.absolutePath,
                scriptName = scriptName,
                withLogs = false,
            )
        }
        val produced = File(outputDirectory, "$scriptName.sql")
        return if (produced.exists() && produced.length() > 0L) {
            produced
        } else {
            // Some Exposed builds always write a file; treat zero-length as empty diff.
            if (produced.exists()) produced.delete()
            null
        }
    }
}
