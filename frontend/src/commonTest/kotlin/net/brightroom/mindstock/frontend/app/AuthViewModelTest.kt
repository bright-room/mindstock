package net.brightroom.mindstock.frontend.app

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.frontend.auth.Tokens
import net.brightroom.mindstock.frontend.core.auth.AuthState
import net.brightroom.mindstock.rpc.session.SessionStatus
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
private class FakeAuthDeps(
    private val path: String,
    private val token: String?,
    private val status: SessionStatus? = null,
) : AuthDeps {
    var redirectCalled = false
    var onAuthenticatedCalled = false

    override fun currentPath(): String = path

    override suspend fun handleCallback() {
        // no-op for tests not on callback path
    }

    override fun loadValidToken(): Tokens? =
        token?.let {
            Tokens(
                accessToken = it,
                refreshToken = "refresh",
                idToken = "id",
                expiresAt = Instant.fromEpochSeconds(Long.MAX_VALUE / 2),
            )
        }

    override suspend fun redirectToAuthorize() {
        redirectCalled = true
    }

    override suspend fun fetchSessionStatus(token: Tokens): SessionStatus = status ?: throw RuntimeException("boot failed")

    override fun onAuthenticated(resident: Resident) {
        onAuthenticatedCalled = true
    }
}

class AuthViewModelTest {
    @Test
    fun no_token_redirects_to_authorize_and_stays_booting() =
        runTest {
            val deps = FakeAuthDeps(path = "/", token = null)
            val vm = AuthViewModel(deps)
            vm.boot()
            deps.redirectCalled shouldBe true
            vm.state.value.shouldBeInstanceOf<AuthState.Booting>()
        }

    @Test
    fun registered_becomes_ready() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
            val deps = FakeAuthDeps(path = "/", token = "tok", status = SessionStatus.Registered(resident))
            val vm = AuthViewModel(deps)
            vm.boot()
            deps.onAuthenticatedCalled shouldBe true
            vm.state.value.shouldBeInstanceOf<AuthState.Ready>()
        }

    @Test
    fun unregistered_becomes_onboarding() =
        runTest {
            val deps = FakeAuthDeps(path = "/", token = "tok", status = SessionStatus.Unregistered)
            val vm = AuthViewModel(deps)
            vm.boot()
            vm.state.value.shouldBeInstanceOf<AuthState.NeedOnboarding>()
        }

    @Test
    fun whoami_failure_becomes_failed() =
        runTest {
            val deps = FakeAuthDeps(path = "/", token = "tok", status = null)
            val vm = AuthViewModel(deps)
            vm.boot()
            vm.state.value.shouldBeInstanceOf<AuthState.Failed>()
        }
}
