package net.brightroom.mindstock.infrastructure.schema

import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.UUIDColumnType

abstract class AggregateRootTable(name: String) : Table(name) {
    val id = uuid("id").defaultExpression(CustomFunction("uuidv7", UUIDColumnType()))
    override val primaryKey = PrimaryKey(id)
}

abstract class HistoryTable(name: String) : Table(name) {
    val id = long("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)
}
