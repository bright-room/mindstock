package net.brightroom.mindstock.domain.model.product

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.exception.DomainException
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId

/**
 * 世帯固有の商品インスタンス。CatalogItem を世帯で「採用」したもの。
 *
 * 最低在庫値(`latestMinimumStock`)とアーカイブ状態(`archivedAt`)を集約スナップショットとして持つ。
 */
public class Product(
    public val id: ProductId,
    internal val householdId: HouseholdId,
    internal val catalogItemId: CatalogItemId,
    internal val createdAt: Instant,
    internal val latestMinimumStock: MinimumStock?,
    internal val archivedAt: Instant?,
) {
    internal val isArchived: Boolean get() = archivedAt != null

    public fun ensureNotArchived() {
        if (isArchived) throw DomainException.ProductArchived(id)
    }

    public fun ensureBelongsTo(householdId: HouseholdId) {
        if (this.householdId != householdId) {
            throw DomainException.ProductNotInHousehold(id, householdId)
        }
    }
}
