package net.brightroom.mindstock.e2e.user

import io.kotest.assertions.throwables.shouldThrowAny
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
 * Server-side errors (e.g. uniqueness violation on `(provider, subject)`) DO propagate to the
 * awaiting client suspend call as a thrown exception — see the "propagates server exception"
 * test below, which pins down the behaviour. See [net.brightroom.mindstock.configuration.transaction.tx]
 * for why this requires a `supervisorScope` wrapper around `newSuspendedTransaction`.
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

        // Pinning test for the server→client exception propagation contract.
        // Regression guard for the `tx` helper's `supervisorScope` wrapper: if that wrapper
        // is removed, the server-side ExposedSQLException's cancellation leaks past kRPC
        // and brings down the testApplication scope, failing the test *after* the catch.
        test("register propagates server exception (duplicate auth identity) to client") {
            e2eTest {
                val rpc = publicRpcClient("user/public").withService<UserPublicRpcService>()
                val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("dupe-subject"))
                rpc.register(DisplayName("First"), identity)

                shouldThrowAny {
                    rpc.register(DisplayName("Second"), identity)
                }

                // The connection must still be usable after the failure — proves the
                // exception did not also leak via the server's connection scope.
                val third =
                    rpc.register(
                        DisplayName("Third"),
                        AuthIdentity(AuthProvider.ZITADEL, AuthSubject("different-subject")),
                    )
                third.displayName shouldBe DisplayName("Third")
            }
        }
    })
