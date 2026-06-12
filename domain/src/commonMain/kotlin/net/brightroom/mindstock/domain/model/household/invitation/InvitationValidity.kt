package net.brightroom.mindstock.domain.model.household.invitation

import kotlinx.serialization.Serializable

@Serializable
enum class InvitationValidity {
    有効,
    無効,
}
