package net.brightroom.mindstock.infrastructure.schema.household

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object HouseholdsTable : AggregateRootTable("households") {
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)
}
