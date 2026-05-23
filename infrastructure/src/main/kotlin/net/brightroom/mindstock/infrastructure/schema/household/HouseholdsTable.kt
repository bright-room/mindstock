package net.brightroom.mindstock.infrastructure.schema.household

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object HouseholdsTable : AggregateRootTable("households") {
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
