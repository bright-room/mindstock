@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.ApplicationCall

/** WS upgrade 時に MindstockAuthPlugin が格納した session を取り出す。 */
fun sessionOf(call: ApplicationCall): MindstockSession = call.attributes[MindstockSessionKey]
