package net.brightroom.mindstock.e2e.user

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.rpc.withService
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.infrastructure.datasource.user.UserDataSource
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserRpcService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * E2E for [UserRpcService]: authenticated RPC path covering Bearer token wiring
 * and [net.brightroom.mindstock.configuration.auth.MindstockAuthPlugin] /
 * [net.brightroom.mindstock.configuration.auth.RequireRegisteredUserPlugin] behaviour.
 *
 * The three tests pin down:
 *  1. Happy path — Bearer header is honoured, MindstockAuthPlugin builds the session,
 *     RequireRegisteredUserPlugin admits the request, mutation persists.
 *  2. No `Authorization` header — MindstockAuthPlugin rejects with 401 before the WS upgrade
 *     completes (WS upgrade itself fails → `shouldThrowAny` on the client).
 *  3. Bearer with an unknown UserId — RequireRegisteredUserPlugin's DB check fails for the
 *     unknown sub → 401 → WS upgrade itself fails on the client.
 */
@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class UserRpcServiceE2eTest :
    FunSpec({
        test("rename updates the actor's display name and persists") {
            e2eTest {
                val user = seedUser(displayName = "Old Name")
                val rpc = authenticatedRpcClient(asUser = user, path = "user").withService<UserRpcService>()

                val r = rpc.rename(DisplayName("New Name"))
                r.shouldBeInstanceOf<RpcResult.Ok<Unit>>()

                val persisted =
                    transaction(database) {
                        (UserDataSource() as UserRepository).findById(user.id)
                    }
                persisted.shouldNotBeNull()
                persisted.displayName shouldBe DisplayName("New Name")
            }
        }

        test("rename without Authorization header is rejected at WS upgrade") {
            e2eTest {
                val rpc = publicRpcClient("user").withService<UserRpcService>()
                shouldThrowAny {
                    rpc.rename(DisplayName("anyone"))
                }
            }
        }

        test("rename with unknown UserId Bearer is rejected at WS upgrade") {
            e2eTest {
                val ghost =
                    User(
                        id = UserId(Uuid.random()),
                        authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("ghost")),
                        displayName = DisplayName("ghost"),
                    )
                val rpc = authenticatedRpcClient(asUser = ghost, path = "user").withService<UserRpcService>()
                shouldThrowAny {
                    rpc.rename(DisplayName("nobody"))
                }
            }
        }
    })
