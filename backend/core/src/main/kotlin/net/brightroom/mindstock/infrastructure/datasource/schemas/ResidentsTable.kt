@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object ResidentsTable : Table("residents") {
    val id = uuid("id")
    override val primaryKey = PrimaryKey(id)

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
