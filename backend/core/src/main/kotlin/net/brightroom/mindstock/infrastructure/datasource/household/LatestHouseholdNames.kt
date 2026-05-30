package net.brightroom.mindstock.infrastructure.datasource.household

import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class LatestHouseholdNames(
    val alias: QueryAlias,
    val householdId: ExpressionWithColumnType<Uuid>,
    val maxId: ExpressionWithColumnType<Long?>,
)

@OptIn(ExperimentalUuidApi::class)
internal fun latestHouseholdNames(): LatestHouseholdNames {
    val maxIdExpr = HouseholdNamesTable.id.max().alias("max_household_name_id")
    val alias =
        HouseholdNamesTable
            .select(HouseholdNamesTable.household_id, maxIdExpr)
            .groupBy(HouseholdNamesTable.household_id)
            .alias("latest_household_names")
    return LatestHouseholdNames(
        alias = alias,
        householdId = alias[HouseholdNamesTable.household_id],
        maxId = alias[maxIdExpr],
    )
}
