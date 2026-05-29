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
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserPublicRpcService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi

/**
 * E2E for [UserPublicRpcService]: WebSocket → JWT(`user-public` realm) → kRPC → Handler → Repository → Postgres.
 *
 * `register()` no longer takes `AuthIdentity`; it derives the identity from the verified
 * JWT `sub` (mapped to `AuthProvider.ZITADEL`) on the server side. The tests below issue
 * real JWTs through [TestJwtIssuer] / [SharedJwksServer] and assert the persisted identity
 * matches the token's subject.
 */
@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class UserPublicRpcServiceE2eTest :
    FunSpec({
        test("register persists a new User keyed off the JWT sub and returns its Profile with assigned id") {
            e2eTest {
                val sub = "new-user-sub"
                val token = TestJwtIssuer.issue(subject = sub)
                val rpc =
                    authenticatedRpcClientWithToken(token = token, path = "user/public")
                        .withService<UserPublicRpcService>()

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

        // Pinning test for the server→client error propagation contract.
        // 重複 sub による DB UNIQUE 制約違反は tx() の catch で RpcError.Internal に変換される。
        // 将来 UserRegisterService で重複検出を入れ Conflict を明示返却するのは TODO。
        test("register returns Err(Internal) on duplicate sub and pipeline stays usable") {
            e2eTest {
                val dupeSub = "dupe-subject"
                val dupeToken = TestJwtIssuer.issue(subject = dupeSub)
                val rpc =
                    authenticatedRpcClientWithToken(token = dupeToken, path = "user/public")
                        .withService<UserPublicRpcService>()
                rpc.register(DisplayName("First"))

                val dup = rpc.register(DisplayName("Second"))
                dup.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                dup.error.shouldBeInstanceOf<RpcError.Internal>()

                // The pipeline must still be usable for a *different* sub after the failure
                // — proves the exception did not also leak via the server's connection scope.
                val freshToken = TestJwtIssuer.issue(subject = "different-subject")
                val freshRpc =
                    authenticatedRpcClientWithToken(token = freshToken, path = "user/public")
                        .withService<UserPublicRpcService>()
                val third = freshRpc.register(DisplayName("Third"))
                third.shouldBeInstanceOf<RpcResult.Ok<Profile>>()
                third.value.displayName shouldBe DisplayName("Third")
            }
        }
    })
