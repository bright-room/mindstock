@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ProductWantedEventsTable : HistoryTable("product_wanted_events") {
    val productId = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val wanted = bool("wanted")
    val recordedAt = timestampWithTimeZone("recorded_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, productId, id)
    }
}
