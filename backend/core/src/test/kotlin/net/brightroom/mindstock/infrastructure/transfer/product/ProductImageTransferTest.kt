package net.brightroom.mindstock.infrastructure.transfer.product

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.net.url.Url
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Tags("integration")
class ProductImageTransferTest :
    FunSpec({
        // app と同じ env(external.storage.* と同一名)を読む。専用 TEST_ は作らない。
        // 既定値は docker/garage-init.sh が import する固定 dev キー = application.yaml デフォルトと一致。
        // → docker compose up さえしていれば env を一切設定せずローカルでも CI でも回る。
        val endpoint = System.getenv("STORAGE_ENDPOINT") ?: "http://localhost:3900"
        val bucket = System.getenv("STORAGE_BUCKET") ?: "mindstock-images"
        val ak = System.getenv("STORAGE_ACCESS_KEY") ?: "GKdeadbeefdeadbeefdeadbeef"
        val sk =
            System.getenv("STORAGE_SECRET_KEY")
                ?: "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"

        val s3 =
            S3Client {
                region = "garage"
                endpointUrl = Url.parse(endpoint)
                forcePathStyle = true
                credentialsProvider =
                    StaticCredentialsProvider {
                        accessKeyId = ak
                        secretAccessKey = sk
                    }
            }
        val ds = ProductImageTransfer(s3, bucket)

        fun png(): ByteArray {
            val out = ByteArrayOutputStream()
            ImageIO.write(BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "png", out)
            return out.toByteArray()
        }

        test("store → presignedUrl → 直 GET で画像が引ける") {
            val ref = ds.store(RawImageUpload(png()))
            val url = ds.presignedUrl(ref)
            val fetched = HttpClient().use { it.get(url.invoke()).readRawBytes() }
            ImageIO.read(fetched.inputStream()).width shouldBe 100
        }
    })
