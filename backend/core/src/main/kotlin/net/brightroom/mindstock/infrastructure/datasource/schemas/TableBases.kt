@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.Table

/** insert-once 識別子行。id はドメイン採番(autoGenerate しない)。 */
abstract class AggregateRootTable(
    name: String,
) : Table(name) {
    val id = uuid("id")
    override val primaryKey = PrimaryKey(id)
}

/** append-only 履歴/イベント。id は単調増加 bigint(Window の ORDER キー)。 */
abstract class HistoryTable(
    name: String,
) : Table(name) {
    val id = long("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)
}
