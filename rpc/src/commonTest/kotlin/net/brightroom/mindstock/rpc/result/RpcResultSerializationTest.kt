package net.brightroom.mindstock.rpc.result

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.test.Test

class RpcResultSerializationTest {
    @Test
    fun Ok_を_KrpcJson_で往復できる() {
        val resident = Resident(ResidentId.create(), Profile(DisplayName("たろう")))
        val ok: RpcResult<Resident, RpcError> = RpcResult.Ok(resident)
        val back = KrpcJson.decodeFromString<RpcResult<Resident, RpcError>>(KrpcJson.encodeToString(ok))
        back shouldBe ok
    }

    @Test
    fun Err_を_KrpcJson_で往復できる() {
        val err: RpcResult<Resident, RpcError> = RpcResult.Err(RpcError.NotFound("resident not found"))
        val back = KrpcJson.decodeFromString<RpcResult<Resident, RpcError>>(KrpcJson.encodeToString(err))
        back shouldBe err
    }
}
