@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas.user

import net.brightroom.mindstock.infrastructure.datasource.schemas.HistoryTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object UserDisplayNamesTable : HistoryTable("user_display_names") {
    val user_id = reference("user_id", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val display_name = varchar("display_name", 100)
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, user_id, id)
    }
}
