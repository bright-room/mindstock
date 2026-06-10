package net.brightroom.mindstock.infrastructure.storage.image

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
class ProductImageStorageDataSourceTest :
    FunSpec({
        val endpoint = System.getenv("TEST_STORAGE_ENDPOINT") ?: "http://localhost:3900"
        val bucket = System.getenv("TEST_STORAGE_BUCKET") ?: "mindstock-images"
        val ak = System.getenv("TEST_STORAGE_ACCESS_KEY") ?: error("TEST_STORAGE_ACCESS_KEY required")
        val sk = System.getenv("TEST_STORAGE_SECRET_KEY") ?: error("TEST_STORAGE_SECRET_KEY required")

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
        val ds = ProductImageStorageDataSource(s3, bucket)

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
