package net.brightroom.mindstock.frontend.feature.inventory.ui

import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile

/**
 * 認証 / backend 無しで在庫ホーム画面の見た目を検証するためのサンプル `Stocks`。
 *
 * 文言はサンプルデータ(i18n 対象外のテストデータ扱い)なので literal で持つ。
 * status は [net 在庫数] と [最低在庫(min)] の関係で作り分ける:
 *  - 在庫切れ: net <= 0
 *  - 残りわずか: 0 < net <= min
 *  - 十分: net > min
 */
fun previewStocks(): Stocks =
    Stocks(
        listOf(
            // net 0, min 1 → 在庫切れ
            sampleStock(name = "キレイキレイ 泡ハンドソープ", unit = "本", min = 1, net = 0),
            // net 1, min 2 → 残りわずか
            sampleStock(name = "トイレットペーパー 12ロール", unit = "パック", min = 2, net = 1),
            // net 3, min 1 → 十分
            sampleStock(name = "牛乳 1L", unit = "本", min = 1, net = 3),
            // net 2, min 2 → 残りわずか(境界: net == min)
            sampleStock(name = "卵 10個入り", unit = "パック", min = 2, net = 2),
            // net 4, min 1 → 十分
            sampleStock(name = "お米 5kg", unit = "袋", min = 1, net = 4),
            // net 0, min 4 → 在庫切れ
            sampleStock(name = "単3 アルカリ乾電池", unit = "本", min = 4, net = 0),
            // net 6, min 2 → 十分
            sampleStock(name = "食器用洗剤 詰め替え", unit = "個", min = 2, net = 6),
        ),
    )

private val sampleActor: Resident =
    Resident(
        id = ResidentId.create(),
        profile = Profile(DisplayName("サンプル ユーザー")),
    )

/**
 * 指定した [net] になるよう movements を組んだサンプル [Stock] を作る。
 * - net > 0: 補充 1 件だけ積む。
 * - net == 0: 補充と同量の消費を積み「使い切った」状態を表す(Quantity は常に正)。
 *
 * movements は domain メソッド([Stock.consume] 等)を介さず直接構築する。
 * [Stock.consume] は在庫不足で例外を投げるため、net 0 を作れないことへの対処。
 */
private fun sampleStock(
    name: String,
    unit: String,
    min: Int,
    net: Int,
): Stock {
    require(net >= 0) { "preview net must be >= 0: $net" }
    val product =
        Product(
            id = ProductId.create(),
            name = ProductName(name),
            barcode = Barcode.Unlinked,
            setting = StockingPolicy(ProductUnit(unit), MinimumStock(min)),
            image = ProductImage.None,
            status = ProductStatus.採用中,
        )
    val movements =
        if (net > 0) {
            StockMovements(listOf(replenishment(net)))
        } else {
            // 補充 → 同量消費 で net 0(在庫切れ)を表現
            StockMovements(listOf(replenishment(1), consumption(1)))
        }
    return Stock(product, movements)
}

private fun replenishment(quantity: Int): StockMovement.Replenishment =
    StockMovement.Replenishment(
        identity = MovementIdentity.Pending,
        quantity = Quantity(quantity),
        occurredAt = OccurredAt.now(),
        actor = sampleActor,
        note = Note(""),
    )

private fun consumption(quantity: Int): StockMovement.Consumption =
    StockMovement.Consumption(
        identity = MovementIdentity.Pending,
        quantity = Quantity(quantity),
        occurredAt = OccurredAt.now(),
        actor = sampleActor,
        note = Note(""),
    )
