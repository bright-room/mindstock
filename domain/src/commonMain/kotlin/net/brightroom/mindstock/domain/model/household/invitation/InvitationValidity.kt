package net.brightroom.mindstock.domain.model.household.invitation

import kotlinx.serialization.Serializable

@Serializable
enum class InvitationValidity {
    有効,
    無効,
    ;

    fun is有効(): Boolean = this == 有効
}
