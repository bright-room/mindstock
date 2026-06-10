package net.brightroom.mindstock.application.repository.product

import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload

/** 商品画像の実体ストレージ(Garage/S3)。 */
interface ProductImageStorageRepository {
    /** 原バイトを処理(縮小/JPEG/sha256)して put し、保存キー(ImageRef)を返す。 */
    suspend fun store(upload: RawImageUpload): ImageRef

    /** ref に対する presigned GET URL(有効期限付き)を発行する。 */
    suspend fun presignedUrl(ref: ImageRef): ImageUrl
}
