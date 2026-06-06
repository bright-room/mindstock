package net.brightroom.mindstock.frontend.core.rpc

import io.kotest.matchers.shouldBe
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.error_bad_request
import mindstock.frontend.generated.resources.error_conflict
import mindstock.frontend.generated.resources.error_internal
import mindstock.frontend.generated.resources.error_not_found
import mindstock.frontend.generated.resources.error_unauthorized
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

class UiTextErrorTest {
    @Test
    fun maps_each_variant_to_resource_and_args() {
        errorText(RpcError.Unauthorized("x")).resource shouldBe Res.string.error_unauthorized
        errorText(RpcError.NotFound("x")).resource shouldBe Res.string.error_not_found
        errorText(RpcError.Internal("x")).resource shouldBe Res.string.error_internal

        val bad = errorText(RpcError.BadRequest("qty", "too big"))
        bad.resource shouldBe Res.string.error_bad_request
        bad.args shouldBe listOf("too big")

        val conflict = errorText(RpcError.Conflict("insufficient"))
        conflict.resource shouldBe Res.string.error_conflict
        conflict.args shouldBe listOf("insufficient")
    }

    @Test
    fun requires_reauth_only_for_unauthorized() {
        RpcError.Unauthorized("x").requiresReauth() shouldBe true
        RpcError.NotFound("x").requiresReauth() shouldBe false
    }
}
