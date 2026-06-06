package net.brightroom.mindstock.frontend.core.rpc

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

class RpcOutcomeTest {
    @Test
    fun ok_maps_to_success() {
        val out = RpcResult.Ok(42).toOutcome()
        out.shouldBeInstanceOf<RpcOutcome.Success<Int>>()
        (out as RpcOutcome.Success).value shouldBe 42
    }

    @Test
    fun err_maps_to_failure_with_error() {
        val err = RpcError.Conflict("dup")
        val out = RpcResult.Err(err).toOutcome<Int>()
        out.shouldBeInstanceOf<RpcOutcome.Failure>()
        (out as RpcOutcome.Failure).error shouldBe err
    }
}
