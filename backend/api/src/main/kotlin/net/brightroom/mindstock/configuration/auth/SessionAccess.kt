@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

/** WS upgrade 時に MindstockAuthPlugin が格納した session を取り出す。 */
fun sessionOf(call: ApplicationCall): MindstockSession = call.attributes[MindstockSessionKey]

/**
 * Registered のときだけ residentId を返す。RequireRegisteredUserPlugin 配下では常に Registered のため、
 * Unregistered での呼び出しは到達しない不変条件違反(IllegalStateException → guarded で Internal)。
 */
fun MindstockSession.requireResidentId(): ResidentId =
    when (this) {
        is MindstockSession.Registered -> residentId
        is MindstockSession.Unregistered -> error("registered session required")
    }
