@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object ProductWantedEventsTable : HistoryTable("product_wanted_events") {
    val productId = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val wanted = bool("wanted")
    val recordedAt = datetime("recorded_at").defaultExpression(CurrentDateTime)

    init {
        index(false, productId, id)
    }
}
