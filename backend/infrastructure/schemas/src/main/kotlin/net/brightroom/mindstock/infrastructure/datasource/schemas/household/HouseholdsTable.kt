package net.brightroom.mindstock.infrastructure.datasource.schemas.household

import net.brightroom.mindstock.infrastructure.datasource.schemas.AggregateRootTable
import net.brightroom.mindstock.infrastructure.migration.annotation.Migratable
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object HouseholdsTable : AggregateRootTable("households") {
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
