@file:OptIn(
    kotlin.uuid.ExperimentalUuidApi::class,
    kotlin.time.ExperimentalTime::class,
    kotlin.io.encoding.ExperimentalEncodingApi::class,
)

package net.brightroom.mindstock.presentation.rpc.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.registerInstanceFactory
import io.mockk.slot
import net.brightroom.mindstock.application.scenario.product.AdoptProductScenario
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.product.UploadImageRequest
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * 画像 RPC(uploadImage / imageUrl)の Controller 単体テスト。
 *
 * base64 デコード・上限サイズガード・例外 → RpcError 翻訳という、この Controller が
 * 新たに足すロジックだけを検証する。Service は mock し、実 Garage / DB / WS は使わない
 * (store → presign → 直 GET の E2E は後続の手動ステージで確認する)。
 */
class ProductImageControllerTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val residentId = ResidentId.create()
        val session =
            MindstockSession.Registered(
                identity,
                residentId,
                Clock.System.now().plus(1.hours),
                Uuid.random(),
            )

        // RawImageUpload は ByteArray を包む value class で、空配列を拒否する(init require)。
        // mockk の any()/capture は型の「ダミー値」を reflective に生成するため、空配列で
        // インスタンス化して IAE になる。非空のダミーを返す instance factory を登録して回避する。
        beforeSpec {
            registerInstanceFactory { RawImageUpload(byteArrayOf(0)) }
        }

        test("uploadImage は base64 を復号し RawImageUpload で Service に渡し Ok(Unit) を返す") {
            val registerService = mockk<ProductRegisterService>()
            val controller = ProductRegisterController(registerService, mockk<AdoptProductScenario>(), session)
            val productId = ProductId.create()
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            val captured = slot<RawImageUpload>()
            coEvery { registerService.uploadImage(productId, capture(captured), residentId) } just Runs

            controller.uploadImage(productId, UploadImageRequest(Base64.encode(bytes))) shouldBe RpcResult.Ok(Unit)
            captured.captured() shouldBe bytes
            coVerify { registerService.uploadImage(productId, any(), residentId) }
        }

        test("uploadImage は不正な base64 を BadRequest にする") {
            val registerService = mockk<ProductRegisterService>()
            val controller = ProductRegisterController(registerService, mockk<AdoptProductScenario>(), session)

            val result = controller.uploadImage(ProductId.create(), UploadImageRequest("!!! not base64 !!!"))

            (result as RpcResult.Err).error.shouldBeInstanceOf<RpcError.BadRequest>()
        }

        test("uploadImage は 8MiB 超を BadRequest にする(Service を呼ばない)") {
            val registerService = mockk<ProductRegisterService>()
            val controller = ProductRegisterController(registerService, mockk<AdoptProductScenario>(), session)
            val tooLarge = ByteArray(8 * 1024 * 1024 + 1) { 0 }

            val result = controller.uploadImage(ProductId.create(), UploadImageRequest(Base64.encode(tooLarge)))

            (result as RpcResult.Err).error.shouldBeInstanceOf<RpcError.BadRequest>()
            coVerify(exactly = 0) { registerService.uploadImage(any(), any(), any()) }
        }

        test("imageUrl は Service の ImageUrl を Ok で包んで返す") {
            val productService = mockk<ProductService>()
            val controller = ProductController(productService, session)
            val productId = ProductId.create()
            val url = ImageUrl("https://storage.example/img.jpg?sig=abc")
            coEvery { productService.imageUrl(productId) } returns url

            controller.imageUrl(productId) shouldBe RpcResult.Ok(url)
        }

        test("imageUrl は画像未設定(ResourceNotFoundException)を NotFound に翻訳する") {
            val productService = mockk<ProductService>()
            val controller = ProductController(productService, session)
            val productId = ProductId.create()
            coEvery { productService.imageUrl(productId) } throws ResourceNotFoundException("product has no image: $productId")

            val result = controller.imageUrl(productId)

            (result as RpcResult.Err).error.shouldBeInstanceOf<RpcError.NotFound>()
        }
    })
