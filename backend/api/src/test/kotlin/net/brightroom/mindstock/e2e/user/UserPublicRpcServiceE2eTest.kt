package net.brightroom.mindstock.e2e.user

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.rpc.withService
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.e2e.auth.TestJwtIssuer
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.infrastructure.datasource.user.UserDataSource
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserPublicRpcService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * E2E for [UserPublicRpcService]: WebSocket → JWT(`user-public` realm) → kRPC → Handler → Repository → Postgres.
 *
 * `register()` no longer takes `AuthIdentity`; it derives the identity from the verified
 * JWT `sub` (mapped to `AuthProvider.ZITADEL`) on the server side. The tests below issue
 * real JWTs through [TestJwtIssuer] / [SharedJwksServer] and assert the persisted identity
 * matches the token's subject.
 */
@Tags("integration")
class UserPublicRpcServiceE2eTest :
    FunSpec({
        test("register persists a new User keyed off the JWT sub and returns it with assigned id") {
            e2eTest {
                val sub = "new-user-sub"
                val token = TestJwtIssuer.issue(subject = sub)
                val rpc =
                    authenticatedRpcClientWithToken(token = token, path = "user/public")
                        .withService<UserPublicRpcService>()

                val result = rpc.register(DisplayName("Alice"))
                result.shouldBeInstanceOf<RpcResult.Ok<User>>()
                val user = result.value

                val expectedIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub))
                user.displayName shouldBe DisplayName("Alice")
                user.authIdentity shouldBe expectedIdentity

                val persisted =
                    transaction(database) {
                        (UserDataSource() as UserRepository).findById(user.id)
                    }
                persisted.shouldNotBeNull()
                persisted.displayName shouldBe DisplayName("Alice")
                persisted.authIdentity shouldBe expectedIdentity
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
                third.shouldBeInstanceOf<RpcResult.Ok<User>>()
                third.value.displayName shouldBe DisplayName("Third")
            }
        }
    })
