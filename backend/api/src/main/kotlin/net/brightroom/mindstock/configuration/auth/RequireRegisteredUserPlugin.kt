package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.server.response.respond

class RequireRegisteredUserConfig {
    /** ガード対象の API 接頭辞。これで始まらないパスは素通しする。 */
    var apiPathPrefix: String = "/api/v1"

    /**
     * 登録なし([MindstockSession.Unregistered])でもアクセスを許す public パス(完全一致)。
     * 例: 初回登録のための `/api/v1/resident/register`。
     */
    val publicPaths: MutableSet<String> = mutableSetOf()
}

/**
 * 「登録済み Resident しか通さない」境界を **application レベル** で張る plugin。
 * [MindstockAuthPlugin] が組み立てた [MindstockSession] を見て、[MindstockSession.Registered]
 * でなければ 401。[RequireRegisteredUserConfig.publicPaths] に列挙した public パスのみ除外する。
 *
 * application レベルにしているのは、route-scoped plugin が **WebSocket upgrade の経路だけ**
 * 隣接ルートや子パスへ漏れる Ktor の挙動を避けるため。kotlinx-rpc は全 RPC を WS で張るので、
 * route-scoped でガードすると public な `/resident/register` が WS upgrade でだけ 401 になり、
 * 初回登録ができなくなる(通常 GET では再現しない)。app レベル onCall は GET / WS upgrade で
 * 同一に走るため、この経路差をなくせる。
 *
 * public パスは「完全一致」で判定する。`/household/register` `/stock/register` 等は
 * **保護対象** なので、接尾辞一致(endsWith)では誤って公開してしまう点に注意。
 *
 * [MindstockAuthPlugin] の後に install すること(session が attributes に入った後に判定するため)。
 */
val RequireRegisteredUserPlugin =
    createApplicationPlugin(name = "RequireRegisteredUser", createConfiguration = ::RequireRegisteredUserConfig) {
        val apiPathPrefix = pluginConfig.apiPathPrefix
        val publicPaths = pluginConfig.publicPaths.toSet()

        onCall { call ->
            val path = call.request.path()
            if (!path.startsWith(apiPathPrefix)) return@onCall
            if (path in publicPaths) return@onCall
            if (call.attributes.getOrNull(MindstockSessionKey) is MindstockSession.Registered) return@onCall
            call.respond(HttpStatusCode.Unauthorized)
        }
    }
