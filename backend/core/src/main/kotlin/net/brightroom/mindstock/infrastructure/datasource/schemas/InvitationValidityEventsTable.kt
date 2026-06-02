package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object InvitationValidityEventsTable : Table("invitation_validity_events") {
    val id = long("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)

    val invitationCode = reference("invitation_code", InvitationsTable.code, onDelete = ReferenceOption.RESTRICT)
    val validity = enumerationByName("validity", 10, InvitationValidity::class)
    val recordedAt = datetime("recorded_at").defaultExpression(CurrentDateTime)

    init {
        index(false, invitationCode, id)
    }
}
