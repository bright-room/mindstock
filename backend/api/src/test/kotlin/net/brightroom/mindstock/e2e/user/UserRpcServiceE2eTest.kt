package net.brightroom.mindstock.e2e.user

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.rpc.withService
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.infrastructure.datasource.user.UserDataSource
import net.brightroom.mindstock.rpc.UserRpcService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi

/**
 * E2E for [UserRpcService]: authenticated RPC path covering Bearer token wiring
 * and [net.brightroom.mindstock.configuration.auth.ActorResolver] behaviour.
 *
 * The three tests pin down:
 *  1. Happy path — Bearer header is honoured, handler resolves the actor, mutation persists.
 *  2. No `Authorization` header — Ktor `authenticate("user")` rejects before reaching the handler.
 *  3. Bearer with an unknown subject — `ActorResolver.actor()` throws `UnauthorizedException`.
 *
 * Server-side errors propagate to the awaiting client suspend call thanks to the
 * `supervisorScope` wrapper in `tx` (see [UserPublicRpcServiceE2eTest] for the regression guard).
 */
@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class UserRpcServiceE2eTest :
    FunSpec({
        test("rename updates the actor's display name and persists") {
            e2eTest {
                val user = seedUser(displayName = "Old Name")
                val rpc = authenticatedRpcClient(asUser = user, path = "user").withService<UserRpcService>()

                rpc.rename(DisplayName("New Name"))

                val persisted =
                    transaction(database) {
                        (UserDataSource() as UserRepository).findProfileById(user.userId)
                    }
                persisted.shouldNotBeNull()
                persisted.displayName shouldBe DisplayName("New Name")
            }
        }

        test("rename without Authorization header is rejected") {
            e2eTest {
                val rpc = publicRpcClient("user").withService<UserRpcService>()
                shouldThrowAny {
                    rpc.rename(DisplayName("anyone"))
                }
            }
        }

        test("rename with unknown subject Bearer is rejected") {
            e2eTest {
                val rpc =
                    authenticatedRpcClientWithSubject(subject = "ghost-sub", path = "user")
                        .withService<UserRpcService>()
                shouldThrowAny {
                    rpc.rename(DisplayName("nobody"))
                }
            }
        }
    })
