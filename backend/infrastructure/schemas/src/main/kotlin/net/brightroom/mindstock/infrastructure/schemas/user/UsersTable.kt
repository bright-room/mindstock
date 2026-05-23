package net.brightroom.mindstock.infrastructure.schemas.user

import net.brightroom.mindstock.infrastructure.migration.annotation.Migratable
import net.brightroom.mindstock.infrastructure.schemas.AggregateRootTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object UsersTable : AggregateRootTable("users") {
    val zitadel_sub = text("zitadel_sub").uniqueIndex()
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
