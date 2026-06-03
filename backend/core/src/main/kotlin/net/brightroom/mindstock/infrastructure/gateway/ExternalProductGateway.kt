package net.brightroom.mindstock.infrastructure.gateway

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem

/**
 * JAN で外部商品 API(楽天/Yahoo 等)を照会し CatalogItem を返す境界。
 * 不在 / レート制限 / 障害 / パース失敗はすべて ResourceNotFoundException に倒す
 * (理由は出し分けず、呼び出し側=CatalogService は NotFound としてフロントの手入力フォールバックへ繋ぐ)。
 */
interface ExternalProductGateway {
    fun fetch(jan: Jan): CatalogItem
}
