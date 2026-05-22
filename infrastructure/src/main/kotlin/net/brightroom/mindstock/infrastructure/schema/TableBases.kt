package net.brightroom.mindstock.infrastructure.schema

import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.UUIDColumnType
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

/**
 * Base for aggregate root tables. id is UUID with `DEFAULT uuidv7()` so PG
 * generates the value server-side. created_at is automatically populated.
 */
abstract class AggregateRootTable(name: String) : Table(name) {
    val id = uuid("id").defaultExpression(CustomFunction("uuidv7", UUIDColumnType()))
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}

/**
 * Base for history / fact tables. id is BIGINT GENERATED ALWAYS AS
 * IDENTITY (monotonic, ensures "latest" = MAX(id) per group). created_at
 * is automatically populated.
 *
 * Note: Exposed's `long("id").autoIncrement()` emits BIGSERIAL by default
 * on PG. To get GENERATED ALWAYS AS IDENTITY, we declare the column as
 * `long("id")` (no autoIncrement) and rely on the migration generator to
 * emit the GENERATED clause via a custom DDL. See HistoryTableSchemaSql
 * for the override.
 *
 * For simplicity in this initial plan we accept BIGSERIAL (functionally
 * equivalent for our purposes — both produce monotonically increasing
 * BIGINT values).
 */
abstract class HistoryTable(name: String) : Table(name) {
    val id = long("id").autoIncrement()
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}
