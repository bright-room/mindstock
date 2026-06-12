package net.brightroom.mindstock.frontend.core.ui

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.settings_error_last_owner_leave
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

class FailureHandlerTest {
    @Test
    fun mutation_unauthorized_requests_reauth_no_toast() =
        runTest {
            var reauthed = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { reauthed++ } }
            runCurrent()
            val toast = ToastController()
            FailureHandler(reauth, toast).onMutationFailure(RpcError.Unauthorized("x"))
            runCurrent()
            reauthed shouldBe 1
            toast.current.value shouldBe null
            job.cancel()
        }

    @Test
    fun mutation_other_error_toasts() =
        runTest {
            val toast = ToastController()
            FailureHandler(ReauthController(), toast).onMutationFailure(RpcError.Internal("x"))
            (toast.current.value != null) shouldBe true
        }

    @Test
    fun mutation_conflict_with_text_uses_conflict_text() =
        runTest {
            val toast = ToastController()
            FailureHandler(ReauthController(), toast)
                .onMutationFailure(RpcError.Conflict("dup"), Res.string.settings_error_last_owner_leave)
            toast.current.value
                ?.text
                ?.resource shouldBe Res.string.settings_error_last_owner_leave
        }

    @Test
    fun load_unauthorized_requests_reauth_but_no_toast() =
        runTest {
            var reauthed = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { reauthed++ } }
            runCurrent()
            val toast = ToastController()
            FailureHandler(reauth, toast).onLoadFailure(RpcError.Unauthorized("x"))
            runCurrent()
            reauthed shouldBe 1
            toast.current.value shouldBe null
            job.cancel()
        }

    @Test
    fun load_other_error_does_not_toast() =
        runTest {
            val toast = ToastController()
            FailureHandler(ReauthController(), toast).onLoadFailure(RpcError.Internal("x"))
            toast.current.value shouldBe null
        }
}
