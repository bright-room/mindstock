package net.brightroom.mindstock.rpc.result

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.test.Test

class RpcErrorSerializationTest {
    @Test
    fun NotFound_を_KrpcJson_で往復できる() {
        val error: RpcError = RpcError.NotFound("household not found: 42")
        val json = KrpcJson.encodeToString(error)
        val back = KrpcJson.decodeFromString<RpcError>(json)
        back shouldBe error
    }

    @Test
    fun BadRequest_は_field_と_reason_を保持して往復できる() {
        val error: RpcError = RpcError.BadRequest(field = "displayName", reason = "must not be blank")
        val back = KrpcJson.decodeFromString<RpcError>(KrpcJson.encodeToString(error))
        back.shouldBeInstanceOf<RpcError.BadRequest>()
        back shouldBe error
    }
}
