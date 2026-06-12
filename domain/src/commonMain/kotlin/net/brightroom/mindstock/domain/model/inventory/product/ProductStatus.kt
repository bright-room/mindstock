package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable

@Serializable
enum class ProductStatus {
    採用中,
    アーカイブ済,
}
