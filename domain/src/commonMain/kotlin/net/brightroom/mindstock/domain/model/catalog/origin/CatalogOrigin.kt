package net.brightroom.mindstock.domain.model.catalog.origin

import kotlinx.serialization.Serializable

@Serializable
enum class CatalogOrigin {
    大元マスタ,
    外部取得,
    世帯独自,
}
