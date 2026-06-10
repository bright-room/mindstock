package net.brightroom.mindstock.frontend.core.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome

/**
 * 商品画像の取得・キャッシュ。presigned URL を [fetchUrl] で取り、[http] で Garage から直 fetch、
 * decode した [ImageBitmap] をメモリにキャッシュする。失敗(NotFound 含む)は null を返し、
 * 呼び出し側はアイコン fallback に倒す。
 */
class ProductImageLoader(
    private val http: HttpClient,
    private val fetchUrl: suspend (ProductId) -> RpcOutcome<ImageUrl>,
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<ProductId, ImageBitmap>()

    /** productId の画像を取得。キャッシュ命中ならそれを返す。取得不能は null。 */
    suspend fun load(productId: ProductId): ImageBitmap? {
        mutex.withLock { cache[productId] }?.let { return it }
        val url =
            when (val out = fetchUrl(productId)) {
                is RpcOutcome.Success -> out.value
                is RpcOutcome.Failure -> return null
            }
        val bitmap = http.get(url.invoke()).readRawBytes().decodeToImageBitmap()
        mutex.withLock { cache[productId] = bitmap }
        return bitmap
    }

    /** キャッシュを破棄(upload 後の再ロード用)。 */
    suspend fun invalidate(productId: ProductId) {
        mutex.withLock { cache.remove(productId) }
    }
}

/**
 * [hasStoredImage] が true のとき [loader] で画像を読み込み返す。false なら null(アイコン fallback)。
 */
@Composable
fun rememberProductImage(
    loader: ProductImageLoader,
    productId: ProductId,
    hasStoredImage: Boolean,
    reloadKey: Int = 0,
): ImageBitmap? {
    var image by remember(productId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(productId, hasStoredImage, reloadKey) {
        image = if (hasStoredImage) loader.load(productId) else null
    }
    return image
}

val LocalProductImageLoader =
    staticCompositionLocalOf<ProductImageLoader> { error("ProductImageLoader not provided") }
