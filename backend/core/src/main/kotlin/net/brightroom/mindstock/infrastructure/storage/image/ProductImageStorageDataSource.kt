package net.brightroom.mindstock.infrastructure.storage.image

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.smithy.kotlin.runtime.content.ByteStream
import net.brightroom.mindstock.application.repository.product.ProductImageStorageRepository
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload
import kotlin.time.Duration.Companion.hours

class ProductImageStorageDataSource(
    private val s3: S3Client,
    private val bucket: String,
) : ProductImageStorageRepository {
    override suspend fun store(upload: RawImageUpload): ImageRef {
        val processed = ImageProcessor.process(upload.invoke())
        s3.putObject(
            PutObjectRequest {
                this.bucket = this@ProductImageStorageDataSource.bucket
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
                    bucket = this@ProductImageStorageDataSource.bucket
                    key = ref.invoke()
                },
                1.hours,
            )
        return ImageUrl(presigned.url.toString())
    }
}
