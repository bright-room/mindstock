package net.brightroom.mindstock.e2e.auth

import com.auth0.jwt.algorithms.Algorithm
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService
import net.brightroom.mindstock.presentation.rpc.UserRpcService
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant

class JwtAuthE2eTest :
    FunSpec({
        test("expired JWT → 401 (rpc call throws)") {
            e2eTest {
                val user = seedUser(displayName = "Alice")
                val expired =
                    TestJwtIssuer.issue(
                        subject = user.authIdentity.subject(),
                        issuedAt = Instant.now().minusSeconds(7200),
                        expiresAt = Instant.now().minusSeconds(3600),
                    )
                val rpc = authenticatedRpcClientWithToken(token = expired, path = "user").withService<UserRpcService>()
                shouldThrowAny { rpc.rename(DisplayName("X")) }
            }
        }

        test("wrong issuer → 401") {
            e2eTest {
                val user = seedUser(displayName = "Alice")
                val badIssuer =
                    TestJwtIssuer.issue(
                        subject = user.authIdentity.subject(),
                        issuer = "wrong-issuer",
                    )
                val rpc = authenticatedRpcClientWithToken(token = badIssuer, path = "user").withService<UserRpcService>()
                shouldThrowAny { rpc.rename(DisplayName("X")) }
            }
        }

        test("wrong audience → 401") {
            e2eTest {
                val user = seedUser(displayName = "Alice")
                val badAud =
                    TestJwtIssuer.issue(
                        subject = user.authIdentity.subject(),
                        audience = "wrong-aud",
                    )
                val rpc = authenticatedRpcClientWithToken(token = badAud, path = "user").withService<UserRpcService>()
                shouldThrowAny { rpc.rename(DisplayName("X")) }
            }
        }

        test("signed by an unknown key → 401") {
            e2eTest {
                val user = seedUser(displayName = "Alice")
                val foreign = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                val foreignAlg =
                    Algorithm.RSA256(foreign.public as RSAPublicKey, foreign.private as RSAPrivateKey)
                val token =
                    TestJwtIssuer.issue(
                        subject = user.authIdentity.subject(),
                        signWith = foreignAlg,
                        kid = "unknown-kid",
                    )
                val rpc = authenticatedRpcClientWithToken(token = token, path = "user").withService<UserRpcService>()
                shouldThrowAny { rpc.rename(DisplayName("X")) }
            }
        }

        test("unregistered sub may call register via user-public realm") {
            e2eTest {
                val newSub = "brand-new-user-sub"
                val token = TestJwtIssuer.issue(subject = newSub)
                val rpc =
                    authenticatedRpcClientWithToken(
                        token = token,
                        path = "user/public",
                    ).withService<UserPublicRpcService>()
                val created = rpc.register(DisplayName("Newbie"))
                created shouldNotBe null
            }
        }
    })
