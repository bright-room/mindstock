package net.brightroom.mindstock.infrastructure.datasource.invitation

import net.brightroom.mindstock.application.repository.invitation.InvitationRegisterRepository
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import net.brightroom.mindstock.infrastructure.datasource.Created
import net.brightroom.mindstock.infrastructure.datasource.schemas.InvitationValidityEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.InvitationsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class InvitationRegisterDataSource(
    private val database: Database,
) : InvitationRegisterRepository {
    override fun issue(invitation: Invitation): Invitation {
        // PK(code) 衝突は unique violation。各試行は独立した transaction で実行し、
        // 衝突時のみ code を再生成して最大 3 回リトライする
        // (1 つの transaction 内で再試行すると aborted state になり 2 回目以降が失敗するため)。
        val createdTime = Created.now()
        var current = invitation
        repeat(MAX_RETRY) { attempt ->
            try {
                return transaction(database) {
                    InvitationsTable.insert {
                        it[code] = current.code()
                        it[householdId] = current.householdId()
                        it[grantedRole] = current.grantedRole
                    }
                    InvitationValidityEventsTable.insert {
                        it[invitationCode] = current.code()
                        it[validity] = InvitationValidity.有効
                        it[recordedAt] = createdTime()
                    }
                    current
                }
            } catch (e: SQLException) {
                // Exposed は JDBC error を ExposedSQLException(SQLException 派生)で包む。
                // SQLState は cause 側に乗ることがある。unique violation(23505)のみリトライ。
                val state = e.sqlState ?: (e.cause as? SQLException)?.sqlState
                if (state != "23505") throw e
                if (attempt == MAX_RETRY - 1) throw e
                current = Invitation.issue(current.householdId, current.grantedRole)
            }
        }
        error("unreachable")
    }

    override fun revoke(code: InvitationCode) {
        transaction(database) {
            val createdTime = Created.now()
            InvitationValidityEventsTable.insert {
                it[invitationCode] = code()
                it[validity] = InvitationValidity.無効
                it[recordedAt] = createdTime()
            }
        }
    }

    private companion object {
        const val MAX_RETRY = 3
    }
}
