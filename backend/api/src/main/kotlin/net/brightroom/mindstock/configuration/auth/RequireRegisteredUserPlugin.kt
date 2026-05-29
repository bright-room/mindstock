package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

/**
 * Route subtree に install することで「登録済み User しか通さない」境界を作る。
 * MindstockAuthPlugin が組み立てた MindstockSession を見て userId が null なら 401。
 */
val RequireRegisteredUserPlugin =
    createRouteScopedPlugin(name = "RequireRegisteredUser") {
        onCall { call ->
            val session = call.attributes.getOrNull(MindstockSessionKey)
            if (session == null || session.userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
