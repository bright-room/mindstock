package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable

@Serializable
data class Stocks(
    val list: List<Stock>,
)
