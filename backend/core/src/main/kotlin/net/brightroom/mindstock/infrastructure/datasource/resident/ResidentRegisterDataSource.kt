@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.resident

import net.brightroom.mindstock.application.repository.resident.ResidentRegisterRepository
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.infrastructure.datasource.Created
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentAuthIdentitiesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ResidentRegisterDataSource(
    private val database: Database,
) : ResidentRegisterRepository {
    override fun registerResident(
        authIdentity: AuthIdentity,
        displayName: DisplayName,
    ): Resident =
        transaction(database) {
            val residentId = ResidentId.create()
            val createdTime = Created.now()
            ResidentsTable.insert {
                it[id] = residentId()
                it[createdAt] = createdTime()
            }
            ResidentAuthIdentitiesTable.insert {
                it[ResidentAuthIdentitiesTable.residentId] = residentId()
                it[provider] = authIdentity.provider
                it[subject] = authIdentity.subject()
                it[linkedAt] = createdTime()
            }
            ResidentDisplayNamesTable.insert {
                it[ResidentDisplayNamesTable.residentId] = residentId()
                it[ResidentDisplayNamesTable.displayName] = displayName()
                it[recordedAt] = createdTime()
            }
            Resident(residentId, Profile(displayName))
        }

    override fun appendDisplayName(
        residentId: ResidentId,
        displayName: DisplayName,
    ) {
        transaction(database) {
            val createdTime = Created.now()
            ResidentDisplayNamesTable.insert {
                it[ResidentDisplayNamesTable.residentId] = residentId()
                it[ResidentDisplayNamesTable.displayName] = displayName()
                it[recordedAt] = createdTime()
            }
        }
    }
}
