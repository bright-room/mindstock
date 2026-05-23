package net.brightroom.mindstock.infrastructure.schema.user

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object UserDisplayNamesTable : HistoryTable("user_display_names") {
    val user_id = reference("user_id", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val display_name = varchar("display_name", 100)
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        index(false, user_id, id)
    }
}
