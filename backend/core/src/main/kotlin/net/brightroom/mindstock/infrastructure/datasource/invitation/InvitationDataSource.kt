package net.brightroom.mindstock.infrastructure.datasource.invitation

import net.brightroom.mindstock.application.repository.invitation.InvitationRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.infrastructure.datasource.schemas.InvitationValidityEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.InvitationsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class InvitationDataSource(
    private val database: Database,
) : InvitationRepository {
    override fun findByCode(code: InvitationCode): Invitation =
        transaction(database) {
            val base =
                InvitationsTable
                    .selectAll()
                    .where { InvitationsTable.code eq code() }
                    .limit(1)
                    .firstOrNull()
                    ?: throw ResourceNotFoundException("invitation not found: $code")

            val rn =
                rowNumber()
                    .over()
                    .partitionBy(InvitationValidityEventsTable.invitationCode)
                    .orderBy(InvitationValidityEventsTable.id to SortOrder.DESC)
            val rnAlias = rn.alias("rn")
            val vSub =
                InvitationValidityEventsTable
                    .select(InvitationValidityEventsTable.invitationCode, InvitationValidityEventsTable.validity, rnAlias)
                    .where { InvitationValidityEventsTable.invitationCode eq code() }
                    .alias("latest_validity")
            val validity =
                vSub
                    .selectAll()
                    .where { vSub[rnAlias] eq 1L }
                    .limit(1)
                    .first()[vSub[InvitationValidityEventsTable.validity]]

            assembleInvitation(
                householdId = HouseholdId(base[InvitationsTable.householdId]),
                code = code,
                grantedRole = base[InvitationsTable.grantedRole],
                validity = validity,
            )
        }
}
