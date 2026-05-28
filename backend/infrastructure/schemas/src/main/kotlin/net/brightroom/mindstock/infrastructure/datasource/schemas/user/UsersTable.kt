@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas.user

import net.brightroom.mindstock.infrastructure.datasource.schemas.AggregateRootTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object UsersTable : AggregateRootTable("users") {
    val zitadel_sub = text("zitadel_sub").uniqueIndex()
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
