package net.brightroom.mindstock.infrastructure.receive.catalog

import net.brightroom.mindstock.application.repository.catalog.ExternalProductRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem

/**
 * 外部プロバイダが未設定のときの既定実装。常に不在を返す。
 * 実プロバイダ(provider 決定後に <Provider>ProductReceive を追加実装)が用意できるまでの間、
 * lookupByJan を master 照合のみで成立させる(外部補完は常に NotFound)。
 */
class UnconfiguredProductReceive : ExternalProductRepository {
    override fun findByJan(jan: Jan): CatalogItem = throw ResourceNotFoundException("external product provider not configured: $jan")
}
