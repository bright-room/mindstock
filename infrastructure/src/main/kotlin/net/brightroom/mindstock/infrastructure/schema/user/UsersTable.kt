package net.brightroom.mindstock.infrastructure.schema.user

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object UsersTable : AggregateRootTable("users") {
    val zitadel_sub = text("zitadel_sub").uniqueIndex()
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
