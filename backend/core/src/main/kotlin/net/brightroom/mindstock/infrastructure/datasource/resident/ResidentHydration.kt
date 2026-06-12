@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.resident

import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import org.jetbrains.exposed.v1.core.ExpressionWithColumnTypeAlias
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.jdbc.select

/**
 * resident_display_names の「resident_id ごと最新行」だけを含むサブクエリ alias を作る。
 * 列: resident_id / display_name(rn=1 で絞る)。
 */
internal fun latestResidentDisplayNames(): Pair<QueryAlias, ResidentDisplayNamesLatestRefs> {
    val rn =
        rowNumber()
            .over()
            .partitionBy(ResidentDisplayNamesTable.residentId)
            .orderBy(ResidentDisplayNamesTable.id to SortOrder.DESC)
    val rnAlias = rn.alias("rn")
    val sub =
        ResidentDisplayNamesTable
            .select(
                ResidentDisplayNamesTable.residentId,
                ResidentDisplayNamesTable.displayName,
                rnAlias,
            ).alias("latest_display_names")
    return sub to ResidentDisplayNamesLatestRefs(rnAlias)
}

internal class ResidentDisplayNamesLatestRefs(
    val rn: ExpressionWithColumnTypeAlias<Long>,
)

/** residents.id + display_name 行から Resident を組み立てる(両テーブルの ResultRow を渡す)。 */
internal fun ResultRow.toResident(displayNameAlias: QueryAlias): Resident =
    Resident(
        id = ResidentId(this[ResidentsTable.id]),
        profile = ResidentProfile(DisplayName(this[displayNameAlias[ResidentDisplayNamesTable.displayName]])),
    )
