package net.brightroom.mindstock.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table

/**
 * Canonical list of every [@Migratable] table. Tasks 7–11 add to this list
 * as each domain's schema is introduced. Listing tables explicitly (rather
 * than scanning the classpath at runtime) keeps the registry trivial and
 * test-friendly.
 */
object MigratableTables {
    val all: List<Table>
        get() = listOf(
            // Populated in subsequent tasks
        )
}
