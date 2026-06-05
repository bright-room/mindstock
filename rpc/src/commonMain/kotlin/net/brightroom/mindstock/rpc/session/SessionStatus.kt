package net.brightroom.mindstock.rpc.session

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.resident.Resident

/** 現在の接続の登録状態。boot 時の分岐に使う wire 型。 */
@Serializable
sealed interface SessionStatus {
    @Serializable
    data class Registered(
        val resident: Resident,
    ) : SessionStatus

    @Serializable
    data object Unregistered : SessionStatus
}
