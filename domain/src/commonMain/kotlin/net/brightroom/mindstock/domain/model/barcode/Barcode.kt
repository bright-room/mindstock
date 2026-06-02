package net.brightroom.mindstock.domain.model.barcode

import kotlinx.serialization.Serializable

@Serializable
sealed interface Barcode {
    @Serializable
    data object Unlinked : Barcode

    @Serializable
    data class Linked(
        val jan: Jan,
    ) : Barcode
}
