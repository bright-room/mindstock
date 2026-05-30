package net.brightroom.mindstock.e2e.auth

import com.auth0.jwt.algorithms.Algorithm
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.rpc.OnboardingRpcService
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserRpcService
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant

@Tags("integration")
class JwtAuthE2eTest :
    FunSpec({
        test("expired JWT → 401 (rpc call throws)") {
            e2eTest {
                val sub = "alice-sub"
                seedUser(displayName = "Alice", subject = sub)
                val expired =
                    TestJwtIssuer.issue(
                        subject = sub,
                        issuedAt = Instant.now().minusSeconds(7200),
                        expiresAt = Instant.now().minusSeconds(3600),
                    )
                val rpc = authenticatedRpcClientWithToken(token = expired, path = "user").withService<UserRpcService>()
                shouldThrowAny { rpc.rename(DisplayName("X")) }
            }
        }

        test("wrong issuer → 401") {
            e2eTest {
                val sub = "alice-sub"
                seedUser(displayName = "Alice", subject = sub)
                val badIssuer =
                    TestJwtIssuer.issue(
                        subject = sub,
                        issuer = "wrong-issuer",
                    )
                val rpc = authenticatedRpcClientWithToken(token = badIssuer, path = "user").withService<UserRpcService>()
                shouldThrowAny { rpc.rename(DisplayName("X")) }
            }
        }

        test("wrong audience → 401") {
            e2eTest {
                val sub = "alice-sub"
                seedUser(displayName = "Alice", subject = sub)
                val badAud =
                    TestJwtIssuer.issue(
                        subject = sub,
                        audience = "wrong-aud",
                    )
                val rpc = authenticatedRpcClientWithToken(token = badAud, path = "user").withService<UserRpcService>()
                shouldThrowAny { rpc.rename(DisplayName("X")) }
            }
        }

        test("signed by an unknown key → 401") {
            e2eTest {
                val sub = "alice-sub"
                seedUser(displayName = "Alice", subject = sub)
                val foreign = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                val foreignAlg =
                    Algorithm.RSA256(foreign.public as RSAPublicKey, foreign.private as RSAPrivateKey)
                val token =
                    TestJwtIssuer.issue(
                        subject = sub,
                        signWith = foreignAlg,
                        kid = "unknown-kid",
                    )
                val rpc = authenticatedRpcClientWithToken(token = token, path = "user").withService<UserRpcService>()
                shouldThrowAny { rpc.rename(DisplayName("X")) }
            }
        }

        test("token via Sec-WebSocket-Protocol succeeds") {
            e2eTest {
                val sub = "alice-sub"
                seedUser(displayName = "Alice", subject = sub)
                val token = TestJwtIssuer.issue(subject = sub)
                val rpc =
                    authenticatedRpcClientWithToken(token = token, path = "user").withService<UserRpcService>()
                rpc.rename(DisplayName("Renamed"))
            }
        }

        test("multiple mindstock.bearer entries → rejected even when each token is valid (fail-closed)") {
            e2eTest {
                val sub = "alice-sub"
                seedUser(displayName = "Alice", subject = sub)
                // 各 token は単体なら有効。2 つ同時提示は曖昧なので extractor が null → 401。
                val first = TestJwtIssuer.issue(subject = sub)
                val second = TestJwtIssuer.issue(subject = sub)
                val rpc =
                    rpcClientWithDuplicateBearer(
                        firstToken = first,
                        secondToken = second,
                        path = "user",
                    ).withService<UserRpcService>()
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
                    ).withService<OnboardingRpcService>()
                val created = rpc.register(DisplayName("Newbie"))
                created.shouldBeInstanceOf<RpcResult.Ok<Profile>>()
            }
        }

        test("registered-user-only endpoint rejects valid JWT with unregistered sub (RequireRegisteredUserPlugin)") {
            e2eTest {
                // Issue a valid JWT for a sub that has no corresponding User in DB
                val unknownSub = "unknown-sub-for-registered-only-route"
                val token = TestJwtIssuer.issue(subject = unknownSub)
                // Try to hit /api/v1/user (requires registered user)
                val rpc = authenticatedRpcClientWithToken(token = token, path = "user").withService<UserRpcService>()
                shouldThrowAny { rpc.rename(DisplayName("anyone")) }
                // The WS upgrade itself fails with 401 because RequireRegisteredUserPlugin
                // rejects sessions where userId is null
            }
        }
    })
