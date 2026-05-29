package net.brightroom.mindstock.infrastructure.datasource.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun hydrateCatalogItem(
    id: Uuid,
    name: String,
    unit: String,
): CatalogItem =
    CatalogItem(
        id = CatalogItemId(id),
        name = CatalogItemName(name),
        unit = CatalogItemUnit(unit),
    )
