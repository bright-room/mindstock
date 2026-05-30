package net.brightroom.mindstock.e2e.user

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.rpc.withService
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.e2e.auth.TestJwtIssuer
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.infrastructure.datasource.user.UserDataSource
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
import net.brightroom.mindstock.rpc.OnboardingRpcService
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi

/**
 * E2E for [OnboardingRpcService]: WebSocket → JWT(`user/public` realm) → kRPC → Controller → Scenario → Repository → Postgres.
 *
 * `register()` no longer takes `AuthIdentity`; it derives the identity from the verified
 * JWT `sub` (mapped to `AuthProvider.ZITADEL`) on the server side, then runs the onboarding
 * scenario. The tests below issue real JWTs through [TestJwtIssuer] / [SharedJwksServer] and
 * assert the persisted identity matches the token's subject.
 */
@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class OnboardingRpcServiceE2eTest :
    FunSpec({
        test("register persists a new User keyed off the JWT sub and returns its Profile with assigned id") {
            e2eTest {
                val sub = "new-user-sub"
                val token = TestJwtIssuer.issue(subject = sub)
                val rpc =
                    authenticatedRpcClientWithToken(token = token, path = "user/public")
                        .withService<OnboardingRpcService>()

                val result = rpc.register(DisplayName("Alice"))
                result.shouldBeInstanceOf<RpcResult.Ok<Profile>>()
                val profile = result.value

                profile.displayName shouldBe DisplayName("Alice")

                val persisted =
                    transaction(database) {
                        (UserDataSource() as UserRepository).findProfileById(profile.userId)
                    }
                persisted.shouldNotBeNull()
                persisted.displayName shouldBe DisplayName("Alice")

                // Verify the persisted row was keyed off the JWT sub.
                val persistedSub =
                    transaction(database) {
                        UsersTable
                            .selectAll()
                            .where { UsersTable.id eq profile.userId() }
                            .single()[UsersTable.zitadel_sub]
                    }
                persistedSub shouldBe sub
            }
        }

        // Pinning test for the onboarding scenario's idempotency contract.
        // register を Scenario 経由にしたことで、同一 sub の再 register は新規作成せず
        // 既存 Profile をそのまま返す(冪等)。重複 sub は DB UNIQUE 違反に到達しない。
        test("register is idempotent for the same sub and the pipeline stays usable for other subs") {
            e2eTest {
                val sameSub = "idempotent-subject"
                val token = TestJwtIssuer.issue(subject = sameSub)
                val rpc =
                    authenticatedRpcClientWithToken(token = token, path = "user/public")
                        .withService<OnboardingRpcService>()

                val first = rpc.register(DisplayName("First"))
                first.shouldBeInstanceOf<RpcResult.Ok<Profile>>()

                // Second register with the same sub returns the existing Profile (same userId),
                // not a new row and not an error.
                val second = rpc.register(DisplayName("Second"))
                second.shouldBeInstanceOf<RpcResult.Ok<Profile>>()
                second.value.userId shouldBe first.value.userId

                // The pipeline must still be usable for a *different* sub.
                val freshToken = TestJwtIssuer.issue(subject = "different-subject")
                val freshRpc =
                    authenticatedRpcClientWithToken(token = freshToken, path = "user/public")
                        .withService<OnboardingRpcService>()
                val third = freshRpc.register(DisplayName("Third"))
                third.shouldBeInstanceOf<RpcResult.Ok<Profile>>()
                third.value.displayName shouldBe DisplayName("Third")
            }
        }
    })
