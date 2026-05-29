@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource

import org.jetbrains.exposed.v1.core.Table

abstract class AggregateRootTable(
    name: String,
) : Table(name) {
    val id = uuid("id").autoGenerate(UuidVersion.V7)
    override val primaryKey = PrimaryKey(id)
}

abstract class HistoryTable(
    name: String,
) : Table(name) {
    val id = long("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)
}
