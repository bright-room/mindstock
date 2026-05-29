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
import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
private fun stubSessionPlugin(session: MindstockSession) =
    createApplicationPlugin(name = "StubSession-${Uuid.random()}") {
        onCall { call ->
            call.attributes.put(MindstockSessionKey, session)
        }
    }

@OptIn(ExperimentalUuidApi::class)
class RequireRegisteredUserPluginTest :
    FunSpec({
        fun mindstockSessionOf(userId: UserId?): MindstockSession =
            MindstockSession(
                identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
                userId = userId,
                exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                callId = Uuid.random(),
            )

        test("session 有り + userId 非 null → 200") {
            testApplication {
                application {
                    install(stubSessionPlugin(mindstockSessionOf(UserId(Uuid.random()))))
                    routing {
                        route("/guarded") {
                            install(RequireRegisteredUserPlugin)
                            get { call.respondText("ok") }
                        }
                    }
                }
                client.get("/guarded").status shouldBe HttpStatusCode.OK
            }
        }

        test("session 有り + userId null → 401") {
            testApplication {
                application {
                    install(stubSessionPlugin(mindstockSessionOf(null)))
                    routing {
                        route("/guarded") {
                            install(RequireRegisteredUserPlugin)
                            get { call.respondText("ok") }
                        }
                    }
                }
                client.get("/guarded").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("session 無し → 401") {
            testApplication {
                application {
                    routing {
                        route("/guarded") {
                            install(RequireRegisteredUserPlugin)
                            get { call.respondText("ok") }
                        }
                    }
                }
                client.get("/guarded").status shouldBe HttpStatusCode.Unauthorized
            }
        }
    })
