@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object ResidentDisplayNamesTable : HistoryTable("resident_display_names") {
    val residentId = reference("resident_id", ResidentsTable.id, onDelete = ReferenceOption.RESTRICT)
    val displayName = varchar("display_name", 100)
    val recordedAt = datetime("recorded_at").defaultExpression(CurrentDateTime)

    init {
        index(false, residentId, id)
    }
}
