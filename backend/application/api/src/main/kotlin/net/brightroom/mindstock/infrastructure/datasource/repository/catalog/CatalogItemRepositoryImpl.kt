package net.brightroom.mindstock.infrastructure.datasource.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.UUIDColumnType
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
internal class CatalogItemRepositoryImpl : CatalogItemRepository {
    override fun search(
        query: String,
        limit: Int,
    ): CatalogItems {
        val sql =
            """
            SELECT ci.id AS catalog_item_id,
                   r.name,
                   r.unit
            FROM catalog_items ci
            INNER JOIN (
                SELECT DISTINCT ON (catalog_item_id) catalog_item_id, name, unit, id
                FROM catalog_item_revisions
                ORDER BY catalog_item_id, id DESC
            ) r ON r.catalog_item_id = ci.id
            WHERE r.name ILIKE ?
            ORDER BY r.name
            LIMIT ?
            """.trimIndent()

        val items = mutableListOf<CatalogItem>()

        TransactionManager.current().exec(
            sql,
            args =
                listOf(
                    TextColumnType() to "%$query%",
                    org.jetbrains.exposed.v1.core
                        .IntegerColumnType() to limit,
                ),
        ) { rs ->
            while (rs.next()) {
                items.add(
                    hydrateCatalogItem(
                        id = rs.getObject("catalog_item_id", UUID::class.java).toKotlinUuid(),
                        name = rs.getString("name"),
                        unit = rs.getString("unit"),
                    ),
                )
            }
        }

        return CatalogItems(items)
    }

    override fun findById(id: CatalogItemId): CatalogItem? {
        val sql =
            """
            SELECT ci.id AS catalog_item_id,
                   r.name,
                   r.unit
            FROM catalog_items ci
            INNER JOIN (
                SELECT DISTINCT ON (catalog_item_id) catalog_item_id, name, unit, id
                FROM catalog_item_revisions
                ORDER BY catalog_item_id, id DESC
            ) r ON r.catalog_item_id = ci.id
            WHERE ci.id = ?
            """.trimIndent()

        return TransactionManager.current().exec(
            sql,
            args = listOf(UUIDColumnType() to id().toJavaUuid()),
        ) { rs ->
            if (rs.next()) {
                hydrateCatalogItem(
                    id = rs.getObject("catalog_item_id", UUID::class.java).toKotlinUuid(),
                    name = rs.getString("name"),
                    unit = rs.getString("unit"),
                )
            } else {
                null
            }
        }
    }
}
