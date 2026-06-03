package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

/**
 * Route subtree に install することで「登録済み Resident しか通さない」境界を作る。
 * [MindstockAuthPlugin] が組み立てた [MindstockSession] を見て、
 * [MindstockSession.Registered] でなければ 401。register route には install しない。
 */
val RequireRegisteredUserPlugin =
    createRouteScopedPlugin(name = "RequireRegisteredUser") {
        onCall { call ->
            val session = call.attributes.getOrNull(MindstockSessionKey)
            if (session is MindstockSession.Registered) return@onCall
            call.respond(HttpStatusCode.Unauthorized)
        }
    }
