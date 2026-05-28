package net.brightroom.mindstock.frontend.rpc

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class StubReauth(
    var nextSuccess: Boolean,
) : Reauth {
    var refreshCount = 0

    override suspend fun refresh(): Boolean {
        refreshCount++
        return nextSuccess
    }
}

class RpcCallWrapperTest {
    @Test
    fun success_first_try_no_refresh() =
        runTest {
            val reauth = StubReauth(nextSuccess = true)
            val w = RpcCallWrapper(reauth)
            val result = w.call { 42 }
            assertEquals(42, result)
            assertEquals(0, reauth.refreshCount)
        }

    @Test
    fun unauthorized_then_refresh_then_success() =
        runTest {
            val reauth = StubReauth(nextSuccess = true)
            var attempts = 0
            val w = RpcCallWrapper(reauth)
            val result =
                w.call {
                    attempts++
                    if (attempts == 1) throw UnauthorizedException() else "ok"
                }
            assertEquals("ok", result)
            assertEquals(1, reauth.refreshCount)
        }

    @Test
    fun unauthorized_then_refresh_then_unauthorized_throws_reauthRequired() =
        runTest {
            val reauth = StubReauth(nextSuccess = true)
            val w = RpcCallWrapper(reauth)
            assertFailsWith<ReauthRequiredException> {
                w.call<Unit> { throw UnauthorizedException() }
            }
            assertEquals(1, reauth.refreshCount)
        }

    @Test
    fun unauthorized_then_refresh_fails_throws_reauthRequired() =
        runTest {
            val reauth = StubReauth(nextSuccess = false)
            val w = RpcCallWrapper(reauth)
            assertFailsWith<ReauthRequiredException> {
                w.call<Unit> { throw UnauthorizedException() }
            }
            assertEquals(1, reauth.refreshCount)
        }
}
