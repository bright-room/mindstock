package net.brightroom.mindstock.e2e.user

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRepositoryImpl
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * E2E for [UserPublicRpcService]: WebSocket → kRPC → Handler → Repository → Postgres.
 *
 * Note on invariant tests: the only argument-level invariant on [UserPublicRpcService.register]
 * is `DisplayName` non-blank/length, which is enforced by the domain VO's `init { require(...) }`
 * and therefore fires **client-side** at parameter construction — it never reaches the server.
 *
 * The next candidate was a server-side uniqueness violation on `(provider, subject)` (the
 * `users_zitadel_sub_unique` index). Empirically the resulting `ExposedSQLException` does
 * thrown server-side but is **not propagated synchronously to the kRPC client suspend call**
 * in the current setup — it surfaces asynchronously on channel/teardown and is not catchable
 * via `shouldThrowAny { rpc.register(...) }`. Validating that behaviour properly belongs in
 * an RPC error-handling task, not here, so we keep just the happy-path persistence test.
 */
class UserPublicRpcServiceE2eTest :
    FunSpec({
        test("register persists a new User and returns it with assigned id") {
            e2eTest {
                val rpc = publicRpcClient("user/public").withService<UserPublicRpcService>()
                val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("user-1"))
                val user =
                    rpc.register(
                        displayName = DisplayName("Alice"),
                        authIdentity = identity,
                    )

                user.displayName shouldBe DisplayName("Alice")
                user.authIdentity shouldBe identity

                val persisted =
                    transaction(database) {
                        (UserRepositoryImpl() as UserRepository).findById(user.id)
                    }
                persisted.shouldNotBeNull()
                persisted.displayName shouldBe DisplayName("Alice")
                persisted.authIdentity shouldBe identity
            }
        }
    })
