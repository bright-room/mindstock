package net.brightroom.mindstock.frontend.core.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import kotlin.coroutines.cancellation.CancellationException

/** 商品画像の表示状態。nullable を使わず不在/失敗をアイコン fallback として型で表す。 */
sealed interface ProductImageState {
    /** ロード中(初期)。 */
    data object Loading : ProductImageState

    /** ロード成功。 */
    data class Loaded(
        val bitmap: ImageBitmap,
    ) : ProductImageState

    /** 画像なし or 取得/デコード失敗 → アイコン表示。 */
    data object Fallback : ProductImageState
}

/**
 * 商品画像の取得・キャッシュ。presigned URL を [fetchUrl] で取り、[http] で Garage から直 fetch、
 * decode した [ImageBitmap] をメモリにキャッシュする。失敗(NotFound 含む)は [ProductImageState.Fallback]
 * を返し、呼び出し側はアイコン fallback に倒す。
 */
class ProductImageLoader(
    private val http: HttpClient,
    private val fetchUrl: suspend (ProductId) -> RpcOutcome<ImageUrl>,
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<ProductId, ImageBitmap>()

    /**
     * 画像の世代。[invalidate] で bump され、composition から [versionOf] で読むと snapshot 追跡されるので、
     * 置換(Stored→Stored)のように productId/hasStoredImage が不変でも再 fetch のトリガになる。
     */
    private val versions = mutableStateMapOf<ProductId, Int>()

    /** productId の画像を取得。キャッシュ命中ならそれを返す。取得不能・例外は [ProductImageState.Fallback]。 */
    suspend fun load(productId: ProductId): ProductImageState {
        mutex.withLock { cache[productId] }?.let { return ProductImageState.Loaded(it) }
        val url =
            when (val out = fetchUrl(productId)) {
                is RpcOutcome.Success -> out.value
                is RpcOutcome.Failure -> return ProductImageState.Fallback
            }
        // 期限切れ presigned URL・ネットワーク障害・不正バイトは Fallback に倒す(KDoc の契約)。
        val bytes =
            try {
                http.get(url.invoke()).readRawBytes()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                return ProductImageState.Fallback
            }
        val bitmap =
            try {
                bytes.decodeToImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                return ProductImageState.Fallback
            }
        mutex.withLock { cache[productId] = bitmap }
        return ProductImageState.Loaded(bitmap)
    }

    /** キャッシュを破棄し世代を上げる(upload/削除/置換後の全画面再 fetch 用)。 */
    suspend fun invalidate(productId: ProductId) =
        mutex.withLock {
            cache.remove(productId)
            versions[productId] = (versions[productId] ?: 0) + 1
        }

    /** composition から読むと snapshot 追跡され、[invalidate] で再 fetch のトリガになる。 */
    fun versionOf(productId: ProductId): Int = versions[productId] ?: 0
}

/**
 * [hasStoredImage] が true のとき [loader] で画像を読み込み状態を返す。false なら [ProductImageState.Fallback]。
 * [ProductImageLoader.invalidate] による世代変化を購読し、置換時も再 fetch する。
 */
@Composable
fun rememberProductImage(
    loader: ProductImageLoader,
    productId: ProductId,
    hasStoredImage: Boolean,
): ProductImageState {
    if (!hasStoredImage) return ProductImageState.Fallback
    val version = loader.versionOf(productId)
    var state by remember(productId) { mutableStateOf<ProductImageState>(ProductImageState.Loading) }
    LaunchedEffect(productId, hasStoredImage, version) {
        state = loader.load(productId)
    }
    return state
}

val LocalProductImageLoader =
    staticCompositionLocalOf<ProductImageLoader> { error("ProductImageLoader not provided") }

/**
 * [LocalProductImageLoader] から商品サムネ用の bitmap を読む薄いヘルパ。
 * Thumb を出す各画面が「loader 取得 → rememberProductImage → Loaded 取り出し」を毎回書かずに済むよう集約する。
 */
@Composable
fun rememberProductThumbnail(
    productId: ProductId,
    hasStoredImage: Boolean,
): ImageBitmap? {
    val loader = LocalProductImageLoader.current
    return (rememberProductImage(loader, productId, hasStoredImage) as? ProductImageState.Loaded)?.bitmap
}
