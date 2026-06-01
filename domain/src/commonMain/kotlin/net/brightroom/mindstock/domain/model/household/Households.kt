package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

@Serializable
data class Households(
    val list: List<Household>,
) {
    fun size(): Int = list.size
}
