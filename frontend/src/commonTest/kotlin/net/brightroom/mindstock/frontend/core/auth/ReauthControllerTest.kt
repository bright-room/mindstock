package net.brightroom.mindstock.frontend.core.auth

import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test

class ReauthControllerTest {
    @Test
    fun request_emits_signal() =
        runTest {
            val controller = ReauthController()
            val received = mutableListOf<Unit>()
            val job = launch { controller.signal.collect { received.add(it) } }
            runCurrent()
            controller.request()
            runCurrent()
            received shouldHaveSize 1
            job.cancel()
        }
}
