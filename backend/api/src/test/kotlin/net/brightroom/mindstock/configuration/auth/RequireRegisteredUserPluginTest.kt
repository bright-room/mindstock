package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
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

        fun registered() = MindstockSession.Registered(identity, ResidentId.create(), farFuture, Uuid.random())

        fun unregistered() = MindstockSession.Unregistered(identity, farFuture, Uuid.random())

        suspend fun guardedStatusWith(session: MindstockSession?): HttpStatusCode {
            lateinit var status: HttpStatusCode
            testApplication {
                application {
                    if (session != null) install(stubSessionPlugin(session))
                    routing {
                        route("/guarded") {
                            install(RequireRegisteredUserPlugin)
                            get { call.respondText("ok") }
                        }
                    }
                }
                status = client.get("/guarded").status
            }
            return status
        }

        test("Registered → 200") {
            guardedStatusWith(registered()) shouldBe HttpStatusCode.OK
        }

        test("Unregistered → 401") {
            guardedStatusWith(unregistered()) shouldBe HttpStatusCode.Unauthorized
        }

        test("session 無し → 401") {
            guardedStatusWith(null) shouldBe HttpStatusCode.Unauthorized
        }

        test("Unregistered: 保護ルートのハンドラは実行されない(バイパスしない)") {
            val handlerRuns = AtomicInteger(0)
            testApplication {
                application {
                    install(stubSessionPlugin(unregistered()))
                    routing {
                        route("/guarded") {
                            install(RequireRegisteredUserPlugin)
                            get {
                                handlerRuns.incrementAndGet()
                                call.respondText("ok")
                            }
                        }
                    }
                }
                client.get("/guarded").status shouldBe HttpStatusCode.Unauthorized
            }
            handlerRuns.get() shouldBe 0
        }
    })
