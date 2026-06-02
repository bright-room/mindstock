@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.resident

import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentAuthIdentitiesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ResidentDataSource(
    private val database: Database,
) : ResidentRepository {
    override fun findByAuth(authIdentity: AuthIdentity): Resident =
        transaction(database) {
            val residentId =
                ResidentAuthIdentitiesTable
                    .select(ResidentAuthIdentitiesTable.residentId)
                    .where {
                        (ResidentAuthIdentitiesTable.provider eq authIdentity.provider) and
                            (ResidentAuthIdentitiesTable.subject eq authIdentity.subject())
                    }.firstOrNull()
                    ?.get(ResidentAuthIdentitiesTable.residentId)
                    ?: throw ResourceNotFoundException("resident not found for auth: ${authIdentity.provider}")
            hydrate(ResidentId(residentId))
        }

    override fun findById(id: ResidentId): Resident = transaction(database) { hydrate(id) }

    private fun hydrate(id: ResidentId): Resident {
        val (sub, refs) = latestResidentDisplayNames()
        val row =
            ResidentsTable
                .join(sub, JoinType.INNER, onColumn = ResidentsTable.id, otherColumn = sub[ResidentDisplayNamesTable.residentId])
                .selectAll()
                .where { (ResidentsTable.id eq id()) and (sub[refs.rn] eq 1L) }
                .firstOrNull()
                ?: throw ResourceNotFoundException("resident not found: $id")
        return row.toResident(sub)
    }
}
