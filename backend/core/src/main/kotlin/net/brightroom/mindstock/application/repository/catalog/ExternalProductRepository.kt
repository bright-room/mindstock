package net.brightroom.mindstock.application.repository.catalog

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem

/**
 * 外部商品 API(楽天/Yahoo 等)を JAN で照会して CatalogItem を得る参照リポジトリ。
 * master(自前 DB)に無い JAN を外部ソースから補完する読み取り専用の境界。
 * 不在 / レート制限 / 障害 / パース失敗はすべて ResourceNotFoundException に倒す
 * (理由は出し分けず、呼び出し側=CatalogService は NotFound としてフロントの手入力フォールバックへ繋ぐ)。
 */
interface ExternalProductRepository {
    fun findByJan(jan: Jan): CatalogItem
}
