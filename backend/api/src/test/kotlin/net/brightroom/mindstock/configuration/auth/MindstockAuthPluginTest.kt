package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import net.brightroom.mindstock.e2e.auth.TestJwtIssuer
import net.brightroom.mindstock.e2e.auth.TestKeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant

class MindstockAuthPluginTest :
    FunSpec({
        val issuer = TestJwtIssuer.DEFAULT_ISSUER
        val audience = TestJwtIssuer.DEFAULT_AUDIENCE

        // TestKeyPair の公開鍵を返す JwkProvider。kid に依らず常に同じ鍵を返す
        // (TestJwtIssuer は kid=TestKeyPair.KID を付与する)。
        fun stubJwkProvider(): JwkProvider =
            mockk<JwkProvider>().also { provider ->
                val jwk = mockk<Jwk>()
                every { jwk.publicKey } returns TestKeyPair.publicKey
                every { provider.get(any<String>()) } returns jwk
            }

        // Pair<probe の HTTP status, probe route で観測した session(または null)>
        suspend fun runProbe(
            repo: ResidentRepository,
            authHeader: String?,
        ): Pair<HttpStatusCode, MindstockSession?> {
            var seen: MindstockSession? = null
            lateinit var status: HttpStatusCode
            testApplication {
                application {
                    install(MindstockAuthPlugin) {
                        jwkProvider = stubJwkProvider()
                        this.issuer = issuer
                        this.audience = audience
                        residentRepository = repo
                    }
                    routing {
                        get("/probe") {
                            seen = call.attributes.getOrNull(MindstockSessionKey)
                            call.respondText("ok")
                        }
                    }
                }
                status =
                    client
                        .get("/probe") {
                            if (authHeader != null) header(HttpHeaders.Authorization, authHeader)
                        }.status
            }
            return status to seen
        }

        fun registeredRepo(residentId: ResidentId): ResidentRepository =
            mockk<ResidentRepository>().also {
                every { it.findByAuth(any()) } returns Resident(residentId, ResidentProfile(DisplayName("Alice")))
            }

        fun unregisteredRepo(): ResidentRepository =
            mockk<ResidentRepository>().also {
                every { it.findByAuth(any<AuthIdentity>()) } throws ResourceNotFoundException("not found")
            }

        test("有効 JWT + 登録済み sub → Registered(residentId 一致)") {
            val residentId = ResidentId.create()
            val token = TestJwtIssuer.issue(subject = "zitadel-sub-1")
            val (status, session) = runProbe(registeredRepo(residentId), "Bearer $token")
            status shouldBe HttpStatusCode.OK
            val registered = session.shouldBeInstanceOf<MindstockSession.Registered>()
            registered.residentId shouldBe residentId
        }

        test("有効 JWT + 未登録 sub → Unregistered") {
            val token = TestJwtIssuer.issue(subject = "zitadel-sub-new")
            val (status, session) = runProbe(unregisteredRepo(), "Bearer $token")
            status shouldBe HttpStatusCode.OK
            session.shouldBeInstanceOf<MindstockSession.Unregistered>()
        }

        test("token 無し → 401, session 未付与") {
            val (status, session) = runProbe(registeredRepo(ResidentId.create()), null)
            status shouldBe HttpStatusCode.Unauthorized
            session shouldBe null
        }

        test("不正署名(別鍵)→ 401") {
            val otherKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val wrongAlg = Algorithm.RSA256(otherKeys.public as RSAPublicKey, otherKeys.private as RSAPrivateKey)
            val token = TestJwtIssuer.issue(subject = "sub", signWith = wrongAlg)
            val (status, _) = runProbe(registeredRepo(ResidentId.create()), "Bearer $token")
            status shouldBe HttpStatusCode.Unauthorized
        }

        test("issuer 不一致 → 401") {
            val token = TestJwtIssuer.issue(subject = "sub", issuer = "evil-issuer")
            val (status, _) = runProbe(registeredRepo(ResidentId.create()), "Bearer $token")
            status shouldBe HttpStatusCode.Unauthorized
        }

        test("audience 不一致 → 401") {
            val token = TestJwtIssuer.issue(subject = "sub", audience = "other-aud")
            val (status, _) = runProbe(registeredRepo(ResidentId.create()), "Bearer $token")
            status shouldBe HttpStatusCode.Unauthorized
        }

        test("exp 切れ(leeway 超過)→ 401") {
            val past = Instant.now().minusSeconds(7200)
            val token = TestJwtIssuer.issue(subject = "sub", issuedAt = past, expiresAt = past.plusSeconds(60))
            val (status, _) = runProbe(registeredRepo(ResidentId.create()), "Bearer $token")
            status shouldBe HttpStatusCode.Unauthorized
        }

        test("sub 空 → 401") {
            val token = TestJwtIssuer.issue(subject = "")
            val (status, _) = runProbe(registeredRepo(ResidentId.create()), "Bearer $token")
            status shouldBe HttpStatusCode.Unauthorized
        }

        test("exp 欠落 → 401") {
            val token = TestJwtIssuer.issue(subject = "sub", expiresAt = null)
            val (status, _) = runProbe(registeredRepo(ResidentId.create()), "Bearer $token")
            status shouldBe HttpStatusCode.Unauthorized
        }

        test("findByAuth が ResourceNotFoundException 以外を投げたら Unregistered に降格しない(2xx にならない)") {
            val brokenRepo =
                mockk<ResidentRepository>().also {
                    every { it.findByAuth(any()) } throws RuntimeException("db down")
                }
            val token = TestJwtIssuer.issue(subject = "sub")
            val (status, session) = runProbe(brokenRepo, "Bearer $token")
            // インフラ障害は握り潰さない: Unregistered セッションを作らず、成功応答も返さない。
            session shouldBe null
            (status.value >= 500) shouldBe true
        }
    })
