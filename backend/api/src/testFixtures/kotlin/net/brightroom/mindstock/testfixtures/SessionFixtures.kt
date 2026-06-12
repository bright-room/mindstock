@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.testfixtures

import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Controller テスト用の登録済みセッション。residentId / identity は呼び出し側で stub に使うため引数で渡せる。 */
fun buildRegisteredSession(
    residentId: ResidentId = ResidentId.create(),
    identity: AuthIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
    expiresAt: Instant = Clock.System.now().plus(1.hours),
    connectionId: Uuid = Uuid.random(),
): MindstockSession.Registered = MindstockSession.Registered(identity, residentId, expiresAt, connectionId)
