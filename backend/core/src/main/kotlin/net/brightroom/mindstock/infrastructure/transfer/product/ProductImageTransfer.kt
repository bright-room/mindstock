package net.brightroom.mindstock.infrastructure.transfer.product

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.brightroom.mindstock.application.repository.product.ProductImageStorageRepository
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload
import kotlin.time.Duration.Companion.hours

class ProductImageTransfer(
    private val s3: S3Client,
    private val bucket: String,
) : ProductImageStorageRepository {
    override suspend fun store(upload: RawImageUpload): ImageRef {
        // decode/縮小/再エンコードは CPU バウンドの同期処理。呼び出し元のコルーチンスレッドを
        // ブロックしないよう Default ディスパッチャへ逃がす。
        val processed = withContext(Dispatchers.Default) { ImageProcessor.process(upload.invoke()) }
        s3.putObject(
            PutObjectRequest {
                this.bucket = this@ProductImageTransfer.bucket
                key = processed.ref
                body = ByteStream.fromBytes(processed.bytes)
                contentType = "image/jpeg"
            },
        )
        return ImageRef(processed.ref)
    }

    override suspend fun presignedUrl(ref: ImageRef): ImageUrl {
        val presigned =
            s3.presignGetObject(
                GetObjectRequest {
                    bucket = this@ProductImageTransfer.bucket
                    key = ref.invoke()
                },
                1.hours,
            )
        return ImageUrl(presigned.url.toString())
    }
}
