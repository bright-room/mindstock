package net.brightroom.mindstock.domain.model.catalog.origin

import kotlinx.serialization.Serializable

@Serializable
enum class CatalogOrigin {
    マスタ,
    世帯独自,
}
