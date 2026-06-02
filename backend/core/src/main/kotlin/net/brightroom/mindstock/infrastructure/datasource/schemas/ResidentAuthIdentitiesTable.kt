@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object ResidentAuthIdentitiesTable : HistoryTable("resident_auth_identities") {
    val residentId = reference("resident_id", ResidentsTable.id, onDelete = ReferenceOption.RESTRICT)
    val provider = enumerationByName("provider", 20, AuthProvider::class)
    val subject = varchar("subject", 255)
    val linkedAt = datetime("linked_at").defaultExpression(CurrentDateTime)

    init {
        uniqueIndex(provider, subject)
        index(false, residentId)
    }
}
