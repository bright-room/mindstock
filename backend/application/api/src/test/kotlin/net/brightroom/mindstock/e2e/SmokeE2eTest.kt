package net.brightroom.mindstock.e2e

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService

class SmokeE2eTest : FunSpec({
    test("UserPublicRpcService.register persists a User end-to-end") {
        e2eTest {
            val rpc = publicRpcClient("user/public").withService<UserPublicRpcService>()
            val user =
                rpc.register(
                    displayName = DisplayName("Smoke Test"),
                    authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("smoke-sub")),
                )
            user.displayName shouldBe DisplayName("Smoke Test")
            user.authIdentity.subject shouldBe AuthSubject("smoke-sub")
        }
    }
})
