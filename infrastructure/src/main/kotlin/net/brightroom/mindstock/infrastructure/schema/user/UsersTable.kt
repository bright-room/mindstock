package net.brightroom.mindstock.infrastructure.schema.user

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object UsersTable : AggregateRootTable("users") {
    val zitadel_sub = text("zitadel_sub").uniqueIndex()
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)
}
