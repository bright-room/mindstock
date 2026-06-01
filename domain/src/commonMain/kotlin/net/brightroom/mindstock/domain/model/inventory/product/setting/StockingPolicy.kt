package net.brightroom.mindstock.domain.model.inventory.product.setting

import kotlinx.serialization.Serializable

@Serializable
data class StockingPolicy(
    val unit: ProductUnit,
    val minimumStock: MinimumStock,
)
