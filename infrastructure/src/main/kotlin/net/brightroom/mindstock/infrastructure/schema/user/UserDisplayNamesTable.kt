package net.brightroom.mindstock.infrastructure.schema.user

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object UserDisplayNamesTable : HistoryTable("user_display_names") {
    val user_id = reference("user_id", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val display_name = text("display_name")

    init {
        index(false, user_id, id)
    }
}
