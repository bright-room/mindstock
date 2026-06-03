package net.brightroom.mindstock.infrastructure.gateway

import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem

/**
 * 外部プロバイダ未設定時の既定 Gateway。常に不在を返す。
 * 実プロバイダ(provider 決定後に <Provider>ProductGateway を実装)が用意できるまでの間、
 * lookupByJan を master 照合のみで成立させる(未存在は NotFound)。
 */
class UnconfiguredProductGateway : ExternalProductGateway {
    override fun fetch(jan: Jan): CatalogItem = throw ResourceNotFoundException("external product gateway not configured: $jan")
}
