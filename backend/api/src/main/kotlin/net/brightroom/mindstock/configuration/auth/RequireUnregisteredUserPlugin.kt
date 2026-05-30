package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

/**
 * Route subtree に install することで「未登録 User しか通さない」境界を作る。
 * MindstockAuthPlugin が組み立てた MindstockSession を見て userId != null(登録済み)なら 409。
 *
 * 用途: /user/public/register は未登録ユーザー専用。登録済みユーザーの再 register を
 * routing 層で遮断し、users.zitadel_sub の UNIQUE 違反を未然に防ぐ。
 */
val RequireUnregisteredUserPlugin =
    createRouteScopedPlugin(name = "RequireUnregisteredUser") {
        onCall { call ->
            val session = call.attributes.getOrNull(MindstockSessionKey)
            if (session == null || session.userId != null) {
                call.respond(HttpStatusCode.Conflict)
            }
        }
    }
