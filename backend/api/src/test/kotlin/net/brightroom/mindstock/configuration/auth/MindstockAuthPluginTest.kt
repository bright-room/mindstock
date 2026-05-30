package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.JwkProviderBuilder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.user.UserRepository
import org.jetbrains.exposed.v1.jdbc.Database
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * DB / 本物 JWT を使わない最小ユニットテスト。
 * 「token なし → session 未付与 → 401 + handler 内で session 取得不可」のフロー確認のみ。
 *
 * 有効トークン / 期限切れ / wrong issuer / wrong audience 等の検証は
 * 既存 `JwtAuthE2eTest` (integration) が網羅しており、Phase 4.12 で
 * MindstockAuthPlugin 化後も同テストが green であることが本 plugin の検証になる。
 */
class MindstockAuthPluginTest :
    FunSpec({
        test("認証情報無し(token なし)→ 401, MindstockSession 属性は付かない") {
            var sessionSeen = false
            testApplication {
                application {
                    install(MindstockAuthPlugin) {
                        // JWKS は本テストでは到達しないが、null チェックを通すためダミー URL を渡す
                        jwkProvider =
                            JwkProviderBuilder(URL("http://127.0.0.1:1/jwks"))
                                .cached(1, 1, TimeUnit.MINUTES)
                                .rateLimited(1, 1, TimeUnit.MINUTES)
                                .build()
                        issuer = "test-issuer"
                        audience = "test-aud"
                        userRepository = mockk<UserRepository>(relaxed = true)
                        database = mockk<Database>(relaxed = true)
                    }
                    routing {
                        get("/probe") {
                            sessionSeen = call.attributes.getOrNull(MindstockSessionKey) != null
                            call.respondText("ok")
                        }
                    }
                }
                val res = client.get("/probe")
                res.status shouldBe HttpStatusCode.Unauthorized
                sessionSeen shouldBe false
            }
        }
    })
