package net.brightroom.mindstock.frontend.feature.catalog

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_archived
import mindstock.frontend.generated.resources.toast_settings_saved
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.image.ImagePickResult
import net.brightroom.mindstock.frontend.core.image.pickImage
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.ui.FailureHandler
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText

class ProductMasterViewModel(
    private val householdId: HouseholdId,
    private val loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>,
    private val changeUnitOf: suspend (ProductId, ProductUnit) -> RpcOutcome<Unit>,
    private val changeMinimumOf: suspend (ProductId, MinimumStock) -> RpcOutcome<Unit>,
    private val archiveProduct: suspend (ProductId) -> RpcOutcome<Unit>,
    private val uploadImageOf: suspend (ProductId, String) -> RpcOutcome<Unit>,
    private val removeImageOf: suspend (ProductId) -> RpcOutcome<Unit>,
    private val invalidateImage: suspend (ProductId) -> Unit,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val failure = FailureHandler(reauth, toast)

    private val _state = MutableStateFlow<ProductMasterUiState>(ProductMasterUiState.Loading)
    val state: StateFlow<ProductMasterUiState> = _state.asStateFlow()

    // 画像更新(upload/remove)の進行中フラグ。再入を抑止して書込を 1 本に直列化する。
    private val _imageBusy = MutableStateFlow(false)
    val imageBusy: StateFlow<Boolean> = _imageBusy.asStateFlow()

    suspend fun load() {
        _state.value = ProductMasterUiState.Loading
        _state.value =
            when (val out = loadStocks(householdId)) {
                is RpcOutcome.Success -> {
                    ProductMasterUiState.Content(out.value)
                }

                is RpcOutcome.Failure -> {
                    failure.onLoadFailure(out.error)
                    ProductMasterUiState.Error(errorText(out.error))
                }
            }
    }

    suspend fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
    ) = write(changeUnitOf(productId, unit), UiText(Res.string.toast_settings_saved))

    suspend fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ) = write(changeMinimumOf(productId, minimumStock), UiText(Res.string.toast_settings_saved))

    suspend fun archive(productId: ProductId) = write(archiveProduct(productId), UiText(Res.string.toast_archived))

    /** 画像をアップロードし、成功なら true。loader キャッシュを無効化して一覧を更新する。 */
    suspend fun uploadImage(
        productId: ProductId,
        base64: String,
    ): Boolean = imageWrite(uploadImageOf(productId, base64), productId)

    /** 画像を削除し、成功なら true。 */
    suspend fun removeImage(productId: ProductId): Boolean = imageWrite(removeImageOf(productId), productId)

    /**
     * ピッカー起動 → 選択時のみアップロード。再入中(busy)は無視して false。
     * アップロード成功なら true(呼び出し側のシートは楽観表示を Stored に倒す)。Cancelled/失敗は false。
     */
    suspend fun pickAndUploadImage(productId: ProductId): Boolean {
        if (_imageBusy.value) return false
        _imageBusy.value = true
        try {
            return when (val r = pickImage()) {
                is ImagePickResult.Selected -> uploadImage(productId, r.base64)
                ImagePickResult.Cancelled -> false
            }
        } finally {
            _imageBusy.value = false
        }
    }

    /** 画像削除。再入中(busy)は無視して false。削除成功なら true(シートは楽観表示を無しに倒す)。 */
    suspend fun removeImageFor(productId: ProductId): Boolean {
        if (_imageBusy.value) return false
        _imageBusy.value = true
        try {
            return removeImage(productId)
        } finally {
            _imageBusy.value = false
        }
    }

    // 画像書込の共通後処理。成功時は loader 無効化 → 一覧再読込 → 全画面 refresh。成功可否を呼び出し側に返し、
    // シート側の楽観表示(stored フラグ)を更新できるようにする。トーストは出さない(画像は即時反映で十分)。
    private suspend fun imageWrite(
        outcome: RpcOutcome<Unit>,
        productId: ProductId,
    ): Boolean =
        when (outcome) {
            is RpcOutcome.Success -> {
                invalidateImage(productId)
                load()
                refresh.request()
                true
            }

            is RpcOutcome.Failure -> {
                failure.onMutationFailure(outcome.error)
                false
            }
        }

    private suspend fun write(
        outcome: RpcOutcome<Unit>,
        successText: UiText,
    ) {
        when (outcome) {
            is RpcOutcome.Success -> {
                load()
                refresh.request()
                toast.show(successText)
            }

            is RpcOutcome.Failure -> {
                failure.onMutationFailure(outcome.error)
            }
        }
    }
}
