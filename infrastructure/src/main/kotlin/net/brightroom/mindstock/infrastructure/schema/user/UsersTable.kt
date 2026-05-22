package net.brightroom.mindstock.infrastructure.schema.user

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable

@Migratable
object UsersTable : AggregateRootTable("users") {
    val zitadel_sub = text("zitadel_sub").uniqueIndex()
}
