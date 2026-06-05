package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
private fun stubSessionPlugin(session: MindstockSession) =
    createApplicationPlugin(name = "StubSession-${Uuid.random()}") {
        onCall { call -> call.attributes.put(MindstockSessionKey, session) }
    }

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class RequireRegisteredUserPluginTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        // 本 plugin は exp を見ないため任意の値でよい(失効判定は P5c の guard)。
        val farFuture = Instant.DISTANT_FUTURE
        val publicPath = "/api/v1/resident/register"

        fun registered() = MindstockSession.Registered(identity, ResidentId.create(), farFuture, Uuid.random())

        fun unregistered() = MindstockSession.Unregistered(identity, farFuture, Uuid.random())

        // session を stub し、app レベルで RequireRegisteredUserPlugin を install したアプリで path を GET。
        // 返り値は (status, 保護/public/healthz ハンドラの実行回数合計)。
        suspend fun statusFor(
            session: MindstockSession?,
            path: String,
        ): Pair<HttpStatusCode, Int> {
            val handlerRuns = AtomicInteger(0)
            lateinit var status: HttpStatusCode
            testApplication {
                application {
                    if (session != null) install(stubSessionPlugin(session))
                    install(RequireRegisteredUserPlugin) { publicPaths.add(publicPath) }
                    routing {
                        get("/api/v1/resident") {
                            handlerRuns.incrementAndGet()
                            call.respondText("ok")
                        }
                        get(publicPath) {
                            handlerRuns.incrementAndGet()
                            call.respondText("ok")
                        }
                        get("/healthz") {
                            handlerRuns.incrementAndGet()
                            call.respondText("ok")
                        }
                    }
                }
                status = client.get(path).status
            }
            return status to handlerRuns.get()
        }

        test("Registered + 保護ルート → 200") {
            statusFor(registered(), "/api/v1/resident").first shouldBe HttpStatusCode.OK
        }

        test("Unregistered + 保護ルート → 401") {
            statusFor(unregistered(), "/api/v1/resident").first shouldBe HttpStatusCode.Unauthorized
        }

        test("session 無し + 保護ルート → 401") {
            statusFor(null, "/api/v1/resident").first shouldBe HttpStatusCode.Unauthorized
        }

        test("Unregistered: 保護ルートのハンドラは実行されない(バイパスしない)") {
            val (status, runs) = statusFor(unregistered(), "/api/v1/resident")
            status shouldBe HttpStatusCode.Unauthorized
            runs shouldBe 0
        }

        test("Unregistered + public 登録ルート(allowlist 完全一致)→ 200") {
            statusFor(unregistered(), publicPath).first shouldBe HttpStatusCode.OK
        }

        test("Unregistered + API 接頭辞外のパス → 素通し(200)") {
            statusFor(unregistered(), "/healthz").first shouldBe HttpStatusCode.OK
        }
    })
