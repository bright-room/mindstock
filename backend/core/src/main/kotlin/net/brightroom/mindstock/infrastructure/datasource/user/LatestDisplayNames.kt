package net.brightroom.mindstock.infrastructure.datasource.user

import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class LatestDisplayNames(
    val alias: QueryAlias,
    val userId: ExpressionWithColumnType<Uuid>,
    val maxId: ExpressionWithColumnType<Long?>,
)

@OptIn(ExperimentalUuidApi::class)
internal fun latestDisplayNames(): LatestDisplayNames {
    val maxIdExpr = UserDisplayNamesTable.id.max().alias("max_name_id")
    val alias =
        UserDisplayNamesTable
            .select(UserDisplayNamesTable.user_id, maxIdExpr)
            .groupBy(UserDisplayNamesTable.user_id)
            .alias("latest_names")
    return LatestDisplayNames(
        alias = alias,
        userId = alias[UserDisplayNamesTable.user_id],
        maxId = alias[maxIdExpr],
    )
}
