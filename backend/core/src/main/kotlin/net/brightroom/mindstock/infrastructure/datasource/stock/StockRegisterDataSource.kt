package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.infrastructure.datasource.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
import net.brightroom.mindstock.infrastructure.datasource.user.toProfile
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.ZoneOffset
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class StockRegisterDataSource : StockRegisterRepository {
    override fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    ): Replenishment {
        insertMovement(product, quantity, occurredAt, by, note, StockMovementType.REPLENISHMENT)
        val actorProfile = loadProfile(by)
        return Replenishment(product, quantity, occurredAt, actorProfile, note)
    }

    override fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    ): Consumption {
        insertMovement(product, quantity, occurredAt, by, note, StockMovementType.CONSUMPTION)
        val actorProfile = loadProfile(by)
        return Consumption(product, quantity, occurredAt, actorProfile, note)
    }

    private fun insertMovement(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: UserId,
        note: Note,
        type: StockMovementType,
    ) {
        StockMovementsTable.insert {
            it[product_id] = product.id()
            it[StockMovementsTable.type] = type
            it[StockMovementsTable.quantity] = quantity()
            it[occurred_at] = occurredAt().toJavaInstant().atOffset(ZoneOffset.UTC)
            it[acted_by] = actor()
            it[StockMovementsTable.note] = note()
        }
    }

    private fun loadProfile(userId: UserId): Profile {
        val maxNameIdAlias = UserDisplayNamesTable.id.max().alias("max_name_id")
        val latestNames =
            UserDisplayNamesTable
                .select(UserDisplayNamesTable.user_id, maxNameIdAlias)
                .groupBy(UserDisplayNamesTable.user_id)
                .alias("latest_names")
        val latestNameUserId = latestNames[UserDisplayNamesTable.user_id]
        val latestNameMaxId = latestNames[maxNameIdAlias]

        return UsersTable
            .join(latestNames, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latestNameUserId)
            .join(UserDisplayNamesTable, JoinType.INNER) {
                (UserDisplayNamesTable.user_id eq latestNameUserId) and
                    (UserDisplayNamesTable.id eq latestNameMaxId)
            }.selectAll()
            .where { UsersTable.id eq userId() }
            .single()
            .toProfile()
    }
}
